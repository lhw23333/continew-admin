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

/** Append-only effective merchant-default version for one agent. */
public record AgentMerchantDefaultVersion(Long id, Long tenantId, Long agentId, Integer versionNo,
                                          AgentMerchantDefaults defaults, LocalDateTime effectiveTime,
                                          LocalDateTime expiresTime, AgentMerchantDefaultStatus status, Long createUser,
                                          LocalDateTime createTime) {

    public AgentMerchantDefaultVersion {
        if (id == null || tenantId == null || agentId == null || versionNo == null || versionNo <= 0 || defaults == null || effectiveTime == null || status == null || createUser == null || createTime == null) {
            throw new IllegalArgumentException("Required agent merchant-default fields must not be null");
        }
        if (expiresTime != null && !expiresTime.isAfter(effectiveTime)) {
            throw new AgentDomainException("Merchant-default expiry must be after the effective time");
        }
    }
}
