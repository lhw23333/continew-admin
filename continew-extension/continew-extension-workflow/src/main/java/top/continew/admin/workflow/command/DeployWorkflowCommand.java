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

package top.continew.admin.workflow.command;

import top.continew.admin.workflow.dto.WorkflowDefinitionContract;

import java.util.Arrays;

/** Trusted internal command for validated BPMN deployment. */
public record DeployWorkflowCommand(Long tenantId, Long actorUserId, String deploymentName, String resourceName,
                                    byte[] resourceBytes, WorkflowDefinitionContract contract) {
    public DeployWorkflowCommand {
        resourceBytes = resourceBytes == null ? null : Arrays.copyOf(resourceBytes, resourceBytes.length);
    }

    @Override
    public byte[] resourceBytes() {
        return resourceBytes == null ? null : Arrays.copyOf(resourceBytes, resourceBytes.length);
    }
}
