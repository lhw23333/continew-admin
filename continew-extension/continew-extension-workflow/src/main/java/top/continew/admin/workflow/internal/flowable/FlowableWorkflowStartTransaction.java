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

import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.workflow.api.WorkflowOperationException;
import top.continew.admin.workflow.dto.WorkflowInstanceMapping;
import top.continew.admin.workflow.dto.WorkflowRef;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;

/** Commits process start and durable mapping before the cross-node start lock is released. */
@Component
public class FlowableWorkflowStartTransaction {

    private final RuntimeService runtimeService;
    private final RepositoryService repositoryService;
    private final JdbcWorkflowMappingRepository mappingRepository;
    private final Clock clock = Clock.systemDefaultZone();

    public FlowableWorkflowStartTransaction(RuntimeService runtimeService,
                                            RepositoryService repositoryService,
                                            JdbcWorkflowMappingRepository mappingRepository) {
        this.runtimeService = runtimeService;
        this.repositoryService = repositoryService;
        this.mappingRepository = mappingRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public WorkflowRef startOrExisting(WorkflowBusinessKey businessKey,
                                       String processDefinitionKey,
                                       Map<String, Object> variables) {
        WorkflowInstanceMapping existing = mappingRepository.findByBusinessKeyForUpdate(businessKey
            .tenantId(), businessKey.value()).orElse(null);
        if (existing != null) {
            if (!processDefinitionKey.equals(existing.processDefinitionKey())) {
                throw new WorkflowOperationException(WorkflowOperationException.Code.MAPPING_CONFLICT);
            }
            return existing.toRef();
        }
        ProcessInstance instance = runtimeService.startProcessInstanceByKeyAndTenantId(processDefinitionKey, businessKey
            .value(), variables, String.valueOf(businessKey.tenantId()));
        ProcessDefinition definition = repositoryService.getProcessDefinition(instance.getProcessDefinitionId());
        if (definition == null) {
            throw new WorkflowOperationException(WorkflowOperationException.Code.NOT_FOUND);
        }
        WorkflowInstanceMapping mapping = mappingRepository.insert(mappingId(instance
            .getProcessInstanceId()), businessKey, definition.getId(), definition.getKey(), definition
                .getVersion(), instance.getProcessInstanceId(), LocalDateTime.now(clock));
        return mapping.toRef();
    }

    private Long mappingId(String processInstanceId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(processInstanceId.getBytes(StandardCharsets.UTF_8));
            long value = ByteBuffer.wrap(digest).getLong() & Long.MAX_VALUE;
            return value == 0 ? 1L : value;
        } catch (NoSuchAlgorithmException ex) {
            throw new WorkflowOperationException(WorkflowOperationException.Code.ENGINE_FAILURE);
        }
    }
}
