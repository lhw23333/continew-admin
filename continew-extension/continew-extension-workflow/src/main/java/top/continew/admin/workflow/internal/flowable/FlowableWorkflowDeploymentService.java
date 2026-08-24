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

package top.continew.admin.workflow.internal.flowable;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.bpmn.model.StartEvent;
import org.flowable.bpmn.model.SubProcess;
import org.flowable.bpmn.model.UserTask;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.workflow.api.WorkflowDeploymentService;
import top.continew.admin.workflow.api.WorkflowOperationException;
import top.continew.admin.workflow.command.DeployWorkflowCommand;
import top.continew.admin.workflow.dto.WorkflowDefinitionContract;
import top.continew.admin.workflow.dto.WorkflowDeploymentRef;
import top.continew.admin.workflow.dto.WorkflowNodeContract;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Transactional Flowable deployment adapter with an immutable project-owned node contract. */
@Component
public class FlowableWorkflowDeploymentService implements WorkflowDeploymentService {

    private static final Pattern STABLE_KEY = Pattern.compile("[A-Za-z][A-Za-z0-9._-]{0,127}");
    private static final int MAX_RESOURCE_BYTES = 10 * 1024 * 1024;

    private final RepositoryService repositoryService;
    private final JdbcWorkflowDeploymentRepository deploymentRepository;
    private final Clock clock = Clock.systemDefaultZone();

    public FlowableWorkflowDeploymentService(RepositoryService repositoryService,
                                             JdbcWorkflowDeploymentRepository deploymentRepository) {
        this.repositoryService = repositoryService;
        this.deploymentRepository = deploymentRepository;
    }

    @Override
    @Transactional
    public WorkflowDeploymentRef deploy(DeployWorkflowCommand command) {
        ValidatedDeployment request = validate(command);
        WorkflowDeploymentRef sameResource = deploymentRepository.findByResourceHash(request.tenantId(), request
            .processDefinitionKey(), request.resourceSha256()).orElse(null);
        if (sameResource != null) {
            if (!sameResource.contractVersion().equals(request.contractVersion())) {
                throw deploymentConflict();
            }
            return sameResource;
        }
        WorkflowDeploymentRef sameContract = deploymentRepository.findByContractVersion(request.tenantId(), request
            .processDefinitionKey(), request.contractVersion()).orElse(null);
        if (sameContract != null) {
            throw deploymentConflict();
        }

        try {
            Deployment deployment = repositoryService.createDeployment()
                .tenantId(String.valueOf(request.tenantId()))
                .name(request.deploymentName())
                .addBytes(request.resourceName(), request.resourceBytes())
                .deploy();
            List<ProcessDefinition> definitions = repositoryService.createProcessDefinitionQuery()
                .deploymentId(deployment.getId())
                .list();
            if (definitions.size() != 1) {
                throw contractViolation();
            }
            ProcessDefinition definition = definitions.get(0);
            if (!request.processDefinitionKey().equals(definition.getKey()) || !String.valueOf(request.tenantId())
                .equals(definition.getTenantId())) {
                throw contractViolation();
            }
            verifyNodeContract(definition.getId(), request.requiredNodes());
            LocalDateTime deployedTime = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS);
            return deploymentRepository.insert(metadataId(request.tenantId(), definition.getId()), request
                .tenantId(), deployment.getId(), definition.getId(), definition.getKey(), definition
                    .getVersion(), request.contractVersion(), request.resourceName(), request.resourceSha256(), request
                        .actorUserId(), deployedTime);
        } catch (WorkflowOperationException ex) {
            throw ex;
        } catch (FlowableException ex) {
            throw new WorkflowOperationException(WorkflowOperationException.Code.DEFINITION_CONTRACT_VIOLATION);
        }
    }

    private ValidatedDeployment validate(DeployWorkflowCommand command) {
        if (command == null || command.tenantId() == null || command.tenantId() <= 0 || command
            .actorUserId() == null || command.actorUserId() <= 0 || blank(command.deploymentName()) || blank(command
                .resourceName()) || command.resourceBytes() == null || command.resourceBytes().length == 0 || command
                    .resourceBytes().length > MAX_RESOURCE_BYTES || command.contract() == null) {
            throw invalidRequest();
        }
        String deploymentName = command.deploymentName().trim();
        String resourceName = command.resourceName().trim();
        if (deploymentName.length() > 255 || resourceName.length() > 255 || !(resourceName
            .endsWith(".bpmn20.xml") || resourceName.endsWith(".bpmn"))) {
            throw invalidRequest();
        }
        WorkflowDefinitionContract contract = command.contract();
        String processDefinitionKey = contract.processDefinitionKey() == null
            ? null
            : contract.processDefinitionKey().trim();
        if (processDefinitionKey == null || !STABLE_KEY.matcher(processDefinitionKey).matches() || contract
            .contractVersion() == null || contract.contractVersion() <= 0 || contract
                .requiredNodes() == null || contract.requiredNodes().isEmpty()) {
            throw invalidRequest();
        }
        Set<String> nodeIds = new HashSet<>();
        for (WorkflowNodeContract node : contract.requiredNodes()) {
            if (node == null || blank(node.nodeId()) || node.nodeId().length() > 255 || node
                .nodeType() == null || !nodeIds.add(node.nodeId())) {
                throw invalidRequest();
            }
        }
        byte[] resourceBytes = command.resourceBytes();
        return new ValidatedDeployment(command.tenantId(), command
            .actorUserId(), deploymentName, resourceName, resourceBytes, processDefinitionKey, contract
                .contractVersion(), contract.requiredNodes(), sha256(resourceBytes));
    }

    private void verifyNodeContract(String processDefinitionId, List<WorkflowNodeContract> requiredNodes) {
        BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionId);
        if (bpmnModel == null || bpmnModel.getMainProcess() == null) {
            throw contractViolation();
        }
        Map<String, WorkflowNodeContract.NodeType> actualNodes = new HashMap<>();
        collectNodes(bpmnModel.getMainProcess().getFlowElements(), actualNodes);
        for (WorkflowNodeContract expected : requiredNodes) {
            if (actualNodes.get(expected.nodeId()) != expected.nodeType()) {
                throw contractViolation();
            }
        }
    }

    private void collectNodes(Iterable<FlowElement> elements, Map<String, WorkflowNodeContract.NodeType> nodes) {
        for (FlowElement element : elements) {
            if (element.getId() != null && nodes.put(element.getId(), nodeType(element)) != null) {
                throw contractViolation();
            }
            if (element instanceof SubProcess subProcess) {
                collectNodes(subProcess.getFlowElements(), nodes);
            }
        }
    }

    private WorkflowNodeContract.NodeType nodeType(FlowElement element) {
        if (element instanceof StartEvent) {
            return WorkflowNodeContract.NodeType.START_EVENT;
        }
        if (element instanceof EndEvent) {
            return WorkflowNodeContract.NodeType.END_EVENT;
        }
        if (element instanceof UserTask) {
            return WorkflowNodeContract.NodeType.USER_TASK;
        }
        if (element instanceof ExclusiveGateway) {
            return WorkflowNodeContract.NodeType.EXCLUSIVE_GATEWAY;
        }
        if (element instanceof ServiceTask) {
            return WorkflowNodeContract.NodeType.SERVICE_TASK;
        }
        return WorkflowNodeContract.NodeType.OTHER;
    }

    private Long metadataId(Long tenantId, String processDefinitionId) {
        byte[] digest = digest((tenantId + ":" + processDefinitionId).getBytes(StandardCharsets.UTF_8));
        long value = ByteBuffer.wrap(digest).getLong() & Long.MAX_VALUE;
        return value == 0 ? 1L : value;
    }

    private String sha256(byte[] resourceBytes) {
        return java.util.HexFormat.of().formatHex(digest(resourceBytes));
    }

    private byte[] digest(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private WorkflowOperationException invalidRequest() {
        return new WorkflowOperationException(WorkflowOperationException.Code.INVALID_REQUEST);
    }

    private WorkflowOperationException deploymentConflict() {
        return new WorkflowOperationException(WorkflowOperationException.Code.DEPLOYMENT_CONFLICT);
    }

    private WorkflowOperationException contractViolation() {
        return new WorkflowOperationException(WorkflowOperationException.Code.DEFINITION_CONTRACT_VIOLATION);
    }

    private record ValidatedDeployment(Long tenantId, Long actorUserId, String deploymentName, String resourceName,
                                       byte[] resourceBytes, String processDefinitionKey, Integer contractVersion,
                                       List<WorkflowNodeContract> requiredNodes, String resourceSha256) {
    }
}
