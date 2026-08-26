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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.continew.admin.channel.api.ChannelCallbackException;
import top.continew.admin.channel.api.ChannelCallbackReplayPort;
import top.continew.admin.channel.api.ChannelCallbackSecurityAuditPort;
import top.continew.admin.channel.api.ChannelConfigurationException;
import top.continew.admin.channel.api.ChannelSecret;
import top.continew.admin.channel.api.LoadedChannelCallbackConfiguration;
import top.continew.admin.channel.dto.ChannelCallbackReplayClaim;
import top.continew.admin.channel.dto.ChannelCallbackSecurityAuditRecord;
import top.continew.admin.channel.dto.ChannelCallbackSecurityOutcome;
import top.continew.admin.channel.dto.ChannelConnectionConfig;
import top.continew.admin.channel.dto.RawChannelCallback;
import top.continew.admin.channel.dto.VerifiedChannelCallback;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.regex.Pattern;

/** Verifies an untrusted callback completely before exposing its payload to event processing. */
@Service
public class ChannelCallbackVerifier {
    private static final Duration DEFAULT_TIMESTAMP_TOLERANCE = Duration.ofMinutes(5);
    private static final int MAX_PAYLOAD_BYTES = 10 * 1024 * 1024;
    private static final Pattern TIMESTAMP = Pattern.compile("[0-9]{10,17}");
    private static final Pattern NONCE = Pattern.compile("[A-Za-z0-9_-]{16,128}");
    private static final Pattern KEY_VERSION = Pattern.compile("ref-[0-9a-f]{16}");
    private static final Pattern SIGNATURE = Pattern.compile("[A-Za-z0-9_-]{40,128}");
    private static final Base64.Decoder BASE64_URL = Base64.getUrlDecoder();

    private final ChannelConfigurationLoader configurationLoader;
    private final ChannelCallbackReplayPort replayPort;
    private final ChannelCallbackSecurityAuditPort auditPort;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Duration timestampTolerance;

    @Autowired
    public ChannelCallbackVerifier(ChannelConfigurationLoader configurationLoader,
                                   ChannelCallbackReplayPort replayPort,
                                   ChannelCallbackSecurityAuditPort auditPort,
                                   ObjectMapper objectMapper) {
        this(configurationLoader, replayPort, auditPort, objectMapper, Clock.systemUTC(), DEFAULT_TIMESTAMP_TOLERANCE);
    }

    ChannelCallbackVerifier(ChannelConfigurationLoader configurationLoader,
                            ChannelCallbackReplayPort replayPort,
                            ChannelCallbackSecurityAuditPort auditPort,
                            ObjectMapper objectMapper,
                            Clock clock,
                            Duration timestampTolerance) {
        if (configurationLoader == null || replayPort == null || auditPort == null || objectMapper == null || clock == null || timestampTolerance == null || timestampTolerance
            .isZero() || timestampTolerance.isNegative() || timestampTolerance.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalArgumentException("Callback verifier configuration is invalid");
        }
        this.configurationLoader = configurationLoader;
        this.replayPort = replayPort;
        this.auditPort = auditPort;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.timestampTolerance = timestampTolerance;
    }

    public VerifiedChannelCallback verify(RawChannelCallback callback) {
        if (callback == null) {
            throw new ChannelCallbackException(ChannelCallbackException.Code.INVALID_REQUEST);
        }
        LocalDateTime receivedTime = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        String nonceFingerprint = fingerprintOptional(callback.nonce());
        String presentedKeyFingerprint = fingerprintOptional(callback.keyVersion());
        String sourceFingerprint = fingerprintOptional(callback.sourceAddress());
        String payloadHash = digest(callback.payload());
        String callbackKeyVersion = null;
        try {
            long timestamp = requireEnvelope(callback);
            requireTimestamp(timestamp);
            try (LoadedChannelCallbackConfiguration loaded = configurationLoader.loadCallback(callback
                .tenantId(), callback.product(), callback.configVersion(), receivedTime)) {
                ChannelConnectionConfig config = loaded.config();
                requireIdentity(callback, config);
                callbackKeyVersion = keyVersion(config.keyReferences().callbackVerification().reference());
                if (!constantEquals(callbackKeyVersion, callback.keyVersion())) {
                    throw failure(ChannelCallbackException.Code.KEY_VERSION_MISMATCH);
                }
                requireSignature(callback, timestamp, callbackKeyVersion, payloadHash, loaded.verificationSecret());
                requirePayloadFields(callback.payload());
                String nonceHash = digest(callback.nonce().getBytes(StandardCharsets.UTF_8));
                boolean claimed;
                try {
                    claimed = replayPort.claim(new ChannelCallbackReplayClaim(callback.tenantId(), callback
                        .product(), callback.configVersion(), callbackKeyVersion, nonceHash, receivedTime, receivedTime
                            .plus(timestampTolerance.multipliedBy(2))));
                } catch (RuntimeException ex) {
                    throw failure(ChannelCallbackException.Code.REPLAY_STORE_FAILED);
                }
                if (!claimed) {
                    throw failure(ChannelCallbackException.Code.REPLAY_DETECTED);
                }
                appendAudit(audit(callback, ChannelCallbackSecurityOutcome.ACCEPTED, null, callbackKeyVersion, presentedKeyFingerprint, nonceFingerprint, payloadHash, sourceFingerprint, receivedTime));
                return new VerifiedChannelCallback(callback.tenantId(), callback.product(), callback
                    .configVersion(), timestamp, nonceFingerprint, callbackKeyVersion, payloadHash, callback
                        .payload(), receivedTime);
            }
        } catch (ChannelConfigurationException ex) {
            appendAudit(audit(callback, ChannelCallbackSecurityOutcome.REJECTED, ChannelCallbackException.Code.CONFIGURATION_UNAVAILABLE
                .name(), callbackKeyVersion, presentedKeyFingerprint, nonceFingerprint, payloadHash, sourceFingerprint, receivedTime));
            throw failure(ChannelCallbackException.Code.CONFIGURATION_UNAVAILABLE);
        } catch (ChannelCallbackException ex) {
            if (ex.code() != ChannelCallbackException.Code.AUDIT_FAILED) {
                appendAudit(audit(callback, ChannelCallbackSecurityOutcome.REJECTED, ex.code()
                    .name(), callbackKeyVersion, presentedKeyFingerprint, nonceFingerprint, payloadHash, sourceFingerprint, receivedTime));
            }
            throw ex;
        } catch (RuntimeException ex) {
            appendAudit(audit(callback, ChannelCallbackSecurityOutcome.REJECTED, ChannelCallbackException.Code.INVALID_REQUEST
                .name(), callbackKeyVersion, presentedKeyFingerprint, nonceFingerprint, payloadHash, sourceFingerprint, receivedTime));
            throw failure(ChannelCallbackException.Code.INVALID_REQUEST);
        }
    }

    private long requireEnvelope(RawChannelCallback callback) {
        if (callback.payload().length == 0 || callback.payload().length > MAX_PAYLOAD_BYTES || callback
            .timestamp() == null || !TIMESTAMP.matcher(callback.timestamp()).matches() || callback
                .nonce() == null || !NONCE.matcher(callback.nonce()).matches() || callback
                    .keyVersion() == null || !KEY_VERSION.matcher(callback.keyVersion()).matches() || callback
                        .signature() == null || !SIGNATURE.matcher(callback.signature()).matches()) {
            throw failure(ChannelCallbackException.Code.INVALID_REQUEST);
        }
        try {
            return Long.parseLong(callback.timestamp());
        } catch (NumberFormatException ex) {
            throw failure(ChannelCallbackException.Code.INVALID_REQUEST);
        }
    }

    private void requireIdentity(RawChannelCallback callback, ChannelConnectionConfig config) {
        if (!callback.tenantId().equals(config.tenantId()) || !callback.product().equals(config.product()) || !callback
            .configVersion()
            .equals(config.configVersion())) {
            throw failure(ChannelCallbackException.Code.CHANNEL_IDENTITY_MISMATCH);
        }
    }

    private void requireTimestamp(long timestamp) {
        try {
            Duration skew = Duration.between(Instant.ofEpochMilli(timestamp), clock.instant()).abs();
            if (skew.compareTo(timestampTolerance) > 0) {
                throw failure(ChannelCallbackException.Code.TIMESTAMP_OUT_OF_TOLERANCE);
            }
        } catch (DateTimeException | ArithmeticException ex) {
            throw failure(ChannelCallbackException.Code.TIMESTAMP_OUT_OF_TOLERANCE);
        }
    }

    private void requireSignature(RawChannelCallback callback,
                                  long timestamp,
                                  String callbackKeyVersion,
                                  String payloadHash,
                                  ChannelSecret secret) {
        byte[] supplied;
        try {
            supplied = BASE64_URL.decode(callback.signature());
        } catch (IllegalArgumentException ex) {
            throw failure(ChannelCallbackException.Code.SIGNATURE_INVALID);
        }
        byte[] material = secret.copyMaterial();
        byte[] expected = null;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(material, "HmacSHA256"));
            expected = mac.doFinal(canonical(callback, timestamp, callbackKeyVersion, payloadHash));
            if (!MessageDigest.isEqual(expected, supplied)) {
                throw failure(ChannelCallbackException.Code.SIGNATURE_INVALID);
            }
        } catch (GeneralSecurityException ex) {
            throw failure(ChannelCallbackException.Code.SIGNATURE_INVALID);
        } finally {
            Arrays.fill(material, (byte)0);
            Arrays.fill(supplied, (byte)0);
            if (expected != null)
                Arrays.fill(expected, (byte)0);
        }
    }

    private void requirePayloadFields(byte[] payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (root == null || !root
                .isObject() || !requiredText(root, "eventId", 128) || !requiredText(root, "eventType", 64) || !requiredText(root, "businessType", 64) || !requiredPositiveLong(root, "businessId") || !requiredPositiveLong(root, "businessVersion") || !requiredText(root, "businessSerial", 128) || !requiredText(root, "rawStatusCode", 64) || !requiredTimestamp(root, "occurredTime")) {
                throw failure(ChannelCallbackException.Code.INVALID_REQUEST);
            }
        } catch (ChannelCallbackException ex) {
            throw ex;
        } catch (RuntimeException | java.io.IOException ex) {
            throw failure(ChannelCallbackException.Code.INVALID_REQUEST);
        }
    }

    private boolean requiredText(JsonNode root, String name, int maxLength) {
        JsonNode value = root.get(name);
        return value != null && value.isTextual() && !value.textValue().isBlank() && value.textValue()
            .length() <= maxLength && value.textValue().chars().noneMatch(Character::isISOControl);
    }

    private boolean requiredPositiveLong(JsonNode root, String name) {
        JsonNode value = root.get(name);
        return value != null && value.isIntegralNumber() && value.canConvertToLong() && value.longValue() > 0;
    }

    private boolean requiredTimestamp(JsonNode root, String name) {
        if (!requiredText(root, name, 64)) {
            return false;
        }
        try {
            OffsetDateTime.parse(root.get(name).textValue());
            return true;
        } catch (DateTimeException ex) {
            return false;
        }
    }

    private byte[] canonical(RawChannelCallback callback,
                             long timestamp,
                             String callbackKeyVersion,
                             String payloadHash) {
        return String.join("\n", "CALLBACK", callback.tenantId().toString(), callback.product().channelCode(), callback
            .product()
            .productCode(), callback.configVersion(), Long.toString(timestamp), callback
                .nonce(), callbackKeyVersion, payloadHash).getBytes(StandardCharsets.UTF_8);
    }

    private ChannelCallbackSecurityAuditRecord audit(RawChannelCallback callback,
                                                     ChannelCallbackSecurityOutcome outcome,
                                                     String failureCategory,
                                                     String callbackKeyVersion,
                                                     String presentedKeyFingerprint,
                                                     String nonceFingerprint,
                                                     String payloadHash,
                                                     String sourceFingerprint,
                                                     LocalDateTime receivedTime) {
        return new ChannelCallbackSecurityAuditRecord(callback.tenantId(), callback.product(), callback
            .configVersion(), outcome, failureCategory, callbackKeyVersion, presentedKeyFingerprint, nonceFingerprint, payloadHash, sourceFingerprint, receivedTime);
    }

    private void appendAudit(ChannelCallbackSecurityAuditRecord record) {
        try {
            if (auditPort.append(record) == null) {
                throw new IllegalStateException();
            }
        } catch (RuntimeException ex) {
            if (ex instanceof ChannelCallbackException callbackException && callbackException
                .code() == ChannelCallbackException.Code.AUDIT_FAILED) {
                throw callbackException;
            }
            throw failure(ChannelCallbackException.Code.AUDIT_FAILED);
        }
    }

    private String keyVersion(String reference) {
        return "ref-" + digest(reference.getBytes(StandardCharsets.UTF_8)).substring(0, 16);
    }

    private String fingerprintOptional(String value) {
        return value == null || value.isBlank()
            ? null
            : digest(value.getBytes(StandardCharsets.UTF_8)).substring(0, 16);
    }

    private String digest(byte[] value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException ex) {
            throw failure(ChannelCallbackException.Code.INVALID_REQUEST);
        }
    }

    private boolean constantEquals(String expected, String supplied) {
        return supplied != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), supplied
            .getBytes(StandardCharsets.US_ASCII));
    }

    private ChannelCallbackException failure(ChannelCallbackException.Code code) {
        return new ChannelCallbackException(code);
    }
}
