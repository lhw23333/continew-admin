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

package top.continew.admin.merchant.kyc.attachment;

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
import top.continew.starter.extension.tenant.context.TenantContext;
import top.continew.starter.extension.tenant.context.TenantContextHolder;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KycAttachmentServiceTest {

    private static final Long TENANT_ID = 1101L;
    private static final Long MERCHANT_ID = 2101L;
    private static final Long KYC_VERSION_ID = 3101L;
    private static final Long OPERATOR_USER_ID = 4101L;

    private InMemoryAttachmentRepository attachmentRepository;
    private InMemoryPrivateStorage storage;
    private InMemorySecurityAuditRepository auditRepository;
    private MutableScanner scanner;
    private MutableInspector inspector;
    private KycAttachmentService service;

    @BeforeEach
    void setUp() {
        InMemoryMerchantRepository merchantRepository = new InMemoryMerchantRepository();
        merchantRepository.insert(merchant());
        EmptyAgentRepository agentRepository = new EmptyAgentRepository();
        MerchantScopeAuthorizationService merchantScope = new MerchantScopeAuthorizationService(merchantRepository, new AgentScopeAuthorizationService(agentRepository, new EmptyAgentClosureRepository()));
        attachmentRepository = new InMemoryAttachmentRepository();
        storage = new InMemoryPrivateStorage();
        auditRepository = new InMemorySecurityAuditRepository();
        scanner = new MutableScanner();
        inspector = new MutableInspector();
        KycVersionOwnershipRepository ownershipRepository = (tenantId, kycVersionId) -> tenantId
            .equals(TENANT_ID) && kycVersionId.equals(KYC_VERSION_ID) ? Optional.of(MERCHANT_ID) : Optional.empty();
        KycAttachmentPolicy policy = new KycAttachmentPolicy(16L, 2, 3, Duration.ofMinutes(5), Set
            .of("jpg", "jpeg", "png", "pdf"), Map
                .of("jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png", "pdf", "application/pdf"));
        service = new KycAttachmentService(ownershipRepository, attachmentRepository, merchantScope, agentRepository, inspector, scanner, storage, policy, new SecurityAuditWriter(auditRepository));
    }

    @Test
    void noScannerKeepsUploadQuarantinedAndDeniesTemporaryAccess() {
        scanner.status = KycAttachmentScanStatus.UNAVAILABLE;
        withTenant(() -> {
            KycAttachment attachment = service.upload(command("license.png", "image/png", new byte[] {1, 2, 3}));

            assertEquals(KycAttachmentScanStatus.UNAVAILABLE, attachment.scanStatus());
            assertEquals(KycAttachmentValidationStatus.QUARANTINED, attachment.validationStatus());
            assertTrue(storage.lastQuarantine);
            assertFalse(attachment.storageObjectId().contains("http"));
            assertThrows(KycAttachmentAccessDeniedException.class, () -> service
                .createTemporaryAccess(TENANT_ID, OPERATOR_USER_ID, attachment.id(), "127.0.0.1"));
            assertEquals("ATTACHMENT_NOT_CLEARED", auditRepository.records.get(1).failureCode());
            assertEquals(SecurityAuditResult.DENIED, auditRepository.records.get(1).result());
        });
    }

    @Test
    void cleanValidatedAttachmentGetsBoundedAccessAndAudit() {
        scanner.status = KycAttachmentScanStatus.CLEAN;
        withTenant(() -> {
            KycAttachment attachment = service.upload(command("license.png", "image/png", new byte[] {1, 2, 3}));

            PrivateObjectStoragePort.TemporaryAccess access = service
                .createTemporaryAccess(TENANT_ID, OPERATOR_USER_ID, attachment.id(), "127.0.0.1");

            assertEquals(KycAttachmentValidationStatus.VALID, attachment.validationStatus());
            assertEquals("https://temporary.example/object", access.url());
            assertFalse(access.toString().contains(access.url()));
            assertEquals(Duration.ofMinutes(5), storage.lastExpiry);
            assertEquals(SecurityAuditResult.SUCCESS, auditRepository.records.get(1).result());
            assertEquals("ATTACHMENT_VIEW", auditRepository.records.get(1).action());
        });
    }

    @Test
    void rejectsDisguisedOrUnreadableContentBeforeStorage() {
        inspector.detectedMime = "application/x-msdownload";
        withTenant(() -> assertThrows(KycAttachmentException.class, () -> service
            .upload(command("license.png", "image/png", new byte[] {1, 2, 3}))));
        assertEquals(0, storage.storeCount);

        inspector.detectedMime = "image/png";
        inspector.readable = false;
        withTenant(() -> assertThrows(KycAttachmentException.class, () -> service
            .upload(command("license.png", "image/png", new byte[] {1, 2, 3}))));
        assertEquals(0, storage.storeCount);
    }

    @Test
    void enforcesSizeAndCountLimitsBeforeStorage() {
        withTenant(() -> assertThrows(KycAttachmentException.class, () -> service
            .upload(command("license.png", "image/png", new byte[17]))));
        attachmentRepository.forceCount = 3;
        withTenant(() -> assertThrows(KycAttachmentException.class, () -> service
            .upload(command("license.png", "image/png", new byte[] {1}))));
        assertEquals(0, storage.storeCount);
    }

    private KycAttachmentUploadCommand command(String name, String declaredMime, byte[] content) {
        return new KycAttachmentUploadCommand(TENANT_ID, OPERATOR_USER_ID, KYC_VERSION_ID, "BUSINESS_LICENSE", name, declaredMime, content, 1);
    }

    private Merchant merchant() {
        return Merchant
            .create(new MerchantRegistration(MERCHANT_ID, TENANT_ID, 5101L, "M-2101", MerchantType.ENTERPRISE, "Synthetic Merchant", "Synthetic", "a"
                .repeat(64), OPERATOR_USER_ID, 4102L, "Contact", null, "Technology", "Synthetic merchant"), LocalDateTime
                    .of(2026, 8, 21, 10, 0));
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

    private static final class MutableInspector implements AttachmentContentInspectionPort {
        private String detectedMime = "image/png";
        private boolean readable = true;

        @Override
        public InspectionResult inspect(byte[] content, String originalName) {
            return new InspectionResult(detectedMime, readable);
        }
    }

    private static final class MutableScanner implements MalwareScannerPort {
        private KycAttachmentScanStatus status = KycAttachmentScanStatus.UNAVAILABLE;

        @Override
        public ScanResult scan(byte[] content, String detectedMime, String sha256) {
            return new ScanResult(status, status.name());
        }
    }

    private static final class InMemoryPrivateStorage implements PrivateObjectStoragePort {
        private int storeCount;
        private boolean lastQuarantine;
        private Duration lastExpiry;

        @Override
        public StoredObject store(Long tenantId,
                                  Long kycVersionId,
                                  String originalName,
                                  String detectedMime,
                                  byte[] content,
                                  boolean quarantine) {
            storeCount++;
            lastQuarantine = quarantine;
            return new StoredObject("private|kyc/|object.png");
        }

        @Override
        public TemporaryAccess createTemporaryAccess(String storageObjectId, Duration expiry) {
            lastExpiry = expiry;
            return new TemporaryAccess("https://temporary.example/object", LocalDateTime.now().plus(expiry));
        }

        @Override
        public void delete(String storageObjectId) {
        }
    }

    private static final class InMemoryAttachmentRepository implements KycAttachmentRepository {
        private final Map<Long, KycAttachment> attachments = new HashMap<>();
        private long nextId = 1;
        private long forceCount;

        @Override
        public long countByKycVersion(Long tenantId, Long kycVersionId) {
            return forceCount > 0 ? forceCount : attachments.size();
        }

        @Override
        public long countByEvidenceType(Long tenantId, Long kycVersionId, String evidenceType) {
            return attachments.values().stream().filter(item -> item.evidenceType().equals(evidenceType)).count();
        }

        @Override
        public Optional<KycAttachment> findById(Long tenantId, Long attachmentId) {
            return Optional.ofNullable(attachments.get(attachmentId)).filter(item -> item.tenantId().equals(tenantId));
        }

        @Override
        public KycAttachment insert(KycAttachmentDraft draft) {
            KycAttachment attachment = new KycAttachment(nextId++, draft.tenantId(), draft.kycVersionId(), draft
                .evidenceType(), draft.storageObjectId(), draft.originalName(), draft.extension(), draft
                    .declaredMime(), draft.detectedMime(), draft.sizeBytes(), draft.sha256(), draft.scanStatus(), draft
                        .validationStatus(), draft.sort(), draft.createTime());
            attachments.put(attachment.id(), attachment);
            return attachment;
        }
    }

    private static final class InMemorySecurityAuditRepository implements SecurityAuditRepository {
        private final List<SecurityAuditRecord> records = new ArrayList<>();

        @Override
        public Long append(SecurityAuditRecord record) {
            records.add(record);
            return (long)records.size();
        }
    }

    private static final class InMemoryMerchantRepository implements MerchantRepository {
        private final Map<Long, Merchant> merchants = new HashMap<>();

        @Override
        public Optional<Merchant> findById(Long tenantId, Long merchantId) {
            return Optional.ofNullable(merchants.get(merchantId)).filter(item -> item.tenantId().equals(tenantId));
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
