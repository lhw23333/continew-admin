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

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.continew.admin.merchant.agent.application.AgentRepository;
import top.continew.admin.merchant.master.application.MerchantScopeAuthorizationService;
import top.continew.admin.merchant.master.domain.MerchantAccessDeniedException;
import top.continew.admin.merchant.security.audit.application.SecurityAuditWriter;
import top.continew.admin.merchant.security.audit.domain.SecurityAuditRecord;
import top.continew.admin.merchant.security.audit.domain.SecurityAuditResult;
import top.continew.starter.extension.tenant.context.TenantContextHolder;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;

/** Validates, quarantines, stores, authorizes, and audits private KYC attachments. */
@Service
@RequiredArgsConstructor
public class KycAttachmentService {

    private final KycVersionOwnershipRepository ownershipRepository;
    private final KycAttachmentRepository attachmentRepository;
    private final MerchantScopeAuthorizationService merchantScopeAuthorizationService;
    private final AgentRepository agentRepository;
    private final AttachmentContentInspectionPort contentInspectionPort;
    private final MalwareScannerPort malwareScannerPort;
    private final PrivateObjectStoragePort privateObjectStoragePort;
    private final KycAttachmentPolicy policy;
    private final SecurityAuditWriter securityAuditWriter;
    private final Clock clock = Clock.systemDefaultZone();

    public KycAttachment upload(KycAttachmentUploadCommand command) {
        requireTenantContext(command.tenantId());
        requireMerchantAccess(command.tenantId(), command.actorUserId(), command.kycVersionId());
        byte[] content = command.content();
        try {
            validateLimits(command, content.length);
            String safeOriginalName = safeOriginalName(command.originalName());
            String extension = extensionOf(safeOriginalName);
            if (!policy.allowedExtensions().contains(extension)) {
                throw new KycAttachmentException("KYC attachment extension is not allowed");
            }
            AttachmentContentInspectionPort.InspectionResult inspection = contentInspectionPort
                .inspect(content, safeOriginalName);
            validateInspection(command.declaredMime(), extension, inspection);
            String sha256 = sha256(content);
            MalwareScannerPort.ScanResult scanResult = malwareScannerPort.scan(content, inspection
                .detectedMime(), sha256);
            boolean quarantine = !KycAttachmentScanStatus.CLEAN.equals(scanResult.status());
            PrivateObjectStoragePort.StoredObject storedObject = privateObjectStoragePort.store(command
                .tenantId(), command.kycVersionId(), safeOriginalName, inspection.detectedMime(), content, quarantine);
            KycAttachment attachment;
            try {
                attachment = attachmentRepository.insert(new KycAttachmentDraft(command.tenantId(), command
                    .kycVersionId(), command.evidenceType(), storedObject
                        .storageObjectId(), safeOriginalName, extension, command.declaredMime(), inspection
                            .detectedMime(), (long)content.length, sha256, scanResult.status(), quarantine
                                ? KycAttachmentValidationStatus.QUARANTINED
                                : KycAttachmentValidationStatus.VALID, command.sort(), LocalDateTime.now(clock)));
            } catch (RuntimeException ex) {
                privateObjectStoragePort.delete(storedObject.storageObjectId());
                throw ex;
            }
            audit(command.tenantId(), command.actorUserId(), command.kycVersionId(), attachment.id(), command
                .evidenceType(), "ATTACHMENT_UPLOAD", SecurityAuditResult.SUCCESS, quarantine
                    ? scanResult.status().name()
                    : null, null);
            return attachment;
        } finally {
            Arrays.fill(content, (byte)0);
        }
    }

    public PrivateObjectStoragePort.TemporaryAccess createTemporaryAccess(Long tenantId,
                                                                          Long actorUserId,
                                                                          Long attachmentId,
                                                                          String ipAddress) {
        requireTenantContext(tenantId);
        KycAttachment attachment = attachmentRepository.findById(tenantId, attachmentId)
            .orElseThrow(KycAttachmentAccessDeniedException::new);
        requireMerchantAccess(tenantId, actorUserId, attachment.kycVersionId());
        if (!attachment.isAccessible()) {
            audit(tenantId, actorUserId, attachment.kycVersionId(), attachment.id(), attachment
                .evidenceType(), "ATTACHMENT_VIEW", SecurityAuditResult.DENIED, "ATTACHMENT_NOT_CLEARED", ipAddress);
            throw new KycAttachmentAccessDeniedException();
        }
        PrivateObjectStoragePort.TemporaryAccess access = privateObjectStoragePort.createTemporaryAccess(attachment
            .storageObjectId(), policy.accessExpiry());
        audit(tenantId, actorUserId, attachment.kycVersionId(), attachment.id(), attachment
            .evidenceType(), "ATTACHMENT_VIEW", SecurityAuditResult.SUCCESS, null, ipAddress);
        return access;
    }

    private void validateLimits(KycAttachmentUploadCommand command, int sizeBytes) {
        if (sizeBytes > policy.maxSizeBytes()) {
            throw new KycAttachmentException("KYC attachment exceeds the configured size limit");
        }
        if (attachmentRepository.countByKycVersion(command.tenantId(), command.kycVersionId()) >= policy
            .maxPerKycVersion()) {
            throw new KycAttachmentException("KYC attachment count limit is reached");
        }
        if (attachmentRepository.countByEvidenceType(command.tenantId(), command.kycVersionId(), command
            .evidenceType()) >= policy.maxPerEvidenceType()) {
            throw new KycAttachmentException("KYC evidence-type attachment count limit is reached");
        }
    }

    private void validateInspection(String declaredMime,
                                    String extension,
                                    AttachmentContentInspectionPort.InspectionResult inspection) {
        if (inspection == null || inspection.detectedMime() == null || !inspection.readable()) {
            throw new KycAttachmentException("KYC attachment content is unreadable");
        }
        String detectedMime = normalizeMime(inspection.detectedMime());
        String expectedMime = policy.extensionMimeTypes().get(extension);
        if (expectedMime == null || !expectedMime.equals(detectedMime)) {
            throw new KycAttachmentException("KYC attachment content does not match its extension");
        }
        if (declaredMime != null && !normalizeMime(declaredMime).equals(detectedMime)) {
            throw new KycAttachmentException("KYC attachment declared MIME does not match detected content");
        }
    }

    private Long requireMerchantAccess(Long tenantId, Long actorUserId, Long kycVersionId) {
        Long merchantId = ownershipRepository.findMerchantId(tenantId, kycVersionId)
            .orElseThrow(KycAttachmentAccessDeniedException::new);
        try {
            merchantScopeAuthorizationService.requireAccessible(tenantId, actorUserId, merchantId);
            return merchantId;
        } catch (MerchantAccessDeniedException ex) {
            throw new KycAttachmentAccessDeniedException();
        }
    }

    private String safeOriginalName(String originalName) {
        String normalized = originalName.replace('\\', '/');
        String fileName = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (fileName.isBlank() || fileName.length() > 255 || fileName.chars().anyMatch(Character::isISOControl)) {
            throw new KycAttachmentException("KYC attachment file name is invalid");
        }
        return fileName;
    }

    private String extensionOf(String fileName) {
        int separator = fileName.lastIndexOf('.');
        if (separator <= 0 || separator == fileName.length() - 1) {
            throw new KycAttachmentException("KYC attachment extension is required");
        }
        return fileName.substring(separator + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizeMime(String mime) {
        String normalized = mime.trim().toLowerCase(Locale.ROOT);
        return "image/jpg".equals(normalized) ? "image/jpeg" : normalized;
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new KycAttachmentException("KYC attachment hashing is unavailable", ex);
        }
    }

    private void audit(Long tenantId,
                       Long actorUserId,
                       Long kycVersionId,
                       Long attachmentId,
                       String evidenceType,
                       String action,
                       SecurityAuditResult result,
                       String failureCode,
                       String ipAddress) {
        Long actorAgentId = agentRepository.findByUserId(tenantId, actorUserId).map(agent -> agent.id()).orElse(null);
        securityAuditWriter
            .append(new SecurityAuditRecord(tenantId, actorUserId, actorAgentId, action, "KYC_ATTACHMENT", attachmentId, kycVersionId, evidenceType, null, ipAddress, result, failureCode, LocalDateTime
                .now(clock)));
    }

    private void requireTenantContext(Long tenantId) {
        if (tenantId == null || !tenantId.equals(TenantContextHolder.getTenantId())) {
            throw new KycAttachmentAccessDeniedException();
        }
    }
}
