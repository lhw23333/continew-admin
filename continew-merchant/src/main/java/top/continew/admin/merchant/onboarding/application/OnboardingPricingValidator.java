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
import top.continew.admin.merchant.agent.application.AgentPricingRepository;
import top.continew.admin.merchant.agent.application.AgentRepository;
import top.continew.admin.merchant.agent.domain.Agent;
import top.continew.admin.merchant.agent.domain.AgentPricingBoundaryException;
import top.continew.admin.merchant.agent.domain.AgentPricingStatus;
import top.continew.admin.merchant.agent.domain.AgentPricingVersion;
import top.continew.admin.merchant.master.domain.Merchant;

import java.time.LocalDateTime;

/** Revalidates an exact draft pricing version against the owning agent and current parent boundary. */
@Service
@RequiredArgsConstructor
public class OnboardingPricingValidator {

    private final AgentRepository agentRepository;
    private final AgentPricingRepository pricingRepository;

    public AgentPricingVersion requireValid(Long tenantId,
                                            Merchant merchant,
                                            OnboardingDraft draft,
                                            Long pricingVersionId,
                                            LocalDateTime effectiveAt) {
        return requireValid(tenantId, merchant, draft.channelCode(), draft
            .productCode(), pricingVersionId, effectiveAt);
    }

    public AgentPricingVersion requireValid(Long tenantId,
                                            Merchant merchant,
                                            String channelCode,
                                            String productCode,
                                            Long pricingVersionId,
                                            LocalDateTime effectiveAt) {
        AgentPricingVersion selected = pricingRepository.findById(tenantId, pricingVersionId)
            .orElseThrow(() -> new AgentPricingBoundaryException("Selected pricing version is not available"));
        if (!selected.agentId().equals(merchant.owningAgentId()) || !selected.channelCode()
            .equals(channelCode) || !selected.productCode().equals(productCode) || !AgentPricingStatus.PUBLISHED
                .equals(selected.status()) || !selected.isEffectiveAt(effectiveAt)) {
            throw new AgentPricingBoundaryException("Selected pricing version is outside the merchant draft scope");
        }
        Agent owningAgent = agentRepository.findById(tenantId, merchant.owningAgentId())
            .orElseThrow(() -> new AgentPricingBoundaryException("Owning agent is not available"));
        if (owningAgent.parentId() != null && owningAgent.parentId() > 0) {
            AgentPricingVersion parent = pricingRepository.findEffective(tenantId, owningAgent.parentId(), selected
                .channelCode(), selected.productCode(), selected.currency(), effectiveAt)
                .orElseThrow(() -> new AgentPricingBoundaryException("Current parent pricing boundary is not available"));
            selected.rules().requireWithin(parent.rules());
        }
        return selected;
    }
}
