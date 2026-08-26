/*
 * Copyright (c) 2022-present Charles7c Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package top.continew.admin.channel.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.channel.api.ChannelApplicationStatePort;
import top.continew.admin.channel.api.ChannelConnectionConfigCatalog;
import top.continew.admin.channel.api.ChannelEventProcessingException;
import top.continew.admin.channel.api.ChannelEventStorePort;
import top.continew.admin.channel.dto.ChannelApplicationState;
import top.continew.admin.channel.dto.ChannelBusinessType;
import top.continew.admin.channel.dto.ChannelConnectionConfig;
import top.continew.admin.channel.dto.ChannelEventClaim;
import top.continew.admin.channel.dto.ChannelEventProcessingOutcome;
import top.continew.admin.channel.dto.ChannelEventProcessingResult;
import top.continew.admin.channel.dto.ChannelEventRecord;
import top.continew.admin.channel.dto.ChannelEventType;
import top.continew.admin.channel.dto.ChannelMappedStatus;
import top.continew.admin.channel.dto.ChannelStateMergeResult;
import top.continew.admin.channel.dto.VerifiedChannelCallback;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Persists each authenticated event once and applies only non-regressing normalized state. */
@Service
@RequiredArgsConstructor
public class ChannelEventProcessor {
    private static final String UNMAPPED_STATUS = "UNMAPPED_STATUS";

    private final ObjectMapper objectMapper;
    private final ChannelConnectionConfigCatalog configurationCatalog;
    private final ChannelEventStorePort eventStore;
    private final ChannelApplicationStatePort applicationStatePort;
    private final ChannelStateMerger stateMerger;

    @Transactional
    public ChannelEventProcessingResult process(VerifiedChannelCallback callback) {
        try {
            return processInternal(callback);
        } catch (ChannelEventProcessingException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw failure(ChannelEventProcessingException.Code.PERSISTENCE_FAILED);
        }
    }

    private ChannelEventProcessingResult processInternal(VerifiedChannelCallback callback) {
        if (callback == null) {
            throw failure(ChannelEventProcessingException.Code.INVALID_EVENT);
        }
        ParsedEvent parsed = parse(callback.payload());
        if (parsed.businessType() != ChannelBusinessType.ONBOARDING) {
            throw failure(ChannelEventProcessingException.Code.UNSUPPORTED_BUSINESS_TYPE);
        }
        String eventKey = eventKey(callback, parsed);
        ChannelEventClaim existing = findExisting(callback, eventKey);
        if (existing != null) {
            return duplicate(callback, parsed, existing);
        }
        ChannelConnectionConfig config = configurationCatalog.findVersion(callback.tenantId(), callback
            .product(), callback.configVersion())
            .orElseThrow(() -> failure(ChannelEventProcessingException.Code.CONFIGURATION_UNAVAILABLE));
        requireConfigIdentity(callback, config);
        ChannelApplicationState current = applicationStatePort.lock(callback.tenantId(), parsed.businessId(), callback
            .product(), callback.configVersion())
            .orElseThrow(() -> failure(ChannelEventProcessingException.Code.APPLICATION_NOT_FOUND));
        requireApplicationIdentity(callback, parsed, current);
        String sanitizedPayload = sanitizedPayload(parsed);
        ChannelEventRecord event = eventRecord(callback, parsed, current, config, eventKey, sanitizedPayload);
        ChannelEventClaim claim;
        try {
            claim = eventStore.claim(event);
        } catch (RuntimeException ex) {
            if (ex instanceof ChannelEventProcessingException processingException) {
                throw processingException;
            }
            throw failure(ChannelEventProcessingException.Code.PERSISTENCE_FAILED);
        }
        if (!claim.claimed()) {
            return duplicate(callback, parsed, claim);
        }
        ChannelMappedStatus mapped = config.statusMapping().entries().get(parsed.rawStatusCode());
        if (mapped == null || mapped.onboardingState() == null) {
            eventStore.fail(callback.tenantId(), claim.eventRecordId(), UNMAPPED_STATUS, callback.receivedTime());
            return new ChannelEventProcessingResult(claim.eventRecordId(), parsed
                .eventId(), ChannelEventProcessingOutcome.RECORDED_FAILED);
        }
        ChannelStateMergeResult merged = stateMerger.merge(current, mapped);
        boolean requiresUpdate = merged.changed() || current.businessSerial() == null;
        if (requiresUpdate && !applicationStatePort.apply(current, merged, parsed.businessSerial(), parsed
            .rawStatusCode(), callback.receivedTime())) {
            throw failure(ChannelEventProcessingException.Code.CONCURRENT_STATE_CHANGE);
        }
        String processingStatus = merged.changed() ? "PROCESSED" : "IGNORED_NON_REGRESSION";
        eventStore.complete(callback.tenantId(), claim.eventRecordId(), mapped, merged
            .changed(), processingStatus, callback.receivedTime());
        return new ChannelEventProcessingResult(claim.eventRecordId(), parsed.eventId(), merged.changed()
            ? ChannelEventProcessingOutcome.APPLIED
            : ChannelEventProcessingOutcome.RECORDED_NO_CHANGE);
    }

    private ChannelEventRecord eventRecord(VerifiedChannelCallback callback,
                                           ParsedEvent parsed,
                                           ChannelApplicationState current,
                                           ChannelConnectionConfig config,
                                           String eventKey,
                                           String sanitizedPayload) {
        if (callback.receivedTime().isBefore(parsed.occurredTime())) {
            throw failure(ChannelEventProcessingException.Code.INVALID_EVENT);
        }
        try {
            return new ChannelEventRecord(callback.tenantId(), eventKey, parsed.eventId(), parsed.eventType(), callback
                .product(), callback.configVersion(), parsed.businessType(), parsed.businessId(), parsed
                    .businessVersion(), current.merchantId(), parsed.businessSerial(), parsed.channelRequestId(), parsed
                        .rawStatusCode(), config.statusMappingVersion(), callback
                            .payloadHash(), sanitizedPayload, callback.keyVersion(), parsed.occurredTime(), callback
                                .receivedTime(), parsed.traceId());
        } catch (IllegalArgumentException ex) {
            throw failure(ChannelEventProcessingException.Code.INVALID_EVENT);
        }
    }

    private ChannelEventClaim findExisting(VerifiedChannelCallback callback, String eventKey) {
        try {
            return eventStore.find(callback.tenantId(), callback.product().channelCode(), eventKey).orElse(null);
        } catch (RuntimeException ex) {
            if (ex instanceof ChannelEventProcessingException processingException) {
                throw processingException;
            }
            throw failure(ChannelEventProcessingException.Code.PERSISTENCE_FAILED);
        }
    }

    private ChannelEventProcessingResult duplicate(VerifiedChannelCallback callback,
                                                   ParsedEvent parsed,
                                                   ChannelEventClaim claim) {
        if (!callback.payloadHash().equals(claim.existingPayloadHash())) {
            throw failure(ChannelEventProcessingException.Code.EVENT_ID_CONFLICT);
        }
        return new ChannelEventProcessingResult(claim.eventRecordId(), parsed
            .eventId(), ChannelEventProcessingOutcome.DUPLICATE);
    }

    private ParsedEvent parse(byte[] payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            return new ParsedEvent(requiredText(root, "eventId"), ChannelEventType
                .valueOf(requiredText(root, "eventType")), ChannelBusinessType
                    .valueOf(requiredText(root, "businessType")), requiredLong(root, "businessId"), requiredLong(root, "businessVersion"), requiredText(root, "businessSerial"), optionalText(root, "channelRequestId"), requiredText(root, "rawStatusCode"), OffsetDateTime
                        .parse(requiredText(root, "occurredTime"))
                        .withOffsetSameInstant(ZoneOffset.UTC)
                        .toLocalDateTime(), optionalText(root, "traceId"));
        } catch (RuntimeException | java.io.IOException ex) {
            throw failure(ChannelEventProcessingException.Code.INVALID_EVENT);
        }
    }

    private String requiredText(JsonNode root, String field) {
        if (root == null || !root.isObject()) {
            throw failure(ChannelEventProcessingException.Code.INVALID_EVENT);
        }
        JsonNode value = root.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw failure(ChannelEventProcessingException.Code.INVALID_EVENT);
        }
        return value.textValue().trim();
    }

    private String optionalText(JsonNode root, String field) {
        JsonNode value = root.get(field);
        return value == null || value.isNull() || !value.isTextual() || value.textValue().isBlank()
            ? null
            : value.textValue().trim();
    }

    private Long requiredLong(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() <= 0) {
            throw failure(ChannelEventProcessingException.Code.INVALID_EVENT);
        }
        return value.longValue();
    }

    private void requireConfigIdentity(VerifiedChannelCallback callback, ChannelConnectionConfig config) {
        if (!callback.tenantId().equals(config.tenantId()) || !callback.product().equals(config.product()) || !callback
            .configVersion()
            .equals(config.configVersion())) {
            throw failure(ChannelEventProcessingException.Code.CONFIGURATION_UNAVAILABLE);
        }
    }

    private void requireApplicationIdentity(VerifiedChannelCallback callback,
                                            ParsedEvent parsed,
                                            ChannelApplicationState current) {
        if (!callback.tenantId().equals(current.tenantId()) || !callback.product()
            .equals(current.product()) || !callback.configVersion().equals(current.configVersion()) || !parsed
                .businessId()
                .equals(current.applicationId())) {
            throw failure(ChannelEventProcessingException.Code.APPLICATION_IDENTITY_MISMATCH);
        }
        if (!parsed.businessVersion().equals(current.businessVersion())) {
            throw failure(ChannelEventProcessingException.Code.BUSINESS_VERSION_MISMATCH);
        }
        if (current.businessSerial() != null && !current.businessSerial().equals(parsed.businessSerial())) {
            throw failure(ChannelEventProcessingException.Code.BUSINESS_SERIAL_CONFLICT);
        }
    }

    private String eventKey(VerifiedChannelCallback callback, ParsedEvent parsed) {
        return "CALLBACK:" + digest((callback.product().dimensionKey() + ':' + parsed.eventId())
            .getBytes(StandardCharsets.UTF_8)).substring(0, 40);
    }

    private String sanitizedPayload(ParsedEvent parsed) {
        ObjectNode value = objectMapper.createObjectNode();
        value.put("eventId", parsed.eventId());
        value.put("eventType", parsed.eventType().name());
        value.put("businessType", parsed.businessType().name());
        value.put("businessId", parsed.businessId());
        value.put("businessVersion", parsed.businessVersion());
        value.put("businessSerial", parsed.businessSerial());
        if (parsed.channelRequestId() != null) {
            value.put("channelRequestId", parsed.channelRequestId());
        }
        value.put("rawStatusCode", parsed.rawStatusCode());
        value.put("occurredTime", parsed.occurredTime().atOffset(ZoneOffset.UTC).toString());
        if (parsed.traceId() != null) {
            value.put("traceId", parsed.traceId());
        }
        return value.toString();
    }

    private String digest(byte[] value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException ex) {
            throw failure(ChannelEventProcessingException.Code.INVALID_EVENT);
        }
    }

    private ChannelEventProcessingException failure(ChannelEventProcessingException.Code code) {
        return new ChannelEventProcessingException(code);
    }

    private record ParsedEvent(String eventId, ChannelEventType eventType, ChannelBusinessType businessType,
                               Long businessId, Long businessVersion, String businessSerial, String channelRequestId,
                               String rawStatusCode, LocalDateTime occurredTime, String traceId) {
        private ParsedEvent {
            if (eventId.length() > 128 || businessSerial.length() > 128 || rawStatusCode
                .length() > 64 || channelRequestId != null && channelRequestId
                    .length() > 191 || traceId != null && traceId.length() > 191) {
                throw new IllegalArgumentException("Parsed channel event is invalid");
            }
        }
    }
}
