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

import top.continew.admin.merchant.agent.domain.AgentStatus;

/** Combined agent list filters with bounded deterministic pagination. */
public record AgentListQuery(Long agentId, String name, AgentStatus status, int page, int size, String ipAddress) {

    public AgentListQuery {
        if (agentId != null && agentId <= 0) {
            throw new IllegalArgumentException("agentId must be positive");
        }
        name = name == null || name.isBlank() ? null : name.trim();
        if (name != null && name.length() > 100) {
            throw new IllegalArgumentException("Agent name filter is too long");
        }
        if (page < 1 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Agent page parameters are invalid");
        }
        ipAddress = ipAddress == null || ipAddress.isBlank() ? null : ipAddress.trim();
    }
}
