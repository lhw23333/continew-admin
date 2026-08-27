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

package top.continew.admin.merchant.limit.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.continew.admin.channel.api.ChannelConnectionConfigCatalog;
import top.continew.admin.channel.api.ChannelProductCatalog;
import top.continew.admin.channel.dto.ChannelOperation;
import top.continew.admin.channel.dto.ChannelProductKey;
import top.continew.admin.channel.dto.ChannelProductVersion;
import top.continew.admin.merchant.master.application.MerchantChannelSummary;
import top.continew.admin.merchant.master.application.MerchantRepository;
import top.continew.admin.merchant.master.domain.Merchant;
import top.continew.admin.merchant.master.domain.MerchantDomainException;
import top.continew.admin.merchant.master.domain.MerchantStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** Resolves a server-owned eligibility/configuration snapshot for a limit request. */
@Service
@RequiredArgsConstructor
public class LimitAdjustmentEligibilityService implements LimitAdjustmentEligibilityPort {

    private final MerchantRepository merchantRepository;
    private final ChannelProductCatalog channelProductCatalog;
    private final ChannelConnectionConfigCatalog connectionConfigCatalog;

    @Override
    public LimitAdjustmentEligibility requireEligible(Long tenantId,
                                                      Merchant merchant,
                                                      String channelCode,
                                                      LocalDateTime effectiveAt) {
        if (!MerchantStatus.ENABLED.equals(merchant.status())) {
            throw ineligible();
        }
        MerchantChannelSummary onboarding = merchantRepository.listLatestChannelSummaries(tenantId, List.of(merchant
            .id()))
            .stream()
            .filter(summary -> channelCode.equals(summary.channelCode()) && summary.isSuccessfullyOnboarded() && summary
                .pricing() != null)
            .findFirst()
            .orElseThrow(this::ineligible);
        ChannelProductKey productKey = new ChannelProductKey(channelCode, onboarding.pricing().productCode());
        ChannelProductVersion product = channelProductCatalog.listEffective(tenantId, Set.of(productKey), effectiveAt)
            .stream()
            .filter(version -> version.key().equals(productKey) && version.isEnabledFor(merchant.merchantType().name()))
            .findFirst()
            .orElseThrow(this::ineligible);
        var connection = connectionConfigCatalog.findEffective(tenantId, productKey, effectiveAt)
            .filter(config -> config.endpoints().operationPaths().containsKey(ChannelOperation.ADJUST_LIMIT))
            .orElseThrow(this::ineligible);
        return new LimitAdjustmentEligibility(onboarding.applicationId(), productKey.productCode(), product
            .configVersion(), connection.configVersion());
    }

    private MerchantDomainException ineligible() {
        return new MerchantDomainException("Merchant channel is not eligible for limit adjustment");
    }
}
