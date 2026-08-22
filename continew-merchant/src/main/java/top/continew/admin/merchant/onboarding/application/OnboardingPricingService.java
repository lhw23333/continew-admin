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
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.merchant.agent.domain.AgentPricingVersion;
import top.continew.admin.merchant.master.application.MerchantScopeAuthorizationService;
import top.continew.admin.merchant.master.domain.Merchant;
import top.continew.admin.merchant.master.domain.MerchantAccessDeniedException;
import top.continew.admin.merchant.security.audit.application.SecurityAuditWriter;
import top.continew.admin.merchant.security.audit.domain.SecurityAuditRecord;
import top.continew.admin.merchant.security.audit.domain.SecurityAuditResult;
import top.continew.starter.extension.tenant.context.TenantContextHolder;

import java.time.Clock;
import java.time.LocalDateTime;

/** Selects an exact pricing version after current parent-bound validation. */
@Service
@RequiredArgsConstructor
public class OnboardingPricingService {

    private final MerchantScopeAuthorizationService merchantScopeAuthorizationService;
    private final OnboardingDraftRepository draftRepository;
    private final OnboardingPricingValidator pricingValidator;
    private final OnboardingPricingRepository pricingSelectionRepository;
    private final SecurityAuditWriter securityAuditWriter;
    private final Clock clock = Clock.systemDefaultZone();

    @Transactional
    public OnboardingPricingView select(Long tenantId,
                                        Long actorUserId,
                                        Long merchantId,
                                        Long applicationId,
                                        Long pricingVersionId,
                                        Long expectedVersion,
                                        String ipAddress) {
        requireTenant(tenantId);
        Merchant merchant = merchantScopeAuthorizationService.requireAccessible(tenantId, actorUserId, merchantId);
        OnboardingDraft draft = draftRepository.findByApplicationId(tenantId, merchantId, applicationId)
            .orElseThrow(MerchantAccessDeniedException::new);
        if (!draft.rowVersion().equals(expectedVersion)) {
            throw new OnboardingDraftConflictException();
        }
        LocalDateTime now = LocalDateTime.now(clock);
        AgentPricingVersion selected = pricingValidator.requireValid(tenantId, merchant, draft, pricingVersionId, now);
        if (!pricingSelectionRepository.update(tenantId, merchantId, applicationId, draft.kycVersionId(), selected
            .id(), expectedVersion, now)) {
            throw new OnboardingDraftConflictException();
        }
        securityAuditWriter.append(new SecurityAuditRecord(tenantId, actorUserId, merchant
            .owningAgentId(), "ONBOARDING_PRICING_SELECT", "KYC_VERSION", draft
                .kycVersionId(), expectedVersion + 1, "PRICING_VERSION", "pricingVersionId=%s;channel=%s;product=%s"
                    .formatted(selected.id(), selected.channelCode(), selected
                        .productCode()), ipAddress, SecurityAuditResult.SUCCESS, null, now));
        return new OnboardingPricingView(draft.kycVersionId(), expectedVersion + 1, selected.id(), selected
            .versionNo(), selected.channelCode(), selected.productCode(), selected.currency(), selected
                .rules(), selected.effectiveTime(), selected.expiresTime());
    }

    private void requireTenant(Long tenantId) {
        if (tenantId == null || !tenantId.equals(TenantContextHolder.getTenantId())) {
            throw new MerchantAccessDeniedException();
        }
    }
}
