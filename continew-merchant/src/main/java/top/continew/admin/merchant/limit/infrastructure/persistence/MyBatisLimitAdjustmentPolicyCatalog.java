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

package top.continew.admin.merchant.limit.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import top.continew.admin.merchant.limit.application.LimitAdjustmentPolicyCatalog;
import top.continew.admin.merchant.limit.domain.LimitAdjustmentPolicy;
import top.continew.admin.merchant.limit.domain.LimitAdjustmentPolicyStatus;

import java.time.LocalDateTime;
import java.util.Optional;

/** Selects the latest effective append-only amount policy for one exact dimension. */
@Repository
@RequiredArgsConstructor
public class MyBatisLimitAdjustmentPolicyCatalog implements LimitAdjustmentPolicyCatalog {

    private final LimitAdjustmentPolicyVersionMapper mapper;

    @Override
    public Optional<LimitAdjustmentPolicy> findEffective(Long tenantId,
                                                         String channelCode,
                                                         String platformCode,
                                                         String currency,
                                                         LocalDateTime effectiveAt) {
        LimitAdjustmentPolicyVersionDO row = mapper.lambdaQuery()
            .eq(LimitAdjustmentPolicyVersionDO::getTenantId, tenantId)
            .eq(LimitAdjustmentPolicyVersionDO::getChannelCode, channelCode)
            .eq(LimitAdjustmentPolicyVersionDO::getPlatformCode, platformCode)
            .eq(LimitAdjustmentPolicyVersionDO::getCurrency, currency)
            .eq(LimitAdjustmentPolicyVersionDO::getStatus, LimitAdjustmentPolicyStatus.ENABLED)
            .le(LimitAdjustmentPolicyVersionDO::getEffectiveTime, effectiveAt)
            .and(wrapper -> wrapper.isNull(LimitAdjustmentPolicyVersionDO::getExpiresTime)
                .or()
                .gt(LimitAdjustmentPolicyVersionDO::getExpiresTime, effectiveAt))
            .eq(LimitAdjustmentPolicyVersionDO::getDeleted, 0L)
            .orderByDesc(LimitAdjustmentPolicyVersionDO::getEffectiveTime)
            .orderByDesc(LimitAdjustmentPolicyVersionDO::getId)
            .last("LIMIT 1")
            .one();
        return Optional.ofNullable(row).map(this::toDomain);
    }

    private LimitAdjustmentPolicy toDomain(LimitAdjustmentPolicyVersionDO row) {
        return new LimitAdjustmentPolicy(row.getId(), row.getTenantId(), row.getChannelCode(), row
            .getPlatformCode(), row.getCurrency().trim(), row.getPolicyVersion(), row.getMinimumLimit(), row
                .getMaximumLimit(), row.getCurrencyScale(), row.getRoundingUnit(), row.getRoundingMode(), row
                    .getStatus(), row.getEffectiveTime(), row.getExpiresTime(), row.getCreateTime());
    }
}
