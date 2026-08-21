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
import top.continew.admin.channel.api.ChannelProductCatalog;
import top.continew.admin.channel.dto.ChannelProductKey;
import top.continew.admin.channel.dto.ChannelProductVersion;
import top.continew.admin.merchant.agent.application.AgentMerchantDefaultRepository;
import top.continew.admin.merchant.agent.application.AgentPricingRepository;
import top.continew.admin.merchant.agent.domain.AgentMerchantDefaultProduct;
import top.continew.admin.merchant.agent.domain.AgentMerchantDefaultVersion;
import top.continew.admin.merchant.agent.domain.AgentPricingStatus;
import top.continew.admin.merchant.agent.domain.AgentPricingVersion;
import top.continew.admin.merchant.master.application.MerchantOperationPolicyService;
import top.continew.admin.merchant.master.application.MerchantOperationPolicyService.MerchantOperation;
import top.continew.admin.merchant.master.application.MerchantScopeAuthorizationService;
import top.continew.admin.merchant.master.domain.Merchant;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Resolves onboarding channels from current merchant scope, agent products, channel status, and merchant type. */
@Service
@RequiredArgsConstructor
public class ChannelEligibilityService {

    private final MerchantScopeAuthorizationService merchantScopeAuthorizationService;
    private final MerchantOperationPolicyService merchantOperationPolicyService;
    private final AgentMerchantDefaultRepository agentMerchantDefaultRepository;
    private final AgentPricingRepository agentPricingRepository;
    private final ChannelProductCatalog channelProductCatalog;
    private final Clock clock = Clock.systemDefaultZone();

    public List<EligibleChannel> list(Long tenantId, Long actorUserId, Long merchantId) {
        Merchant merchant = merchantScopeAuthorizationService.requireAccessible(tenantId, actorUserId, merchantId);
        merchantOperationPolicyService.requireAllowed(tenantId, merchantId, MerchantOperation.NEW_ONBOARDING);
        LocalDateTime now = LocalDateTime.now(clock);
        AgentMerchantDefaultVersion defaults = agentMerchantDefaultRepository.findEffective(tenantId, merchant
            .owningAgentId(), now).orElse(null);
        if (defaults == null) {
            return List.of();
        }
        Set<ChannelProductKey> keys = defaults.defaults()
            .products()
            .stream()
            .map(product -> new ChannelProductKey(product.channelCode(), product.productCode()))
            .collect(Collectors.toSet());
        Map<String, ChannelProductVersion> configurations = channelProductCatalog.listEffective(tenantId, keys, now)
            .stream()
            .collect(Collectors.toMap(version -> version.key().dimensionKey(), Function.identity()));
        return defaults.defaults()
            .products()
            .stream()
            .map(product -> eligible(tenantId, merchant, defaults, product, configurations, now))
            .filter(java.util.Objects::nonNull)
            .sorted(Comparator.comparing(EligibleChannel::channelCode).thenComparing(EligibleChannel::productCode))
            .toList();
    }

    private EligibleChannel eligible(Long tenantId,
                                     Merchant merchant,
                                     AgentMerchantDefaultVersion defaults,
                                     AgentMerchantDefaultProduct product,
                                     Map<String, ChannelProductVersion> configurations,
                                     LocalDateTime now) {
        String dimension = new ChannelProductKey(product.channelCode(), product.productCode()).dimensionKey();
        ChannelProductVersion configuration = configurations.get(dimension);
        if (configuration == null || !configuration.isEnabledFor(merchant.merchantType().name())) {
            return null;
        }
        AgentPricingVersion pricing = agentPricingRepository.findById(tenantId, product.pricingVersionId())
            .orElse(null);
        if (pricing == null || !pricing.agentId().equals(merchant.owningAgentId()) || !pricing.channelCode()
            .equals(product.channelCode()) || !pricing.productCode()
                .equals(product.productCode()) || !AgentPricingStatus.PUBLISHED.equals(pricing.status()) || !pricing
                    .isEffectiveAt(now)) {
            return null;
        }
        return new EligibleChannel(merchant.id(), merchant.owningAgentId(), defaults.id(), product
            .channelCode(), product.productCode(), configuration.configVersion(), configuration
                .requirementVersion(), pricing.id(), configuration.requirements(), now);
    }
}
