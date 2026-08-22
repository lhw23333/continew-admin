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

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.merchant.kyc.attachment.KycAttachment;
import top.continew.admin.merchant.kyc.attachment.KycAttachmentRepository;
import top.continew.admin.merchant.master.application.MerchantScopeAuthorizationService;
import top.continew.admin.merchant.master.domain.Merchant;
import top.continew.admin.merchant.master.domain.MerchantAccessDeniedException;
import top.continew.admin.merchant.master.domain.MerchantDomainException;
import top.continew.admin.merchant.security.audit.application.SecurityAuditWriter;
import top.continew.admin.merchant.security.audit.domain.SecurityAuditRecord;
import top.continew.admin.merchant.security.audit.domain.SecurityAuditResult;
import top.continew.starter.extension.tenant.context.TenantContextHolder;

import java.net.URI;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Manages multiple independently versioned operating-platform records and their proof attachments. */
@Service
@RequiredArgsConstructor
public class OperatingPlatformService {

    private final MerchantScopeAuthorizationService merchantScopeAuthorizationService;
    private final OnboardingDraftRepository draftRepository;
    private final OperatingPlatformRepository platformRepository;
    private final KycAttachmentRepository attachmentRepository;
    private final IdentifierGenerator identifierGenerator;
    private final SecurityAuditWriter securityAuditWriter;
    private final Clock clock = Clock.systemDefaultZone();

    public List<OperatingPlatform> list(Long tenantId, Long actorUserId, Long merchantId, Long applicationId) {
        DraftContext context = context(tenantId, actorUserId, merchantId, applicationId);
        return platformRepository.list(tenantId, context.draft().kycVersionId());
    }

    @Transactional
    public OperatingPlatform create(Long tenantId,
                                    Long actorUserId,
                                    Long merchantId,
                                    Long applicationId,
                                    String platformCode,
                                    String storeName,
                                    String storeUrl,
                                    String storeIdentifier,
                                    OperatingPlatform.CertificationStatus certificationStatus,
                                    String ipAddress) {
        DraftContext context = context(tenantId, actorUserId, merchantId, applicationId);
        PlatformValues values = validate(platformCode, storeName, storeUrl, storeIdentifier, certificationStatus);
        OperatingPlatform created = platformRepository.insert(identifierGenerator.nextId(new Object())
            .longValue(), tenantId, context.draft().kycVersionId(), values.platformCode(), values.storeName(), values
                .storeUrl(), values.storeIdentifier(), values.certificationStatus(), actorUserId, LocalDateTime
                    .now(clock));
        audit(tenantId, actorUserId, context.merchant(), context.draft(), created
            .id(), "OPERATING_PLATFORM_CREATE", "platform=%s;storeIdentifier=%s".formatted(created
                .platformCode(), created.storeIdentifier()), ipAddress);
        return created;
    }

    @Transactional
    public OperatingPlatform update(Long tenantId,
                                    Long actorUserId,
                                    Long merchantId,
                                    Long applicationId,
                                    Long platformId,
                                    String storeName,
                                    String storeUrl,
                                    String storeIdentifier,
                                    OperatingPlatform.CertificationStatus certificationStatus,
                                    Long expectedVersion,
                                    String ipAddress) {
        DraftContext context = context(tenantId, actorUserId, merchantId, applicationId);
        OperatingPlatform current = platformRepository.findById(tenantId, context.draft().kycVersionId(), platformId)
            .orElseThrow(MerchantAccessDeniedException::new);
        PlatformValues values = validate(current
            .platformCode(), storeName, storeUrl, storeIdentifier, certificationStatus);
        if (expectedVersion == null || !current.rowVersion().equals(expectedVersion) || !platformRepository
            .update(tenantId, current.kycVersionId(), platformId, values.storeName(), values.storeUrl(), values
                .storeIdentifier(), values.certificationStatus(), expectedVersion, LocalDateTime.now(clock))) {
            throw new OnboardingDraftConflictException();
        }
        OperatingPlatform updated = platformRepository.findById(tenantId, current.kycVersionId(), platformId)
            .orElseThrow(MerchantAccessDeniedException::new);
        audit(tenantId, actorUserId, context.merchant(), context
            .draft(), platformId, "OPERATING_PLATFORM_UPDATE", "platform=%s;version=%s".formatted(updated
                .platformCode(), updated.rowVersion()), ipAddress);
        return updated;
    }

    @Transactional
    public OperatingPlatform linkProof(Long tenantId,
                                       Long actorUserId,
                                       Long merchantId,
                                       Long applicationId,
                                       Long platformId,
                                       Long attachmentId,
                                       String evidenceType,
                                       String ipAddress) {
        DraftContext context = context(tenantId, actorUserId, merchantId, applicationId);
        OperatingPlatform platform = platformRepository.findById(tenantId, context.draft().kycVersionId(), platformId)
            .orElseThrow(MerchantAccessDeniedException::new);
        KycAttachment attachment = attachmentRepository.findById(tenantId, attachmentId)
            .filter(item -> item.kycVersionId().equals(context.draft().kycVersionId()))
            .orElseThrow(MerchantAccessDeniedException::new);
        String normalizedType = required(evidenceType, 64, "evidenceType").toUpperCase(Locale.ROOT);
        if (!normalizedType.equals(attachment.evidenceType())) {
            throw new MerchantDomainException("Platform proof type must match the attachment evidence type");
        }
        Set<String> allowedTypes = new HashSet<>();
        context.draft()
            .requirementSummary()
            .requiredEvidenceTypes()
            .forEach(value -> allowedTypes.add(value.toUpperCase(Locale.ROOT)));
        context.draft()
            .requirementSummary()
            .optionalEvidenceTypes()
            .forEach(value -> allowedTypes.add(value.toUpperCase(Locale.ROOT)));
        if (!allowedTypes.contains(normalizedType)) {
            throw new MerchantDomainException("Platform proof type is not allowed by the draft requirement version");
        }
        platformRepository.linkProof(identifierGenerator.nextId(new Object()).longValue(), tenantId, platform
            .kycVersionId(), platformId, attachmentId, normalizedType, actorUserId, LocalDateTime.now(clock));
        OperatingPlatform updated = platformRepository.findById(tenantId, platform.kycVersionId(), platformId)
            .orElseThrow(MerchantAccessDeniedException::new);
        audit(tenantId, actorUserId, context.merchant(), context
            .draft(), platformId, "OPERATING_PLATFORM_PROOF_LINK", "attachmentId=%s;evidenceType=%s"
                .formatted(attachmentId, normalizedType), ipAddress);
        return updated;
    }

    private DraftContext context(Long tenantId, Long actorUserId, Long merchantId, Long applicationId) {
        requireTenant(tenantId);
        Merchant merchant = merchantScopeAuthorizationService.requireAccessible(tenantId, actorUserId, merchantId);
        OnboardingDraft draft = draftRepository.findByApplicationId(tenantId, merchantId, applicationId)
            .orElseThrow(MerchantAccessDeniedException::new);
        return new DraftContext(merchant, draft);
    }

    private PlatformValues validate(String platformCode,
                                    String storeName,
                                    String storeUrl,
                                    String storeIdentifier,
                                    OperatingPlatform.CertificationStatus certificationStatus) {
        String normalizedUrl = storeUrl == null || storeUrl.isBlank() ? null : storeUrl.trim();
        if (normalizedUrl != null) {
            try {
                URI uri = URI.create(normalizedUrl);
                if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) || uri
                    .getHost() == null || normalizedUrl.length() > 1000) {
                    throw new IllegalArgumentException();
                }
            } catch (IllegalArgumentException ex) {
                throw new MerchantDomainException("Operating platform store URL is invalid");
            }
        }
        if (certificationStatus == null) {
            throw new MerchantDomainException("Operating platform certification status is required");
        }
        return new PlatformValues(required(platformCode, 64, "platformCode")
            .toUpperCase(Locale.ROOT), required(storeName, 200, "storeName"), normalizedUrl, required(storeIdentifier, 128, "storeIdentifier"), certificationStatus);
    }

    private String required(String value, int maxLength, String name) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new MerchantDomainException(name + " is invalid");
        }
        return value.trim();
    }

    private void audit(Long tenantId,
                       Long actorUserId,
                       Merchant merchant,
                       OnboardingDraft draft,
                       Long platformId,
                       String action,
                       String reason,
                       String ipAddress) {
        securityAuditWriter.append(new SecurityAuditRecord(tenantId, actorUserId, merchant
            .owningAgentId(), action, "OPERATING_PLATFORM", platformId, draft
                .rowVersion(), "KYC_VERSION", reason, ipAddress, SecurityAuditResult.SUCCESS, null, LocalDateTime
                    .now(clock)));
    }

    private void requireTenant(Long tenantId) {
        if (tenantId == null || !tenantId.equals(TenantContextHolder.getTenantId())) {
            throw new MerchantAccessDeniedException();
        }
    }

    private record DraftContext(Merchant merchant, OnboardingDraft draft) {}

    private record PlatformValues(String platformCode, String storeName, String storeUrl, String storeIdentifier,
                                  OperatingPlatform.CertificationStatus certificationStatus) {}
}
