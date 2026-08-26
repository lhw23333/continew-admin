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

import org.junit.jupiter.api.Test;
import top.continew.admin.channel.api.ChannelConfigurationException;
import top.continew.admin.channel.api.ChannelConnectionConfigCatalog;
import top.continew.admin.channel.api.ChannelSecret;
import top.continew.admin.channel.api.ChannelSecretProvider;
import top.continew.admin.channel.api.ChannelTransportException;
import top.continew.admin.channel.dto.ChannelBusinessType;
import top.continew.admin.channel.dto.ChannelCommandContext;
import top.continew.admin.channel.dto.ChannelConnectionConfig;
import top.continew.admin.channel.dto.ChannelConnectionStatus;
import top.continew.admin.channel.dto.ChannelEndpointConfiguration;
import top.continew.admin.channel.dto.ChannelKeyPurpose;
import top.continew.admin.channel.dto.ChannelKeyReference;
import top.continew.admin.channel.dto.ChannelKeyReferences;
import top.continew.admin.channel.dto.ChannelMappedStatus;
import top.continew.admin.channel.dto.ChannelOnboardingState;
import top.continew.admin.channel.dto.ChannelOperation;
import top.continew.admin.channel.dto.ChannelOperationStatus;
import top.continew.admin.channel.dto.ChannelOutboundRequest;
import top.continew.admin.channel.dto.ChannelProductKey;
import top.continew.admin.channel.dto.ChannelStageStatus;
import top.continew.admin.channel.dto.ChannelStatusMapping;
import top.continew.admin.channel.dto.ChannelTimeoutPolicy;
import top.continew.admin.channel.dto.ChannelTransportAuditRecord;
import top.continew.admin.channel.dto.ChannelTransportOutcome;
import top.continew.admin.channel.dto.ChannelTransportResponse;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecureChannelTransportTest {
    private static final Instant NOW = Instant.parse("2026-08-24T04:00:00Z");
    private static final byte[] PLAINTEXT = "{\"legalIdentifier\":\"91350211M000100Y43\"}"
        .getBytes(StandardCharsets.UTF_8);

    @Test
    void encryptedRequestIsSignedCorrelatedAndAuditedWithoutPayloadLeakage() {
        List<ChannelTransportAuditRecord> audits = new ArrayList<>();
        AtomicReference<ChannelOutboundRequest> captured = new AtomicReference<>();
        SecureChannelTransport transport = transport(reference -> secret(reference, (byte)7), audits);

        ChannelTransportResponse response = transport
            .exchange(context(), ChannelOperation.SUBMIT_ONBOARDING, PLAINTEXT, (request, timeout) -> {
                captured.set(request);
                assertEquals(Duration.ofSeconds(5), timeout);
                return new ChannelTransportResponse(202, "REQ-934", "accepted"
                    .getBytes(StandardCharsets.UTF_8), LocalDateTime.ofInstant(NOW.plusSeconds(1), ZoneOffset.UTC));
            });

        ChannelOutboundRequest request = captured.get();
        assertNotNull(request);
        assertTrue(request.encrypted());
        assertNotEquals(new String(PLAINTEXT, StandardCharsets.UTF_8), new String(request
            .payload(), StandardCharsets.UTF_8));
        assertFalse(request.toString().contains("91350211M000100Y43"));
        assertFalse(request.toString().contains("synthetic.invalid"));
        assertTrue(request.signature().length() >= 40);
        assertEquals("SERIAL-934", request.context().businessSerial());
        assertEquals("TRACE-934", request.context().traceId());
        assertEquals(202, response.statusCode());
        assertEquals(List.of(ChannelTransportOutcome.PREPARED, ChannelTransportOutcome.SUCCEEDED), audits.stream()
            .map(ChannelTransportAuditRecord::outcome)
            .toList());
        assertEquals(audits.get(0).nonceFingerprint(), audits.get(1).nonceFingerprint());
        assertTrue(audits.stream().noneMatch(audit -> audit.toString().contains("91350211M000100Y43")));
    }

    @Test
    void unavailableSigningKeyFailsBeforeNetworkAndWritesRejectedAudit() {
        List<ChannelTransportAuditRecord> audits = new ArrayList<>();
        AtomicInteger clientCalls = new AtomicInteger();
        SecureChannelTransport transport = transport(reference -> {
            throw new ChannelConfigurationException("Channel secret material is unavailable");
        }, audits);

        ChannelTransportException exception = assertThrows(ChannelTransportException.class, () -> transport
            .exchange(context(), ChannelOperation.SUBMIT_ONBOARDING, PLAINTEXT, (request, timeout) -> {
                clientCalls.incrementAndGet();
                return null;
            }));

        assertEquals(ChannelTransportException.Code.CONFIGURATION_UNAVAILABLE, exception.code());
        assertEquals(0, clientCalls.get());
        assertEquals(1, audits.size());
        assertEquals(ChannelTransportOutcome.REJECTED, audits.get(0).outcome());
        assertFalse(exception.getMessage().contains("CHANNEL_SIGNING"));
    }

    @Test
    void auditFailurePreventsTransport() {
        AtomicInteger clientCalls = new AtomicInteger();
        ChannelConfigurationLoader loader = loader(reference -> secret(reference, (byte)3));
        SecureChannelTransport transport = new SecureChannelTransport(loader, record -> {
            throw new IllegalStateException("audit unavailable");
        }, Clock.fixed(NOW, ZoneOffset.UTC), new FixedSecureRandom());

        ChannelTransportException exception = assertThrows(ChannelTransportException.class, () -> transport
            .exchange(context(), ChannelOperation.SUBMIT_ONBOARDING, PLAINTEXT, (request, timeout) -> {
                clientCalls.incrementAndGet();
                return null;
            }));

        assertEquals(ChannelTransportException.Code.AUDIT_FAILED, exception.code());
        assertEquals(0, clientCalls.get());
    }

    @Test
    void providerFailureIsSanitizedAndAudited() {
        List<ChannelTransportAuditRecord> audits = new ArrayList<>();
        SecureChannelTransport transport = transport(reference -> secret(reference, (byte)4), audits);

        ChannelTransportException exception = assertThrows(ChannelTransportException.class, () -> transport
            .exchange(context(), ChannelOperation.SUBMIT_ONBOARDING, PLAINTEXT, (request, timeout) -> {
                throw new IllegalStateException("https://provider.invalid 91350211M000100Y43");
            }));

        assertEquals(ChannelTransportException.Code.UNCERTAIN_RESULT, exception.code());
        assertFalse(exception.getMessage().contains("provider.invalid"));
        assertFalse(exception.getMessage().contains("91350211M000100Y43"));
        assertEquals(List.of(ChannelTransportOutcome.PREPARED, ChannelTransportOutcome.UNCERTAIN), audits.stream()
            .map(ChannelTransportAuditRecord::outcome)
            .toList());
        assertEquals(ChannelTransportException.Code.UNCERTAIN_RESULT.name(), audits.get(1).failureCategory());
        assertTrue(audits.stream().noneMatch(audit -> audit.toString().contains("provider.invalid")));
        assertTrue(audits.stream().noneMatch(audit -> audit.toString().contains("91350211M000100Y43")));
    }

    @Test
    void safeQueryRetriesWithFreshAuthenticatedEnvelope() {
        List<ChannelTransportAuditRecord> audits = new ArrayList<>();
        List<String> nonces = new ArrayList<>();
        AtomicInteger clientCalls = new AtomicInteger();
        SecureChannelTransport transport = transport(reference -> secret(reference, (byte)6), audits);

        ChannelTransportResponse response = transport
            .exchange(context(), ChannelOperation.QUERY_ONBOARDING_STATUS, PLAINTEXT, (request, timeout) -> {
                nonces.add(request.nonce());
                if (clientCalls.incrementAndGet() < 3) {
                    throw new ChannelTransportException(ChannelTransportException.Code.TIMEOUT, ChannelTransportException.TransmissionState.SENT);
                }
                return new ChannelTransportResponse(200, "REQ-QUERY-934", "ok"
                    .getBytes(StandardCharsets.UTF_8), LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
            });

        assertEquals(200, response.statusCode());
        assertEquals(3, clientCalls.get());
        assertEquals(3, nonces.stream().distinct().count());
        assertEquals(List
            .of(ChannelTransportOutcome.PREPARED, ChannelTransportOutcome.FAILED, ChannelTransportOutcome.PREPARED, ChannelTransportOutcome.FAILED, ChannelTransportOutcome.PREPARED, ChannelTransportOutcome.SUCCEEDED), audits
                .stream()
                .map(ChannelTransportAuditRecord::outcome)
                .toList());
    }

    @Test
    void nonIdempotentTimeoutAfterSendIsUncertainAndNeverRetried() {
        List<ChannelTransportAuditRecord> audits = new ArrayList<>();
        AtomicInteger clientCalls = new AtomicInteger();
        SecureChannelTransport transport = transport(reference -> secret(reference, (byte)8), audits);

        ChannelTransportException exception = assertThrows(ChannelTransportException.class, () -> transport
            .exchange(context(), ChannelOperation.SUBMIT_ONBOARDING, PLAINTEXT, (request, timeout) -> {
                clientCalls.incrementAndGet();
                throw new ChannelTransportException(ChannelTransportException.Code.TIMEOUT, ChannelTransportException.TransmissionState.SENT);
            }));

        assertEquals(ChannelTransportException.Code.UNCERTAIN_RESULT, exception.code());
        assertEquals(ChannelTransportException.TransmissionState.SENT, exception.transmissionState());
        assertEquals(1, clientCalls.get());
        assertEquals(ChannelTransportOutcome.UNCERTAIN, audits.get(1).outcome());
    }

    @Test
    void definitelyNotSentCommandFailureRemainsFailedWithoutRetry() {
        List<ChannelTransportAuditRecord> audits = new ArrayList<>();
        AtomicInteger clientCalls = new AtomicInteger();
        SecureChannelTransport transport = transport(reference -> secret(reference, (byte)2), audits);

        ChannelTransportException exception = assertThrows(ChannelTransportException.class, () -> transport
            .exchange(context(), ChannelOperation.SUBMIT_ONBOARDING, PLAINTEXT, (request, timeout) -> {
                clientCalls.incrementAndGet();
                throw new ChannelTransportException(ChannelTransportException.Code.TRANSPORT_FAILED, ChannelTransportException.TransmissionState.NOT_SENT);
            }));

        assertEquals(ChannelTransportException.Code.TRANSPORT_FAILED, exception.code());
        assertEquals(ChannelTransportException.TransmissionState.NOT_SENT, exception.transmissionState());
        assertEquals(1, clientCalls.get());
        assertEquals(ChannelTransportOutcome.FAILED, audits.get(1).outcome());
    }

    @Test
    void payloadAndResponseUseDefensiveCopies() {
        List<ChannelTransportAuditRecord> audits = new ArrayList<>();
        SecureChannelTransport transport = transport(reference -> secret(reference, (byte)5), audits);
        byte[] mutable = "{}".getBytes(StandardCharsets.UTF_8);
        AtomicReference<byte[]> sent = new AtomicReference<>();
        ChannelTransportResponse response = transport.exchange(context(), ChannelOperation.SUBMIT_ONBOARDING, mutable, (
                                                                                                                        request,
                                                                                                                        timeout) -> {
            sent.set(request.payload());
            return new ChannelTransportResponse(200, null, new byte[] {1, 2, 3}, LocalDateTime
                .ofInstant(NOW, ZoneOffset.UTC));
        });
        mutable[0] = 0;
        byte[] returned = response.payload();
        returned[0] = 9;
        assertNotEquals(0, sent.get()[0]);
        assertArrayEquals(new byte[] {1, 2, 3}, response.payload());
    }

    private SecureChannelTransport transport(ChannelSecretProvider provider, List<ChannelTransportAuditRecord> audits) {
        return new SecureChannelTransport(loader(provider), record -> {
            audits.add(record);
            return (long)audits.size();
        }, Clock.fixed(NOW, ZoneOffset.UTC), new FixedSecureRandom());
    }

    private ChannelConfigurationLoader loader(ChannelSecretProvider provider) {
        ChannelConnectionConfig config = config();
        ChannelConnectionConfigCatalog catalog = new ChannelConnectionConfigCatalog() {
            @Override
            public Optional<ChannelConnectionConfig> findVersion(Long tenantId,
                                                                 ChannelProductKey product,
                                                                 String configVersion) {
                return Optional.of(config);
            }

            @Override
            public Optional<ChannelConnectionConfig> findEffective(Long tenantId,
                                                                   ChannelProductKey product,
                                                                   LocalDateTime effectiveAt) {
                return Optional.of(config);
            }
        };
        return new ChannelConfigurationLoader(catalog, provider);
    }

    private ChannelConnectionConfig config() {
        EnumMap<ChannelOperation, String> paths = new EnumMap<>(ChannelOperation.class);
        EnumMap<ChannelOperation, Duration> timeouts = new EnumMap<>(ChannelOperation.class);
        for (ChannelOperation operation : ChannelOperation.values()) {
            paths.put(operation, "/api/" + operation.name().toLowerCase(java.util.Locale.ROOT));
            timeouts.put(operation, Duration.ofSeconds(5));
        }
        ChannelOnboardingState state = new ChannelOnboardingState(ChannelStageStatus.PROCESSING, ChannelStageStatus.NOT_STARTED, ChannelStageStatus.NOT_STARTED, ChannelStageStatus.NOT_STARTED, ChannelStageStatus.PROCESSING);
        return new ChannelConnectionConfig(9341L, 934L, context()
            .product(), "CFG-934", new ChannelEndpointConfiguration("https://synthetic.invalid", paths), new ChannelTimeoutPolicy(Duration
                .ofSeconds(1), Duration.ofSeconds(5), timeouts), "MAP-934", new ChannelStatusMapping(Map
                    .of("ACCEPTED", new ChannelMappedStatus(ChannelOperationStatus.ACCEPTED, state, null, 10, false))), new ChannelKeyReferences(new ChannelKeyReference(ChannelKeyPurpose.SIGNING, "env://CHANNEL_SIGNING_V1"), new ChannelKeyReference(ChannelKeyPurpose.ENCRYPTION, "env://CHANNEL_ENCRYPTION_V1"), new ChannelKeyReference(ChannelKeyPurpose.CALLBACK_VERIFICATION, "env://CHANNEL_CALLBACK_V1")), ChannelConnectionStatus.ENABLED, LocalDateTime
                        .ofInstant(NOW.minusSeconds(60), ZoneOffset.UTC), null, LocalDateTime.ofInstant(NOW
                            .minusSeconds(120), ZoneOffset.UTC));
    }

    private ChannelCommandContext context() {
        return new ChannelCommandContext(934L, new ChannelProductKey("SYNTHETIC", "ONBOARDING"), "CFG-934", ChannelBusinessType.ONBOARDING, 934201L, 3L, "SERIAL-934", "TRACE-934");
    }

    private ChannelSecret secret(ChannelKeyReference reference, byte value) {
        byte[] material = new byte[32];
        java.util.Arrays.fill(material, value);
        return new ChannelSecret(reference.purpose(), reference.reference(), material);
    }

    private static final class FixedSecureRandom extends SecureRandom {
        private int value = 1;

        @Override
        public void nextBytes(byte[] bytes) {
            for (int index = 0; index < bytes.length; index++) {
                bytes[index] = (byte)value++;
            }
        }
    }
}
