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

package top.continew.admin.merchant.agent.domain;

import java.time.LocalDateTime;

/** Append-only agent pricing version for one channel, product, and currency. */
public record AgentPricingVersion(Long id, Long tenantId, Long agentId, Long parentPricingVersionId, Integer versionNo,
                                  String channelCode, String productCode, String currency, AgentPricingRules rules,
                                  LocalDateTime effectiveTime, LocalDateTime expiresTime, AgentPricingStatus status,
                                  Long createUser, LocalDateTime createTime) {

    public AgentPricingVersion {
        if (id == null || tenantId == null || agentId == null || versionNo == null || versionNo <= 0 || rules == null || effectiveTime == null || status == null || createUser == null || createTime == null) {
            throw new IllegalArgumentException("Required agent pricing fields must not be null");
        }
        if (expiresTime != null && !expiresTime.isAfter(effectiveTime)) {
            throw new AgentDomainException("Pricing expiry must be after the effective time");
        }
    }

    public boolean isEffectiveAt(LocalDateTime time) {
        return !effectiveTime.isAfter(time) && (expiresTime == null || expiresTime.isAfter(time));
    }
}
