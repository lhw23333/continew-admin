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
import top.continew.admin.merchant.agent.application.AgentMerchantDefaultService;
import top.continew.admin.merchant.master.application.MerchantScopeAuthorizationService;
import top.continew.admin.merchant.master.domain.Merchant;
import top.continew.admin.merchant.master.domain.MerchantAccessDeniedException;
import top.continew.admin.merchant.master.domain.MerchantDomainException;
import top.continew.admin.merchant.master.domain.MerchantStatus;
import top.continew.admin.merchant.security.audit.application.SecurityAuditWriter;
import top.continew.admin.merchant.security.audit.domain.SecurityAuditRecord;
import top.continew.admin.merchant.security.audit.domain.SecurityAuditResult;
import top.continew.starter.extension.tenant.context.TenantContextHolder;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Creates, explicitly saves, and restores versioned onboarding drafts. */
@Service
@RequiredArgsConstructor
public class OnboardingDraftService {

    private final OnboardingDraftRepository draftRepository;
    private final ChannelEligibilityService channelEligibilityService;
    private final MerchantScopeAuthorizationService merchantScopeAuthorizationService;
    private final AgentMerchantDefaultService agentMerchantDefaultService;
    private final IdentifierGenerator identifierGenerator;
    private final SecurityAuditWriter securityAuditWriter;
    private final Clock clock = Clock.systemDefaultZone();

    @Transactional
    public OnboardingDraftView createOrLoad(Long tenantId,
                                            Long actorUserId,
                                            Long merchantId,
                                            String channelCode,
                                            String productCode,
                                            String ipAddress) {
        requireTenantContext(tenantId);
        Merchant merchant = merchantScopeAuthorizationService.requireAccessible(tenantId, actorUserId, merchantId);
        String normalizedChannel = requiredCode(channelCode, "channelCode");
        String normalizedProduct = requiredCode(productCode, "productCode");
        OnboardingDraft existing = draftRepository
            .findActive(tenantId, merchantId, normalizedChannel, normalizedProduct)
            .orElse(null);
        if (existing != null) {
            return view(tenantId, actorUserId, merchant, existing);
        }
        EligibleChannel eligible = channelEligibilityService.list(tenantId, actorUserId, merchantId)
            .stream()
            .filter(item -> normalizedChannel.equals(item.channelCode()) && normalizedProduct.equals(item
                .productCode()))
            .findFirst()
            .orElseThrow(() -> new MerchantDomainException("Selected channel product is not currently eligible"));
        LocalDateTime now = LocalDateTime.now(clock);
        OnboardingDraftDraft draft = new OnboardingDraftDraft(identifierGenerator.nextId(new Object())
            .longValue(), "OD" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .toUpperCase(), tenantId, merchantId, merchant
                    .owningAgentId(), normalizedChannel, normalizedProduct, eligible.channelConfigVersion(), eligible
                        .requirementVersion(), identifierGenerator.nextId(new Object()).longValue(), draftRepository
                            .nextKycVersionNo(tenantId, merchantId), eligible.pricingVersionId(), merchant
                                .legalName(), actorUserId, now);
        try {
            draftRepository.insert(draft);
        } catch (OnboardingDraftConflictException ex) {
            OnboardingDraft concurrent = draftRepository
                .findActive(tenantId, merchantId, normalizedChannel, normalizedProduct)
                .orElseThrow(() -> ex);
            return view(tenantId, actorUserId, merchant, concurrent);
        }
        agentMerchantDefaultService.inheritIntoDraft(tenantId, actorUserId, draft.kycVersionId(), ipAddress);
        OnboardingDraft created = draftRepository.findByApplicationId(tenantId, merchantId, draft.applicationId())
            .orElseThrow(() -> new MerchantDomainException("Created onboarding draft is not available"));
        audit(tenantId, actorUserId, merchant, created, "ONBOARDING_DRAFT_CREATE", "channel=%s;product=%s;requirement=%s"
            .formatted(normalizedChannel, normalizedProduct, created.requirementVersion()), ipAddress);
        return view(tenantId, actorUserId, merchant, created);
    }

    public OnboardingDraftView load(Long tenantId, Long actorUserId, Long merchantId, Long applicationId) {
        requireTenantContext(tenantId);
        Merchant merchant = merchantScopeAuthorizationService.requireAccessible(tenantId, actorUserId, merchantId);
        OnboardingDraft draft = draftRepository.findByApplicationId(tenantId, merchantId, applicationId)
            .orElseThrow(MerchantAccessDeniedException::new);
        return view(tenantId, actorUserId, merchant, draft);
    }

    @Transactional
    public OnboardingDraftView saveProgress(Long tenantId,
                                            Long actorUserId,
                                            Long merchantId,
                                            Long applicationId,
                                            Integer savedStep,
                                            List<Integer> completedSteps,
                                            Long expectedVersion,
                                            String ipAddress) {
        requireTenantContext(tenantId);
        Merchant merchant = merchantScopeAuthorizationService.requireAccessible(tenantId, actorUserId, merchantId);
        if (MerchantStatus.DISABLED.equals(merchant.status())) {
            throw new MerchantDomainException("Disabled merchant onboarding draft cannot be changed");
        }
        OnboardingDraft current = draftRepository.findByApplicationId(tenantId, merchantId, applicationId)
            .orElseThrow(MerchantAccessDeniedException::new);
        if (!current.rowVersion().equals(expectedVersion)) {
            throw new OnboardingDraftConflictException();
        }
        List<Integer> normalizedSteps = validateProgress(savedStep, completedSteps);
        LocalDateTime now = LocalDateTime.now(clock);
        if (!draftRepository.updateProgress(tenantId, merchantId, applicationId, current
            .kycVersionId(), savedStep, normalizedSteps, expectedVersion, now)) {
            throw new OnboardingDraftConflictException();
        }
        OnboardingDraft saved = draftRepository.findByApplicationId(tenantId, merchantId, applicationId)
            .orElseThrow(MerchantAccessDeniedException::new);
        audit(tenantId, actorUserId, merchant, saved, "ONBOARDING_DRAFT_SAVE", "savedStep=%s;completed=%s"
            .formatted(savedStep, normalizedSteps), ipAddress);
        return view(tenantId, actorUserId, merchant, saved);
    }

    private OnboardingDraftView view(Long tenantId, Long actorUserId, Merchant merchant, OnboardingDraft draft) {
        if (MerchantStatus.DISABLED.equals(merchant.status())) {
            return new OnboardingDraftView(draft, false, null);
        }
        EligibleChannel current = channelEligibilityService.list(tenantId, actorUserId, merchant.id())
            .stream()
            .filter(item -> draft.channelCode().equals(item.channelCode()) && draft.productCode()
                .equals(item.productCode()))
            .findFirst()
            .orElse(null);
        return new OnboardingDraftView(draft, current != null, current == null ? null : current.requirementVersion());
    }

    private List<Integer> validateProgress(Integer savedStep, List<Integer> completedSteps) {
        if (savedStep == null || savedStep < 1 || savedStep > 5 || completedSteps == null) {
            throw new MerchantDomainException("Onboarding draft progress is invalid");
        }
        List<Integer> normalized = completedSteps.stream().distinct().sorted().toList();
        if (normalized.size() != completedSteps.size() || normalized.stream()
            .anyMatch(step -> step == null || step < 1 || step > savedStep)) {
            throw new MerchantDomainException("Onboarding completed steps are invalid");
        }
        List<Integer> expectedPrefix = new ArrayList<>();
        for (int step = 1; step <= normalized.size(); step++) {
            expectedPrefix.add(step);
        }
        if (!expectedPrefix.equals(normalized) || savedStep > normalized.size() + 1) {
            throw new MerchantDomainException("Onboarding steps must be completed in order");
        }
        return normalized;
    }

    private void audit(Long tenantId,
                       Long actorUserId,
                       Merchant merchant,
                       OnboardingDraft draft,
                       String action,
                       String reason,
                       String ipAddress) {
        securityAuditWriter.append(new SecurityAuditRecord(tenantId, actorUserId, merchant
            .owningAgentId(), action, "ONBOARDING_APPLICATION", draft.applicationId(), draft
                .rowVersion(), "KYC_VERSION", reason, ipAddress, SecurityAuditResult.SUCCESS, null, LocalDateTime
                    .now(clock)));
    }

    private String requiredCode(String value, String name) {
        if (value == null || value.isBlank() || value.trim().length() > 64) {
            throw new MerchantDomainException(name + " is invalid");
        }
        return value.trim();
    }

    private void requireTenantContext(Long tenantId) {
        if (tenantId == null || !tenantId.equals(TenantContextHolder.getTenantId())) {
            throw new MerchantAccessDeniedException();
        }
    }
}
