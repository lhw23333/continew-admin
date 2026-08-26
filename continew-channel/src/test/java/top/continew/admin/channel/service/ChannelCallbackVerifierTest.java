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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import top.continew.admin.channel.api.ChannelCallbackException;
import top.continew.admin.channel.api.ChannelConnectionConfigCatalog;
import top.continew.admin.channel.api.ChannelSecret;
import top.continew.admin.channel.api.ChannelSecretProvider;
import top.continew.admin.channel.dto.ChannelCallbackReplayClaim;
import top.continew.admin.channel.dto.ChannelCallbackSecurityAuditRecord;
import top.continew.admin.channel.dto.ChannelCallbackSecurityOutcome;
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
import top.continew.admin.channel.dto.ChannelProductKey;
import top.continew.admin.channel.dto.ChannelStageStatus;
import top.continew.admin.channel.dto.ChannelStatusMapping;
import top.continew.admin.channel.dto.ChannelTimeoutPolicy;
import top.continew.admin.channel.dto.RawChannelCallback;
import top.continew.admin.channel.dto.VerifiedChannelCallback;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelCallbackVerifierTest {
    private static final Instant NOW = Instant.parse("2026-08-25T00:00:00Z");
    private static final String CALLBACK_REFERENCE = "env://CHANNEL_CALLBACK_V1";
    private static final byte[] CALLBACK_KEY = filled((byte)9);
    private static final byte[] PAYLOAD = "{\"eventId\":\"EVENT-943\",\"eventType\":\"STATUS_CHANGED\",\"businessType\":\"ONBOARDING\",\"businessId\":943001,\"businessVersion\":2,\"businessSerial\":\"SERIAL-943\",\"rawStatusCode\":\"PROCESSING\",\"occurredTime\":\"2026-08-25T00:00:00Z\",\"legalIdentifier\":\"91350211M000100Y43\"}"
        .getBytes(StandardCharsets.UTF_8);

    @Test
    void validCallbackUsesOnlyVerificationKeyAndRejectsAtomicReplay() {
        List<ChannelCallbackSecurityAuditRecord> audits = new ArrayList<>();
        Set<String> nonceClaims = new HashSet<>();
        AtomicInteger callbackSecretLoads = new AtomicInteger();
        ChannelCallbackVerifier verifier = verifier(reference -> {
            assertEquals(ChannelKeyPurpose.CALLBACK_VERIFICATION, reference.purpose());
            callbackSecretLoads.incrementAndGet();
            return new ChannelSecret(reference.purpose(), reference.reference(), CALLBACK_KEY);
        }, claim -> nonceClaims.add(claim.nonceHash()), audits);
        RawChannelCallback callback = signedCallback("nonce-valid-94301", NOW.toEpochMilli(), keyVersion(), PAYLOAD);

        VerifiedChannelCallback verified = verifier.verify(callback);
        ChannelCallbackException replay = assertThrows(ChannelCallbackException.class, () -> verifier.verify(callback));

        assertEquals(ChannelCallbackException.Code.REPLAY_DETECTED, replay.code());
        assertEquals(2, callbackSecretLoads.get());
        assertEquals(1, nonceClaims.size());
        assertArrayEquals(PAYLOAD, verified.payload());
        assertEquals(keyVersion(), verified.keyVersion());
        assertFalse(verified.toString().contains("91350211M000100Y43"));
        assertFalse(callback.toString().contains("nonce-valid-94301"));
        assertEquals(List.of(ChannelCallbackSecurityOutcome.ACCEPTED, ChannelCallbackSecurityOutcome.REJECTED), audits
            .stream()
            .map(ChannelCallbackSecurityAuditRecord::outcome)
            .toList());
        assertEquals(ChannelCallbackException.Code.REPLAY_DETECTED.name(), audits.get(1).failureCategory());
    }

    @Test
    void invalidSignatureNeverClaimsNonceAndWritesSanitizedSecurityAudit() {
        List<ChannelCallbackSecurityAuditRecord> audits = new ArrayList<>();
        List<ChannelCallbackReplayClaim> claims = new ArrayList<>();
        ChannelCallbackVerifier verifier = verifier(reference -> new ChannelSecret(reference.purpose(), reference
            .reference(), CALLBACK_KEY), claim -> {
                claims.add(claim);
                return true;
            }, audits);
        RawChannelCallback callback = new RawChannelCallback(943L, product(), "CFG-943", Long.toString(NOW
            .toEpochMilli()), "nonce-invalid-943", keyVersion(), "a".repeat(43), PAYLOAD, "203.0.113.9");

        ChannelCallbackException exception = assertThrows(ChannelCallbackException.class, () -> verifier
            .verify(callback));

        assertEquals(ChannelCallbackException.Code.SIGNATURE_INVALID, exception.code());
        assertTrue(claims.isEmpty());
        assertEquals(1, audits.size());
        assertEquals(ChannelCallbackException.Code.SIGNATURE_INVALID.name(), audits.get(0).failureCategory());
        assertFalse(audits.toString().contains("91350211M000100Y43"));
        assertFalse(audits.toString().contains("nonce-invalid-943"));
        assertFalse(exception.getMessage().contains("203.0.113.9"));
    }

    @Test
    void staleOrMalformedCallbackIsRejectedBeforeSecretResolution() {
        List<ChannelCallbackSecurityAuditRecord> audits = new ArrayList<>();
        AtomicInteger secretLoads = new AtomicInteger();
        ChannelSecretProvider provider = reference -> {
            secretLoads.incrementAndGet();
            return new ChannelSecret(reference.purpose(), reference.reference(), CALLBACK_KEY);
        };
        ChannelCallbackVerifier verifier = verifier(provider, claim -> true, audits);
        RawChannelCallback stale = signedCallback("nonce-stale-94301", NOW.minus(Duration.ofMinutes(6))
            .toEpochMilli(), keyVersion(), PAYLOAD);
        RawChannelCallback malformed = new RawChannelCallback(943L, product(), "CFG-943", Long.toString(NOW
            .toEpochMilli()), "nonce-valid-94302", keyVersion(), null, PAYLOAD, null);

        ChannelCallbackException staleFailure = assertThrows(ChannelCallbackException.class, () -> verifier
            .verify(stale));
        ChannelCallbackException malformedFailure = assertThrows(ChannelCallbackException.class, () -> verifier
            .verify(malformed));

        assertEquals(ChannelCallbackException.Code.TIMESTAMP_OUT_OF_TOLERANCE, staleFailure.code());
        assertEquals(ChannelCallbackException.Code.INVALID_REQUEST, malformedFailure.code());
        assertEquals(0, secretLoads.get());
        assertEquals(2, audits.size());
    }

    @Test
    void wrongKeyVersionFailsBeforeSignatureAndReplayChecks() {
        List<ChannelCallbackSecurityAuditRecord> audits = new ArrayList<>();
        AtomicInteger replayCalls = new AtomicInteger();
        ChannelCallbackVerifier verifier = verifier(reference -> new ChannelSecret(reference.purpose(), reference
            .reference(), CALLBACK_KEY), claim -> {
                replayCalls.incrementAndGet();
                return true;
            }, audits);
        RawChannelCallback callback = new RawChannelCallback(943L, product(), "CFG-943", Long.toString(NOW
            .toEpochMilli()), "nonce-version-943", "ref-0000000000000000", "a".repeat(43), PAYLOAD, null);

        ChannelCallbackException exception = assertThrows(ChannelCallbackException.class, () -> verifier
            .verify(callback));

        assertEquals(ChannelCallbackException.Code.KEY_VERSION_MISMATCH, exception.code());
        assertEquals(0, replayCalls.get());
        assertEquals(ChannelCallbackException.Code.KEY_VERSION_MISMATCH.name(), audits.get(0).failureCategory());
    }

    @Test
    void authenticatedPayloadMissingRequiredFieldsIsRejectedBeforeReplayClaim() {
        List<ChannelCallbackSecurityAuditRecord> audits = new ArrayList<>();
        AtomicInteger replayCalls = new AtomicInteger();
        ChannelCallbackVerifier verifier = verifier(reference -> new ChannelSecret(reference.purpose(), reference
            .reference(), CALLBACK_KEY), claim -> {
                replayCalls.incrementAndGet();
                return true;
            }, audits);
        byte[] incomplete = "{}".getBytes(StandardCharsets.UTF_8);
        RawChannelCallback callback = signedCallback("nonce-fields-94301", NOW
            .toEpochMilli(), keyVersion(), incomplete);
        byte[] fractionalId = new String(PAYLOAD, StandardCharsets.UTF_8)
            .replace("\"businessId\":943001", "\"businessId\":943001.5")
            .getBytes(StandardCharsets.UTF_8);
        RawChannelCallback fractional = signedCallback("nonce-fields-94302", NOW
            .toEpochMilli(), keyVersion(), fractionalId);

        ChannelCallbackException exception = assertThrows(ChannelCallbackException.class, () -> verifier
            .verify(callback));
        ChannelCallbackException fractionalException = assertThrows(ChannelCallbackException.class, () -> verifier
            .verify(fractional));

        assertEquals(ChannelCallbackException.Code.INVALID_REQUEST, exception.code());
        assertEquals(ChannelCallbackException.Code.INVALID_REQUEST, fractionalException.code());
        assertEquals(0, replayCalls.get());
        assertEquals(ChannelCallbackException.Code.INVALID_REQUEST.name(), audits.get(0).failureCategory());
        assertEquals(2, audits.size());
    }

    @Test
    void auditFailurePreventsVerifiedCallbackFromBeingReturned() {
        ChannelCallbackVerifier verifier = new ChannelCallbackVerifier(loader(reference -> new ChannelSecret(reference
            .purpose(), reference.reference(), CALLBACK_KEY)), claim -> true, record -> {
                throw new IllegalStateException("audit store unavailable");
            }, new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(5));
        RawChannelCallback callback = signedCallback("nonce-audit-94301", NOW.toEpochMilli(), keyVersion(), PAYLOAD);

        ChannelCallbackException exception = assertThrows(ChannelCallbackException.class, () -> verifier
            .verify(callback));

        assertEquals(ChannelCallbackException.Code.AUDIT_FAILED, exception.code());
        assertFalse(exception.getMessage().contains("audit store unavailable"));
    }

    private ChannelCallbackVerifier verifier(ChannelSecretProvider provider,
                                             top.continew.admin.channel.api.ChannelCallbackReplayPort replayPort,
                                             List<ChannelCallbackSecurityAuditRecord> audits) {
        return new ChannelCallbackVerifier(loader(provider), replayPort, record -> {
            audits.add(record);
            return (long)audits.size();
        }, new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(5));
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
        return new ChannelConnectionConfig(9431L, 943L, product(), "CFG-943", new ChannelEndpointConfiguration("https://synthetic.invalid", paths), new ChannelTimeoutPolicy(Duration
            .ofSeconds(1), Duration.ofSeconds(5), timeouts), "MAP-943", new ChannelStatusMapping(Map
                .of("PROCESSING", new ChannelMappedStatus(ChannelOperationStatus.PROCESSING, state, null, 10, false))), new ChannelKeyReferences(new ChannelKeyReference(ChannelKeyPurpose.SIGNING, "env://CHANNEL_SIGNING_V1"), null, new ChannelKeyReference(ChannelKeyPurpose.CALLBACK_VERIFICATION, CALLBACK_REFERENCE)), ChannelConnectionStatus.ENABLED, LocalDateTime
                    .ofInstant(NOW.minusSeconds(60), ZoneOffset.UTC), null, LocalDateTime.ofInstant(NOW
                        .minusSeconds(120), ZoneOffset.UTC));
    }

    private RawChannelCallback signedCallback(String nonce, long timestamp, String keyVersion, byte[] payload) {
        return new RawChannelCallback(943L, product(), "CFG-943", Long
            .toString(timestamp), nonce, keyVersion, sign(timestamp, nonce, keyVersion, payload), payload, "203.0.113.9");
    }

    private String sign(long timestamp, String nonce, String keyVersion, byte[] payload) {
        try {
            String canonical = String.join("\n", "CALLBACK", "943", "SYNTHETIC", "ONBOARDING", "CFG-943", Long
                .toString(timestamp), nonce, keyVersion, digest(payload));
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(CALLBACK_KEY, "HmacSHA256"));
            return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    private String keyVersion() {
        return "ref-" + digest(CALLBACK_REFERENCE.getBytes(StandardCharsets.UTF_8)).substring(0, 16);
    }

    private String digest(byte[] value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    private ChannelProductKey product() {
        return new ChannelProductKey("SYNTHETIC", "ONBOARDING");
    }

    private static byte[] filled(byte value) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, value);
        return bytes;
    }
}
