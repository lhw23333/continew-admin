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
import java.util.List;

/** Process definition/version, task chain, and activity history without raw variables. */
public record WorkflowProcessHistory(String processInstanceId, String businessKey, String processDefinitionId,
                                     String processDefinitionKey, Integer processDefinitionVersion, String tenantId,
                                     LocalDateTime startTime, LocalDateTime endTime, boolean ended,
                                     List<WorkflowActivityHistory> activities, List<WorkflowTask> tasks) {

    public WorkflowProcessHistory {
        activities = List.copyOf(activities);
        tasks = List.copyOf(tasks);
    }
}
