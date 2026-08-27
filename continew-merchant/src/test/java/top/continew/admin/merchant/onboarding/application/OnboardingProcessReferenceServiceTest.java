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

package top.continew.admin.merchant.onboarding.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.continew.admin.channel.dto.ChannelSigningAction;
import top.continew.admin.merchant.master.application.MerchantScopeAuthorizationService;
import top.continew.admin.merchant.master.domain.Merchant;
import top.continew.admin.merchant.master.domain.MerchantAccessDeniedException;
import top.continew.admin.merchant.security.audit.application.SecurityAuditRepository;
import top.continew.admin.merchant.security.audit.application.SecurityAuditWriter;
import top.continew.admin.merchant.security.audit.domain.SecurityAuditRecord;
import top.continew.admin.merchant.security.crypto.VersionedKeyProvider;

import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OnboardingProcessReferenceServiceTest {

    private static final Long TENANT_ID = 1101L;
    private static final Long MERCHANT_ID = 2101L;
    private static final Long APPLICATION_ID = 3101L;
    private static final Long ACTOR_ID = 4101L;

    private MutableAuthorization authorization;
    private MutableBindingRepository bindingRepository;
    private InMemoryAuditRepository auditRepository;
    private OnboardingProcessReferenceService service;

    @BeforeEach
    void setUp() {
        authorization = new MutableAuthorization();
        bindingRepository = new MutableBindingRepository();
        auditRepository = new InMemoryAuditRepository();
        MutableClock clock = new MutableClock(Instant.parse("2026-08-27T12:00:00Z"));
        OnboardingProcessReferenceTokenService tokens = new OnboardingProcessReferenceTokenService(keyProvider(), clock, new SecureRandom());
        ProcessQrCodePort qrCodes = content -> Base64.getEncoder()
            .encodeToString(content.getBytes(StandardCharsets.UTF_8));
        service = new OnboardingProcessReferenceService(authorization, bindingRepository, tokens, new OnboardingProcessReferencePolicy(URI
            .create("https://app.example/onboarding/action"), Duration
                .ofMinutes(10)), qrCodes, new SecurityAuditWriter(auditRepository));
    }

    @Test
    void issueResolveAndAuthorizedRegenerationRetainDatabaseOwnership() {
        OnboardingProcessReference first = service
            .issue(TENANT_ID, ACTOR_ID, MERCHANT_ID, APPLICATION_ID, ChannelSigningAction.SIGN_AGREEMENT, "127.0.0.1");
        OnboardingProcessReference second = service
            .issue(TENANT_ID, ACTOR_ID, MERCHANT_ID, APPLICATION_ID, ChannelSigningAction.SIGN_AGREEMENT, "127.0.0.1");

        assertNotEquals(first.processUrl(), second.processUrl());
        assertEquals(first.processUrl().toASCIIString(), new String(Base64.getDecoder()
            .decode(first.qrCodeBase64()), StandardCharsets.UTF_8));
        assertFalse(first.toString().contains("token="));
        String token = first.processUrl().getQuery().substring("token=".length());
        OnboardingProcessReferenceClaims resolved = service
            .resolve(TENANT_ID, ACTOR_ID, MERCHANT_ID, APPLICATION_ID, token, "127.0.0.1");
        assertEquals("CHANNEL-A", resolved.channelCode());
        assertEquals(List
            .of("PROCESS_REFERENCE_ISSUE", "PROCESS_REFERENCE_ISSUE", "PROCESS_REFERENCE_RESOLVE"), auditRepository.records
                .stream()
                .map(SecurityAuditRecord::action)
                .toList());
    }

    @Test
    void pathAndPersistedChannelTamperingAreRejected() {
        OnboardingProcessReference reference = service
            .issue(TENANT_ID, ACTOR_ID, MERCHANT_ID, APPLICATION_ID, ChannelSigningAction.BIND_CARD, "127.0.0.1");
        String token = reference.processUrl().getQuery().substring("token=".length());

        assertEquals(ProcessReferenceException.Code.INVALID, assertThrows(ProcessReferenceException.class, () -> service
            .resolve(TENANT_ID, ACTOR_ID, MERCHANT_ID + 1, APPLICATION_ID, token, "127.0.0.1")).code());
        bindingRepository.channelCode = "CHANNEL-B";
        assertEquals(ProcessReferenceException.Code.INVALID, assertThrows(ProcessReferenceException.class, () -> service
            .resolve(TENANT_ID, ACTOR_ID, MERCHANT_ID, APPLICATION_ID, token, "127.0.0.1")).code());
    }

    @Test
    void regenerationRequiresCurrentMerchantAuthorization() {
        authorization.allowed = false;

        assertThrows(MerchantAccessDeniedException.class, () -> service
            .issue(TENANT_ID, ACTOR_ID, MERCHANT_ID, APPLICATION_ID, ChannelSigningAction.OPEN_RESERVE_ACCOUNT, "127.0.0.1"));
        assertEquals(0, auditRepository.records.size());
    }

    private VersionedKeyProvider keyProvider() {
        return new VersionedKeyProvider() {
            private final VersionedKey key = new VersionedKey("TEST-HASH-V1", new SecretKeySpec(new byte[32], "HmacSHA256"));

            @Override
            public VersionedKey currentDataKey() {
                return key;
            }

            @Override
            public VersionedKey dataKey(String version) {
                return key;
            }

            @Override
            public VersionedKey currentHashKey() {
                return key;
            }
        };
    }

    private static final class MutableAuthorization extends MerchantScopeAuthorizationService {
        private boolean allowed = true;

        private MutableAuthorization() {
            super(null, null);
        }

        @Override
        public Merchant requireAccessible(Long tenantId, Long actorUserId, Long merchantId) {
            if (!allowed) {
                throw new MerchantAccessDeniedException();
            }
            return null;
        }
    }

    private static final class MutableBindingRepository implements OnboardingProcessReferenceRepository {
        private String channelCode = "CHANNEL-A";

        @Override
        public Optional<OnboardingProcessReferenceBinding> find(Long tenantId, Long merchantId, Long applicationId) {
            if (!TENANT_ID.equals(tenantId) || !MERCHANT_ID.equals(merchantId) || !APPLICATION_ID
                .equals(applicationId)) {
                return Optional.empty();
            }
            return Optional
                .of(new OnboardingProcessReferenceBinding(tenantId, merchantId, applicationId, channelCode, 2L));
        }
    }

    private static final class InMemoryAuditRepository implements SecurityAuditRepository {
        private final List<SecurityAuditRecord> records = new ArrayList<>();

        @Override
        public Long append(SecurityAuditRecord record) {
            records.add(record);
            return (long)records.size();
        }
    }

    private static final class MutableClock extends Clock {
        private final Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
