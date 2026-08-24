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

package top.continew.admin.workflow.api;

import top.continew.admin.workflow.command.ClaimTaskCommand;
import top.continew.admin.workflow.command.CompleteTaskCommand;
import top.continew.admin.workflow.command.StartWorkflowCommand;
import top.continew.admin.workflow.command.TransferTaskCommand;
import top.continew.admin.workflow.command.UnclaimTaskCommand;
import top.continew.admin.workflow.dto.WorkflowPage;
import top.continew.admin.workflow.dto.WorkflowProcessHistory;
import top.continew.admin.workflow.dto.WorkflowRef;
import top.continew.admin.workflow.dto.WorkflowTask;
import top.continew.admin.workflow.query.WorkflowDoneQuery;
import top.continew.admin.workflow.query.WorkflowTaskQuery;

/** Engine-neutral process and task port used by application/domain modules. */
public interface WorkflowService {

    WorkflowRef start(StartWorkflowCommand command);

    void claim(ClaimTaskCommand command);

    void unclaim(UnclaimTaskCommand command);

    void complete(CompleteTaskCommand command);

    void transfer(TransferTaskCommand command);

    WorkflowTask task(Long tenantId, Long userId, String taskId);

    WorkflowTask taskView(Long tenantId, Long userId, String taskId);

    WorkflowPage<WorkflowTask> pageTodo(WorkflowTaskQuery query);

    WorkflowPage<WorkflowTask> pageDone(WorkflowDoneQuery query);

    WorkflowProcessHistory history(Long tenantId, Long userId, String processInstanceId);
}
