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

package top.continew.admin.workflow.definition;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import top.continew.admin.workflow.command.DeployWorkflowCommand;
import top.continew.admin.workflow.dto.WorkflowDefinitionContract;
import top.continew.admin.workflow.dto.WorkflowNodeContract;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** Reviewed merchant-limit BPMN bytes and their stable node contract. */
@Component
public class MerchantLimitAdjustmentWorkflowDefinition {

    public static final String PROCESS_KEY = "merchant-limit-adjustment-v1";
    public static final int CONTRACT_VERSION = 1;
    public static final String RESOURCE_NAME = PROCESS_KEY + ".bpmn20.xml";
    private static final String RESOURCE_PATH = "workflow-definitions/" + RESOURCE_NAME;
    private static final WorkflowDefinitionContract CONTRACT = new WorkflowDefinitionContract(PROCESS_KEY, CONTRACT_VERSION, List
        .of(node("start", WorkflowNodeContract.NodeType.START_EVENT), node("limitReviewTask", WorkflowNodeContract.NodeType.USER_TASK), node("reviewDecisionGateway", WorkflowNodeContract.NodeType.EXCLUSIVE_GATEWAY), node("channelSubmitTask", WorkflowNodeContract.NodeType.USER_TASK), node("channelResultGateway", WorkflowNodeContract.NodeType.EXCLUSIVE_GATEWAY), node("channelQueryTask", WorkflowNodeContract.NodeType.USER_TASK), node("rejectedEnd", WorkflowNodeContract.NodeType.END_EVENT), node("effectiveEnd", WorkflowNodeContract.NodeType.END_EVENT), node("failedEnd", WorkflowNodeContract.NodeType.END_EVENT)));

    private final byte[] resourceBytes = loadResource();

    public WorkflowDefinitionContract contract() {
        return CONTRACT;
    }

    public DeployWorkflowCommand deploymentCommand(Long tenantId, Long actorUserId) {
        return new DeployWorkflowCommand(tenantId, actorUserId, "Merchant limit adjustment contract v1", RESOURCE_NAME, resourceBytes, CONTRACT);
    }

    private static WorkflowNodeContract node(String nodeId, WorkflowNodeContract.NodeType nodeType) {
        return new WorkflowNodeContract(nodeId, nodeType);
    }

    private byte[] loadResource() {
        ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);
        try (InputStream inputStream = resource.getInputStream()) {
            return inputStream.readAllBytes();
        } catch (IOException ex) {
            throw new IllegalStateException("Reviewed merchant limit adjustment BPMN resource is unavailable", ex);
        }
    }
}