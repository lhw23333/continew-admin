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

package top.continew.admin.merchant.security.reveal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.continew.admin.merchant.agent.application.AgentClosureRepository;
import top.continew.admin.merchant.agent.application.AgentRepository;
import top.continew.admin.merchant.agent.application.AgentScopeAuthorizationService;
import top.continew.admin.merchant.agent.domain.Agent;
import top.continew.admin.merchant.agent.domain.AgentClosureLink;
import top.continew.admin.merchant.master.application.MerchantRepository;
import top.continew.admin.merchant.master.application.MerchantScopeAuthorizationService;
import top.continew.admin.merchant.master.domain.Merchant;
import top.continew.admin.merchant.master.domain.MerchantRegistration;
import top.continew.admin.merchant.master.domain.MerchantType;
import top.continew.admin.merchant.security.audit.application.SecurityAuditRepository;
import top.continew.admin.merchant.security.audit.application.SecurityAuditWriter;
import top.continew.admin.merchant.security.audit.domain.SecurityAuditRecord;
import top.continew.admin.merchant.security.audit.domain.SecurityAuditResult;
import top.continew.admin.merchant.security.crypto.AesGcmSensitiveDataCipher;
import top.continew.admin.merchant.security.crypto.HmacSha256KeyedHashService;
import top.continew.admin.merchant.security.crypto.SensitiveValueProtector;
import top.continew.admin.merchant.security.crypto.VersionedKeyProvider;
import top.continew.admin.merchant.security.value.EncryptedMobileNumber;
import top.continew.starter.extension.tenant.context.TenantContext;
import top.continew.starter.extension.tenant.context.TenantContextHolder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrivilegedRevealServiceTest {

    private static final Long TENANT_ID = 1001L;
    private static final Long MERCHANT_ID = 2001L;
    private static final Long OPERATOR_USER_ID = 3001L;

    private InMemoryMerchantRepository merchantRepository;
    private InMemorySecurityAuditRepository auditRepository;
    private SensitiveValueProtector protector;
    private boolean permissionAllowed;
    private boolean stepUpVerified;
    private PrivilegedRevealService service;

    @BeforeEach
    void setUp() {
        merchantRepository = new InMemoryMerchantRepository();
        auditRepository = new InMemorySecurityAuditRepository();
        protector = protector();
        permissionAllowed = true;
        stepUpVerified = true;
        AgentRepository agentRepository = new EmptyAgentRepository();
        AgentScopeAuthorizationService agentScope = new AgentScopeAuthorizationService(agentRepository, new EmptyAgentClosureRepository());
        MerchantScopeAuthorizationService merchantScope = new MerchantScopeAuthorizationService(merchantRepository, agentScope);
        SensitiveRevealPermissionPort permissionPort = actorUserId -> {
            if (!permissionAllowed) {
                throw new PrivilegedRevealDeniedException();
            }
        };
        StepUpAuthenticationPort stepUpPort = (actorUserId, proof, ipAddress) -> stepUpVerified;
        service = new PrivilegedRevealService(merchantScope, agentRepository, permissionPort, stepUpPort, protector, new SecurityAuditWriter(auditRepository));
        merchantRepository.insert(merchant());
    }

    @Test
    void revealsOnlyAfterAllChecksAndSanitizesAuditReason() {
        withTenant(() -> {
            PrivilegedRevealResult result = service
                .reveal(command(MERCHANT_ID, "Review case 13800138000 for settlement verification"));

            assertEquals("13800138000", result.value());
            assertFalse(result.toString().contains("13800138000"));
            SecurityAuditRecord audit = auditRepository.onlyRecord();
            assertEquals(SecurityAuditResult.SUCCESS, audit.result());
            assertEquals("CONTACT_MOBILE", audit.fieldName());
            assertFalse(audit.reason().contains("13800138000"));
            assertTrue(audit.reason().contains("[REDACTED]"));
        });
    }

    @Test
    void returnsServerOwnedMaskWithoutDecryptingOrAuditingReveal() {
        withTenant(() -> {
            assertEquals("138****8000", service
                .maskedValue(TENANT_ID, OPERATOR_USER_ID, MERCHANT_ID, MerchantSensitiveField.CONTACT_MOBILE));
            assertTrue(auditRepository.records.isEmpty());
        });
    }

    @Test
    void deniesAndAuditsFailedStepUp() {
        stepUpVerified = false;
        withTenant(() -> {
            assertThrows(PrivilegedRevealDeniedException.class, () -> service
                .reveal(command(MERCHANT_ID, "Review settlement details")));
            SecurityAuditRecord audit = auditRepository.onlyRecord();
            assertEquals(SecurityAuditResult.DENIED, audit.result());
            assertEquals("STEP_UP_FAILED", audit.failureCode());
        });
    }

    @Test
    void deniesAndAuditsMissingPermissionBeforeDecryption() {
        permissionAllowed = false;
        withTenant(() -> {
            assertThrows(PrivilegedRevealDeniedException.class, () -> service
                .reveal(command(MERCHANT_ID, "Review settlement details")));
            assertEquals("PERMISSION_DENIED", auditRepository.onlyRecord().failureCode());
        });
    }

    @Test
    void missingAndOutOfScopeMerchantUseTheSameDenial() {
        withTenant(() -> {
            assertThrows(PrivilegedRevealDeniedException.class, () -> service
                .reveal(command(9999L, "Review settlement details")));
            assertEquals("SCOPE_DENIED", auditRepository.onlyRecord().failureCode());
        });
    }

    private PrivilegedRevealCommand command(Long merchantId, String reason) {
        return new PrivilegedRevealCommand(TENANT_ID, OPERATOR_USER_ID, merchantId, MerchantSensitiveField.CONTACT_MOBILE, reason, "rsa-proof", "127.0.0.1");
    }

    private void withTenant(Runnable action) {
        TenantContext context = new TenantContext();
        context.setTenantId(TENANT_ID);
        TenantContextHolder.setContext(context);
        try {
            action.run();
        } finally {
            TenantContextHolder.clear();
        }
    }

    private Merchant merchant() {
        EncryptedMobileNumber mobile = EncryptedMobileNumber.fromPlaintext("13800138000", protector);
        MerchantRegistration registration = new MerchantRegistration(MERCHANT_ID, TENANT_ID, 4001L, "M-2001", MerchantType.ENTERPRISE, "Synthetic Merchant", "Synthetic", "a"
            .repeat(64), OPERATOR_USER_ID, 3002L, "Contact", mobile, "Technology", "Synthetic test merchant");
        return Merchant.create(registration, LocalDateTime.of(2026, 8, 20, 12, 0));
    }

    private SensitiveValueProtector protector() {
        VersionedKeyProvider provider = new VersionedKeyProvider() {
            private final SecretKey dataKey = new SecretKeySpec(new byte[32], "AES");
            private final SecretKey hashKey = new SecretKeySpec(new byte[32], "HmacSHA256");

            @Override
            public VersionedKey currentDataKey() {
                return new VersionedKey("data-v1", dataKey);
            }

            @Override
            public VersionedKey dataKey(String version) {
                return new VersionedKey(version, dataKey);
            }

            @Override
            public VersionedKey currentHashKey() {
                return new VersionedKey("hash-v1", hashKey);
            }
        };
        return new SensitiveValueProtector(new AesGcmSensitiveDataCipher(provider), new HmacSha256KeyedHashService(provider));
    }

    private static final class InMemoryMerchantRepository implements MerchantRepository {
        private final Map<Long, Merchant> merchants = new HashMap<>();

        @Override
        public Optional<Merchant> findById(Long tenantId, Long merchantId) {
            return Optional.ofNullable(merchants.get(merchantId))
                .filter(merchant -> merchant.tenantId().equals(tenantId));
        }

        @Override
        public boolean existsById(Long tenantId, Long merchantId) {
            return findById(tenantId, merchantId).isPresent();
        }

        @Override
        public void insert(Merchant merchant) {
            merchants.put(merchant.id(), merchant);
        }

        @Override
        public boolean updateLifecycle(Merchant merchant, Long expectedVersion) {
            return false;
        }
    }

    private static final class InMemorySecurityAuditRepository implements SecurityAuditRepository {
        private final List<SecurityAuditRecord> records = new ArrayList<>();

        @Override
        public Long append(SecurityAuditRecord record) {
            records.add(record);
            return (long)records.size();
        }

        private SecurityAuditRecord onlyRecord() {
            assertEquals(1, records.size());
            return records.get(0);
        }
    }

    private static final class EmptyAgentRepository implements AgentRepository {
        @Override
        public Optional<Agent> findById(Long tenantId, Long agentId) {
            return Optional.empty();
        }

        @Override
        public Optional<Agent> findByUserId(Long tenantId, Long userId) {
            return Optional.empty();
        }

        @Override
        public boolean existsById(Long tenantId, Long agentId) {
            return false;
        }

        @Override
        public boolean existsByAgentNo(Long tenantId, String agentNo) {
            return false;
        }

        @Override
        public boolean existsByUserId(Long tenantId, Long userId) {
            return false;
        }

        @Override
        public top.continew.admin.merchant.agent.application.AgentPage page(Long tenantId,
                                                                            List<Long> authorizedAgentIds,
                                                                            top.continew.admin.merchant.agent.application.AgentListQuery query) {
            return top.continew.admin.merchant.agent.application.AgentPage.empty(query.page(), query.size());
        }

        @Override
        public boolean bindDepartment(Long tenantId, Long agentId, Long deptId) {
            return false;
        }

        @Override
        public boolean updateProfile(Agent agent, Long expectedVersion) {
            return false;
        }

        @Override
        public void insert(Agent agent) {
        }

        @Override
        public boolean updateLifecycle(Agent agent, Long expectedVersion) {
            return false;
        }
    }

    private static final class EmptyAgentClosureRepository implements AgentClosureRepository {
        @Override
        public List<AgentClosureLink> findAncestors(Long tenantId, Long descendantId) {
            return List.of();
        }

        @Override
        public List<Long> findDescendantIds(Long tenantId, Long ancestorId) {
            return List.of();
        }

        @Override
        public boolean contains(Long tenantId, Long ancestorId, Long descendantId) {
            return false;
        }

        @Override
        public void insertAll(List<AgentClosureLink> links) {
        }
    }
}
