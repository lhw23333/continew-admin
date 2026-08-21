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
import top.continew.admin.merchant.agent.domain.AgentStatus;

import java.time.LocalDateTime;

/** Masked agent list/detail view. */
public record AgentSummary(Long id, Long parentId, Long deptId, String agentNo, String name, String contactName,
                           String contactMobileMasked, String remarks, AgentStatus status, Long rowVersion,
                           LocalDateTime createTime, LocalDateTime updateTime) {

    public static AgentSummary from(Agent agent) {
        return new AgentSummary(agent.id(), agent.parentId(), agent.deptId(), agent.agentNo(), agent.name(), agent
            .contactName(), agent.contactMobile() == null ? null : agent.contactMobile().maskedValue(), agent
                .remarks(), agent.status(), agent.rowVersion(), agent.createTime(), agent.updateTime());
    }
}
