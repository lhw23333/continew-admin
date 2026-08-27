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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.continew.admin.channel.api.ChannelEvidenceAccessException;
import top.continew.admin.channel.dto.ChannelBusinessType;
import top.continew.admin.channel.dto.ChannelCommandContext;
import top.continew.admin.channel.dto.ChannelEvidenceAccess;
import top.continew.admin.channel.dto.ChannelEvidenceAuditOutcome;
import top.continew.admin.channel.dto.ChannelEvidenceAuditRecord;
import top.continew.admin.channel.dto.ChannelOnboardingSubmitCommand;
import top.continew.admin.channel.dto.ChannelProductKey;
import top.continew.starter.extension.tenant.context.TenantContext;
import top.continew.starter.extension.tenant.context.TenantContextHolder;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChannelEvidenceAccessServiceTest {

    private static final Long TENANT_ID = 1201L;
    private static final Long KYC_VERSION_ID = 2201L;
    private static final Long ATTACHMENT_ID = 3201L;
    private static final String OBJECT_SHA256 = "a".repeat(64);

    private InMemoryAttachmentRepository attachmentRepository;
    private InMemoryPrivateStorage storage;
    private InMemoryAuditPort auditPort;
    private ChannelEvidenceAccessService service;

    @BeforeEach
    void setUp() {
        TenantContext context = new TenantContext();
        context.setTenantId(TENANT_ID);
        TenantContextHolder.setContext(context);
        attachmentRepository = new InMemoryAttachmentRepository(attachment(KYC_VERSION_ID, KycAttachmentScanStatus.CLEAN, KycAttachmentValidationStatus.VALID));
        storage = new InMemoryPrivateStorage();
        auditPort = new InMemoryAuditPort();
        KycAttachmentPolicy policy = new KycAttachmentPolicy(1024, 5, 20, Duration.ofMinutes(30), Set.of("png"), Map
            .of("png", "image/png"));
        service = new ChannelEvidenceAccessService(attachmentRepository, storage, policy, auditPort);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void issuesBoundedAccessForExactReferencedCleanObjectAndAuditsHash() {
        ChannelEvidenceAccess access = service.issue(command(List.of(ATTACHMENT_ID)), ATTACHMENT_ID);

        assertEquals(ATTACHMENT_ID, access.objectId());
        assertEquals(OBJECT_SHA256, access.sha256());
        assertEquals(Duration.ofMinutes(5), storage.expiry);
        assertFalse(access.toString().contains(access.url().toString()));
        assertEquals(ChannelEvidenceAuditOutcome.GRANTED, auditPort.records.get(0).outcome());
        assertEquals(ATTACHMENT_ID, auditPort.records.get(0).objectId());
        assertEquals(OBJECT_SHA256, auditPort.records.get(0).objectSha256());
        assertEquals("SERIAL-1201", auditPort.records.get(0).context().businessSerial());
    }

    @Test
    void rejectsObjectOutsideCommandAllowlistWithoutTouchingStorage() {
        ChannelEvidenceAccessException exception = assertThrows(ChannelEvidenceAccessException.class, () -> service
            .issue(command(List.of()), ATTACHMENT_ID));

        assertEquals(ChannelEvidenceAccessException.Code.OBJECT_NOT_REFERENCED, exception.code());
        assertEquals(0, storage.accessCount);
        assertEquals(ChannelEvidenceAuditOutcome.DENIED, auditPort.records.get(0).outcome());
        assertEquals("OBJECT_NOT_REFERENCED", auditPort.records.get(0).failureCategory());
    }

    @Test
    void rejectsDifferentKycVersionAndUnclearedObject() {
        attachmentRepository.attachment = attachment(2202L, KycAttachmentScanStatus.CLEAN, KycAttachmentValidationStatus.VALID);
        ChannelEvidenceAccessException versionMismatch = assertThrows(ChannelEvidenceAccessException.class, () -> service
            .issue(command(List.of(ATTACHMENT_ID)), ATTACHMENT_ID));
        assertEquals(ChannelEvidenceAccessException.Code.KYC_VERSION_MISMATCH, versionMismatch.code());

        attachmentRepository.attachment = attachment(KYC_VERSION_ID, KycAttachmentScanStatus.UNAVAILABLE, KycAttachmentValidationStatus.QUARANTINED);
        ChannelEvidenceAccessException uncleared = assertThrows(ChannelEvidenceAccessException.class, () -> service
            .issue(command(List.of(ATTACHMENT_ID)), ATTACHMENT_ID));
        assertEquals(ChannelEvidenceAccessException.Code.OBJECT_NOT_CLEARED, uncleared.code());
        assertEquals(0, storage.accessCount);
    }

    @Test
    void failsClosedWhenAuditCannotBePersisted() {
        auditPort.returnNull = true;

        ChannelEvidenceAccessException exception = assertThrows(ChannelEvidenceAccessException.class, () -> service
            .issue(command(List.of(ATTACHMENT_ID)), ATTACHMENT_ID));

        assertEquals(ChannelEvidenceAccessException.Code.AUDIT_FAILED, exception.code());
        assertEquals(1, storage.accessCount);
    }

    private ChannelOnboardingSubmitCommand command(List<Long> evidenceObjectIds) {
        ChannelCommandContext context = new ChannelCommandContext(TENANT_ID, new ChannelProductKey("SYNTHETIC", "ONBOARDING"), "CFG-1201", ChannelBusinessType.ONBOARDING, 4201L, 3L, "SERIAL-1201", "TRACE-1201");
        return new ChannelOnboardingSubmitCommand(context, KYC_VERSION_ID, "REQ-1201", evidenceObjectIds);
    }

    private KycAttachment attachment(Long kycVersionId,
                                     KycAttachmentScanStatus scanStatus,
                                     KycAttachmentValidationStatus validationStatus) {
        return new KycAttachment(ATTACHMENT_ID, TENANT_ID, kycVersionId, "BUSINESS_LICENSE", "private|kyc/objects/|object.png", "license.png", "png", "image/png", "image/png", 128L, OBJECT_SHA256, scanStatus, validationStatus, 1, LocalDateTime
            .of(2026, 8, 27, 10, 0));
    }

    private static final class InMemoryAttachmentRepository implements KycAttachmentRepository {
        private KycAttachment attachment;

        private InMemoryAttachmentRepository(KycAttachment attachment) {
            this.attachment = attachment;
        }

        @Override
        public long countByKycVersion(Long tenantId, Long kycVersionId) {
            return 1;
        }

        @Override
        public long countByEvidenceType(Long tenantId, Long kycVersionId, String evidenceType) {
            return 1;
        }

        @Override
        public Optional<KycAttachment> findById(Long tenantId, Long attachmentId) {
            return TENANT_ID.equals(tenantId) && ATTACHMENT_ID.equals(attachmentId)
                ? Optional.of(attachment)
                : Optional.empty();
        }

        @Override
        public List<KycAttachment> listByKycVersion(Long tenantId, Long kycVersionId) {
            return List.of(attachment);
        }

        @Override
        public KycAttachment insert(KycAttachmentDraft draft) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class InMemoryPrivateStorage implements PrivateObjectStoragePort {
        private Duration expiry;
        private int accessCount;

        @Override
        public StoredObject store(Long tenantId,
                                  Long kycVersionId,
                                  String originalName,
                                  String detectedMime,
                                  byte[] content,
                                  boolean quarantine) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TemporaryAccess createTemporaryAccess(String storageObjectId, Duration expiry) {
            this.expiry = expiry;
            accessCount++;
            return new TemporaryAccess("https://temporary.example/channel/object?signature=secret", LocalDateTime.now()
                .plus(expiry));
        }

        @Override
        public void delete(String storageObjectId) {
        }
    }

    private static final class InMemoryAuditPort implements top.continew.admin.channel.api.ChannelEvidenceAuditPort {
        private final List<ChannelEvidenceAuditRecord> records = new ArrayList<>();
        private boolean returnNull;

        @Override
        public Long append(ChannelEvidenceAuditRecord record) {
            records.add(record);
            return returnNull ? null : (long)records.size();
        }
    }
}
