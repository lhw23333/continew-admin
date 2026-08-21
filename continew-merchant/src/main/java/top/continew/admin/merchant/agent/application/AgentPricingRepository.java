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

package top.continew.admin.merchant.agent.application;

import top.continew.admin.merchant.agent.domain.AgentPricingVersion;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Append-only persistence port for tenant-bound agent pricing versions. */
public interface AgentPricingRepository {

    Optional<AgentPricingVersion> findById(Long tenantId, Long pricingVersionId);

    Optional<AgentPricingVersion> findEffective(Long tenantId,
                                                Long agentId,
                                                String channelCode,
                                                String productCode,
                                                String currency,
                                                LocalDateTime effectiveAt);

    List<AgentPricingVersion> list(Long tenantId,
                                   Long agentId,
                                   String channelCode,
                                   String productCode,
                                   String currency);

    int nextVersionNo(Long tenantId, Long agentId, String channelCode, String productCode, String currency);

    AgentPricingVersion insert(AgentPricingVersionDraft draft);
}
