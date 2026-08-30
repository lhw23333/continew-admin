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

package top.continew.admin.workflow.query;

import java.time.LocalDateTime;

/** Tenant-scoped todo/claimed query; candidate role codes are always resolved server-side. */
public record WorkflowTaskQuery(Long tenantId, Long userId, String processDefinitionKey, String businessKey,
                                String taskName, String taskDefinitionKey, LocalDateTime dueBefore,
                                boolean claimedOnly, int page, int size) {

    public WorkflowTaskQuery(Long tenantId,
                             Long userId,
                             String processDefinitionKey,
                             String businessKey,
                             String taskName,
                             boolean claimedOnly,
                             int page,
                             int size) {
        this(tenantId, userId, processDefinitionKey, businessKey, taskName, null, null, claimedOnly, page, size);
    }
}
