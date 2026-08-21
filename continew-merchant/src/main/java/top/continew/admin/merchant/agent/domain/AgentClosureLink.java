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

/** One ancestor/descendant relationship in an agent closure table. */
public record AgentClosureLink(Long tenantId, Long ancestorId, Long descendantId, Integer depth,
                               LocalDateTime createTime) {
    public AgentClosureLink {
        if (tenantId == null || ancestorId == null || descendantId == null || depth == null || depth < 0 || createTime == null) {
            throw new IllegalArgumentException("Invalid agent closure link");
        }
    }
}
