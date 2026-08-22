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

package top.continew.admin.workflow.dto;

import java.time.LocalDateTime;

/** Durable versioned business object to Flowable process-instance mapping. */
public record WorkflowInstanceMapping(Long mappingId, Long tenantId, String businessType, Long businessId,
                                      Long businessVersion, String processDefinitionId, String processDefinitionKey,
                                      Integer processDefinitionVersion, String processInstanceId, String businessKey,
                                      String workflowStatus, LocalDateTime startedTime, LocalDateTime endedTime,
                                      Long rowVersion) {

    public WorkflowRef toRef() {
        return new WorkflowRef(mappingId, processInstanceId, businessKey, processDefinitionId, processDefinitionKey, processDefinitionVersion, String
            .valueOf(tenantId));
    }
}
