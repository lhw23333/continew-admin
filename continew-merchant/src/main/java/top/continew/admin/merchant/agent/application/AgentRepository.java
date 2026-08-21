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

import top.continew.admin.merchant.agent.domain.Agent;

import java.util.Optional;
import java.util.List;

/** Tenant-explicit agent persistence port. */
public interface AgentRepository {

    Optional<Agent> findById(Long tenantId, Long agentId);

    Optional<Agent> findByUserId(Long tenantId, Long userId);

    Optional<Agent> findByPromotionCode(Long tenantId, String promotionCode);

    boolean existsById(Long tenantId, Long agentId);

    boolean existsByAgentNo(Long tenantId, String agentNo);

    boolean existsByUserId(Long tenantId, Long userId);

    boolean existsByPromotionCode(Long tenantId, String promotionCode);

    AgentPage page(Long tenantId, List<Long> authorizedAgentIds, AgentListQuery query);

    boolean bindDepartment(Long tenantId, Long agentId, Long deptId);

    boolean updateProfile(Agent agent, Long expectedVersion);

    boolean updatePromotionCode(Agent agent, Long expectedVersion);

    void insert(Agent agent);

    boolean updateLifecycle(Agent agent, Long expectedVersion);
}
