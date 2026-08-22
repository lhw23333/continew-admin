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

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.continew.admin.channel.dto.ChannelRequirementSummary;
import top.continew.admin.merchant.kyc.attachment.KycAttachment;
import top.continew.admin.merchant.kyc.attachment.KycAttachmentException;
import top.continew.admin.merchant.kyc.attachment.KycAttachmentRepository;
import top.continew.admin.merchant.kyc.attachment.KycAttachmentScanStatus;
import top.continew.admin.merchant.kyc.attachment.KycAttachmentValidationStatus;
import top.continew.admin.merchant.kyc.attachment.KycEvidenceRequirementPort;
import top.continew.admin.merchant.master.application.MerchantScopeAuthorizationService;
import top.continew.admin.merchant.master.domain.MerchantAccessDeniedException;
import top.continew.starter.extension.tenant.context.TenantContextHolder;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Enforces the exact draft requirement snapshot and reports evidence completeness. */
@Service
@RequiredArgsConstructor
public class OnboardingEvidenceService implements KycEvidenceRequirementPort {

    private final OnboardingDraftRepository draftRepository;
    private final KycAttachmentRepository attachmentRepository;
    private final MerchantScopeAuthorizationService merchantScopeAuthorizationService;

    @Override
    public EvidenceRule requireUploadAllowed(Long tenantId, Long actorUserId, Long kycVersionId, String evidenceType) {
        requireTenant(tenantId);
        OnboardingDraft draft = draftRepository.findByKycVersionId(tenantId, kycVersionId)
            .orElseThrow(MerchantAccessDeniedException::new);
        merchantScopeAuthorizationService.requireAccessible(tenantId, actorUserId, draft.merchantId());
        String normalizedType = normalizeType(evidenceType);
        ChannelRequirementSummary requirements = draft.requirementSummary();
        Set<String> required = normalized(requirements.requiredEvidenceTypes());
        Set<String> optional = normalized(requirements.optionalEvidenceTypes());
        if (!required.contains(normalizedType) && !optional.contains(normalizedType)) {
            throw new KycAttachmentException("Evidence type is not allowed by the draft requirement version");
        }
        return new EvidenceRule(draft.requirementVersion(), required.contains(normalizedType), optional, requirements
            .maxSupplementAttachments());
    }

    public OnboardingEvidenceSummary summary(Long tenantId, Long actorUserId, Long merchantId, Long applicationId) {
        requireTenant(tenantId);
        merchantScopeAuthorizationService.requireAccessible(tenantId, actorUserId, merchantId);
        OnboardingDraft draft = draftRepository.findByApplicationId(tenantId, merchantId, applicationId)
            .orElseThrow(MerchantAccessDeniedException::new);
        List<KycAttachment> attachments = attachmentRepository.listByKycVersion(tenantId, draft.kycVersionId());
        Set<String> required = normalized(draft.requirementSummary().requiredEvidenceTypes());
        Set<String> optional = normalized(draft.requirementSummary().optionalEvidenceTypes());
        LinkedHashSet<String> allTypes = new LinkedHashSet<>(required);
        allTypes.addAll(optional);
        List<OnboardingEvidenceSummary.EvidenceTypeStatus> statuses = new ArrayList<>();
        for (String type : allTypes) {
            List<KycAttachment> matching = attachments.stream()
                .filter(item -> type.equals(item.evidenceType()))
                .toList();
            int clean = (int)matching.stream().filter(KycAttachment::isAccessible).count();
            int pending = (int)matching.stream()
                .filter(item -> KycAttachmentScanStatus.UNAVAILABLE.equals(item.scanStatus()))
                .count();
            int invalid = (int)matching.stream()
                .filter(item -> KycAttachmentScanStatus.INFECTED.equals(item
                    .scanStatus()) || !KycAttachmentValidationStatus.VALID.equals(item.validationStatus()))
                .count();
            boolean itemComplete = !required.contains(type) || clean > 0;
            statuses.add(new OnboardingEvidenceSummary.EvidenceTypeStatus(type, required.contains(type), matching
                .size(), clean, pending, invalid, itemComplete));
        }
        boolean complete = statuses.stream()
            .filter(OnboardingEvidenceSummary.EvidenceTypeStatus::required)
            .allMatch(OnboardingEvidenceSummary.EvidenceTypeStatus::complete);
        List<OnboardingEvidenceSummary.EvidenceAttachmentView> views = attachments.stream()
            .map(item -> new OnboardingEvidenceSummary.EvidenceAttachmentView(item.id(), item.evidenceType(), item
                .originalName(), item.detectedMime(), item.sizeBytes(), item.scanStatus().name(), item
                    .validationStatus()
                    .name(), item.sort()))
            .toList();
        return new OnboardingEvidenceSummary(applicationId, draft.kycVersionId(), draft
            .requirementVersion(), complete, statuses, views);
    }

    private Set<String> normalized(List<String> values) {
        return values.stream().map(this::normalizeType).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private String normalizeType(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 64) {
            throw new KycAttachmentException("Evidence type is invalid");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private void requireTenant(Long tenantId) {
        if (tenantId == null || !tenantId.equals(TenantContextHolder.getTenantId())) {
            throw new MerchantAccessDeniedException();
        }
    }
}
