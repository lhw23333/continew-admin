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

import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.api.lock.LockManager;
import org.flowable.engine.HistoryService;
import org.flowable.engine.ManagementService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;
import org.springframework.stereotype.Service;
import top.continew.admin.workflow.api.WorkflowOperationException;
import top.continew.admin.workflow.api.WorkflowActor;
import top.continew.admin.workflow.api.WorkflowAuthorizationPort;
import top.continew.admin.workflow.api.WorkflowMappingService;
import top.continew.admin.workflow.api.WorkflowService;
import top.continew.admin.workflow.api.WorkflowVariablePolicy;
import top.continew.admin.workflow.command.ClaimTaskCommand;
import top.continew.admin.workflow.command.CompleteTaskCommand;
import top.continew.admin.workflow.command.StartWorkflowCommand;
import top.continew.admin.workflow.command.UnclaimTaskCommand;
import top.continew.admin.workflow.dto.WorkflowActivityHistory;
import top.continew.admin.workflow.dto.WorkflowInstanceMapping;
import top.continew.admin.workflow.dto.WorkflowPage;
import top.continew.admin.workflow.dto.WorkflowProcessHistory;
import top.continew.admin.workflow.dto.WorkflowRef;
import top.continew.admin.workflow.dto.WorkflowTask;
import top.continew.admin.workflow.query.WorkflowDoneQuery;
import top.continew.admin.workflow.query.WorkflowTaskQuery;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Flowable implementation hidden behind the project-owned workflow port. */
@Service
public class FlowableWorkflowService implements WorkflowService {

    private static final Pattern PROCESS_KEY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");
    private static final Pattern BUSINESS_KEY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9:._-]{0,254}");
    private static final Pattern ENGINE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9:._-]{0,127}");
    private static final Pattern GROUP_CODE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9:._-]{0,63}");
    private static final ZoneId DATABASE_ZONE = ZoneId.systemDefault();

    private final TaskService taskService;
    private final HistoryService historyService;
    private final RepositoryService repositoryService;
    private final ManagementService managementService;
    private final WorkflowVariablePolicy variablePolicy;
    private final FlowableWorkflowStartTransaction startTransaction;
    private final WorkflowAuthorizationPort authorizationPort;
    private final WorkflowMappingService mappingService;

    public FlowableWorkflowService(TaskService taskService,
                                   HistoryService historyService,
                                   RepositoryService repositoryService,
                                   ManagementService managementService,
                                   WorkflowVariablePolicy variablePolicy,
                                   FlowableWorkflowStartTransaction startTransaction,
                                   WorkflowAuthorizationPort authorizationPort,
                                   WorkflowMappingService mappingService) {
        this.taskService = taskService;
        this.historyService = historyService;
        this.repositoryService = repositoryService;
        this.managementService = managementService;
        this.variablePolicy = variablePolicy;
        this.startTransaction = startTransaction;
        this.authorizationPort = authorizationPort;
        this.mappingService = mappingService;
    }

    @Override
    public WorkflowRef start(StartWorkflowCommand command) {
        Long tenantId = positive(command.tenantId());
        String processKey = required(command.processDefinitionKey(), PROCESS_KEY);
        WorkflowBusinessKey businessKey = WorkflowBusinessKey.parse(tenantId, command.businessKey());
        Map<String, Object> variables = variablePolicy.validateAndCopy(command.variables());
        if (variables.containsKey("tenantId") && !tenantId.equals(variables.get("tenantId"))) {
            throw new WorkflowOperationException(WorkflowOperationException.Code.INVALID_REQUEST);
        }
        Object applicantValue = variables.get("applicantId");
        if (!(applicantValue instanceof Long applicantId)) {
            throw new WorkflowOperationException(WorkflowOperationException.Code.INVALID_REQUEST);
        }
        WorkflowActor actor = authorizationPort.requireActor(tenantId, applicantId);
        authorizationPort.requireBusinessAccess(actor, businessKey.businessType(), businessKey.businessId());
        LockManager lockManager = managementService.getLockManager(lockName(businessKey));
        try {
            return lockManager.waitForLockRunAndRelease(Duration.ofSeconds(30), () -> startTransaction
                .startOrExisting(businessKey, processKey, variables));
        } catch (WorkflowOperationException ex) {
            throw ex;
        } catch (FlowableException ex) {
            throw new WorkflowOperationException(WorkflowOperationException.Code.ENGINE_FAILURE);
        }
    }

    @Override
    public void claim(ClaimTaskCommand command) {
        WorkflowActor actor = authorizationPort.requireActor(command.tenantId(), command.userId());
        String userId = actor.flowableUserId();
        Task task = requireTask(command.tenantId(), command.taskId());
        requireTaskAccess(actor, task);
        if (task.getAssignee() != null) {
            if (userId.equals(task.getAssignee())) {
                return;
            }
            throw new WorkflowOperationException(WorkflowOperationException.Code.ALREADY_CLAIMED);
        }
        if (!isCandidate(actor, task)) {
            throw new WorkflowOperationException(WorkflowOperationException.Code.NOT_FOUND);
        }
        try {
            taskService.claim(task.getId(), userId);
        } catch (FlowableException ex) {
            throw new WorkflowOperationException(WorkflowOperationException.Code.ALREADY_CLAIMED);
        }
    }

    @Override
    public void unclaim(UnclaimTaskCommand command) {
        WorkflowActor actor = authorizationPort.requireActor(command.tenantId(), command.userId());
        String userId = actor.flowableUserId();
        Task task = requireTask(command.tenantId(), command.taskId());
        requireTaskAccess(actor, task);
        if (task.getAssignee() == null) {
            return;
        }
        if (!userId.equals(task.getAssignee())) {
            throw new WorkflowOperationException(WorkflowOperationException.Code.NOT_ASSIGNED);
        }
        try {
            taskService.unclaim(task.getId());
        } catch (FlowableException ex) {
            throw new WorkflowOperationException(WorkflowOperationException.Code.ENGINE_FAILURE);
        }
    }

    @Override
    public void complete(CompleteTaskCommand command) {
        WorkflowActor actor = authorizationPort.requireActor(command.tenantId(), command.userId());
        String userId = actor.flowableUserId();
        Task task = requireTask(command.tenantId(), command.taskId());
        requireTaskAccess(actor, task);
        if (!userId.equals(task.getAssignee())) {
            throw new WorkflowOperationException(WorkflowOperationException.Code.NOT_ASSIGNED);
        }
        Map<String, Object> variables = variablePolicy.validateAndCopy(command.variables());
        try {
            taskService.complete(task.getId(), userId, variables);
        } catch (FlowableException ex) {
            throw new WorkflowOperationException(WorkflowOperationException.Code.ENGINE_FAILURE);
        }
    }

    @Override
    public WorkflowPage<WorkflowTask> pageTodo(WorkflowTaskQuery request) {
        WorkflowActor actor = authorizationPort.requireActor(request.tenantId(), request.userId());
        QueryContext context = queryContext(actor, request.page(), request.size());
        Set<String> groups = groups(actor.roleCodes());
        TaskQuery query = taskService.createTaskQuery().taskTenantId(context.tenantId()).active();
        if (request.claimedOnly()) {
            query.taskAssignee(context.userId());
        } else {
            query.or().taskAssignee(context.userId()).taskCandidateUser(context.userId());
            if (!groups.isEmpty()) {
                query.taskCandidateGroupIn(groups);
            }
            query.endOr();
        }
        apply(query, request.processDefinitionKey(), request.businessKey(), request.taskName());
        List<WorkflowTask> authorized = taskViews(query.orderByTaskCreateTime()
            .desc()
            .list()
            .stream()
            .filter(task -> canAccessTask(actor, task.getProcessInstanceId()))
            .toList());
        return page(authorized, context);
    }

    @Override
    public WorkflowPage<WorkflowTask> pageDone(WorkflowDoneQuery request) {
        WorkflowActor actor = authorizationPort.requireActor(request.tenantId(), request.userId());
        QueryContext context = queryContext(actor, request.page(), request.size());
        HistoricTaskInstanceQuery query = historyService.createHistoricTaskInstanceQuery()
            .taskTenantId(context.tenantId())
            .finished()
            .taskCompletedBy(context.userId());
        apply(query, request.processDefinitionKey(), request.businessKey(), request.taskName());
        List<WorkflowTask> authorized = historicTaskViews(query.orderByHistoricTaskInstanceEndTime()
            .desc()
            .list()
            .stream()
            .filter(task -> canAccessTask(actor, task.getProcessInstanceId()))
            .toList());
        return page(authorized, context);
    }

    @Override
    public WorkflowProcessHistory history(Long tenantId, Long userId, String processInstanceId) {
        WorkflowActor actor = authorizationPort.requireActor(tenantId, userId);
        String tenant = tenant(actor.tenantId());
        String instanceId = required(processInstanceId, ENGINE_ID);
        HistoricProcessInstance process = historyService.createHistoricProcessInstanceQuery()
            .processInstanceId(instanceId)
            .singleResult();
        if (process == null || !tenant.equals(process.getTenantId())) {
            throw new WorkflowOperationException(WorkflowOperationException.Code.NOT_FOUND);
        }
        requireProcessAccess(actor, instanceId);
        ProcessDefinition definition = requireDefinition(process.getProcessDefinitionId());
        List<WorkflowActivityHistory> activities = historyService.createHistoricActivityInstanceQuery()
            .processInstanceId(instanceId)
            .orderByHistoricActivityInstanceStartTime()
            .asc()
            .list()
            .stream()
            .map(this::activityView)
            .toList();
        List<WorkflowTask> tasks = historicTaskViews(historyService.createHistoricTaskInstanceQuery()
            .processInstanceId(instanceId)
            .orderByHistoricTaskInstanceStartTime()
            .asc()
            .list());
        return new WorkflowProcessHistory(instanceId, process.getBusinessKey(), definition.getId(), definition
            .getKey(), definition.getVersion(), tenant, time(process.getStartTime()), time(process
                .getEndTime()), process.getEndTime() != null, activities, tasks);
    }

    private Task requireTask(Long tenantId, String taskId) {
        String tenant = tenant(positive(tenantId));
        String id = required(taskId, ENGINE_ID);
        Task task = taskService.createTaskQuery().taskId(id).taskTenantId(tenant).singleResult();
        if (task == null) {
            throw new WorkflowOperationException(WorkflowOperationException.Code.NOT_FOUND);
        }
        return task;
    }

    private void requireTaskAccess(WorkflowActor actor, Task task) {
        requireProcessAccess(actor, task.getProcessInstanceId());
    }

    private void requireProcessAccess(WorkflowActor actor, String processInstanceId) {
        WorkflowInstanceMapping mapping = mappingService.findByProcessInstanceId(actor.tenantId(), processInstanceId)
            .orElseThrow(() -> new WorkflowOperationException(WorkflowOperationException.Code.NOT_FOUND));
        authorizationPort.requireBusinessAccess(actor, mapping.businessType(), mapping.businessId());
    }

    private boolean canAccessTask(WorkflowActor actor, String processInstanceId) {
        WorkflowInstanceMapping mapping = mappingService.findByProcessInstanceId(actor.tenantId(), processInstanceId)
            .orElse(null);
        return mapping != null && authorizationPort.canAccessBusiness(actor, mapping.businessType(), mapping
            .businessId());
    }

    private boolean isCandidate(WorkflowActor actor, Task task) {
        return taskService.getIdentityLinksForTask(task.getId())
            .stream()
            .anyMatch(link -> actor.flowableUserId().equals(link.getUserId()) || link.getGroupId() != null && actor
                .roleCodes()
                .contains(link.getGroupId()));
    }

    private List<WorkflowTask> taskViews(List<Task> tasks) {
        Map<String, ProcessMetadata> metadata = new HashMap<>();
        return tasks.stream()
            .map(task -> taskView(task, processMetadata(task.getProcessInstanceId(), task
                .getProcessDefinitionId(), metadata)))
            .toList();
    }

    private List<WorkflowTask> historicTaskViews(List<HistoricTaskInstance> tasks) {
        Map<String, ProcessMetadata> metadata = new HashMap<>();
        return tasks.stream()
            .map(task -> historicTaskView(task, processMetadata(task.getProcessInstanceId(), task
                .getProcessDefinitionId(), metadata)))
            .toList();
    }

    private WorkflowTask taskView(Task task, ProcessMetadata process) {
        WorkflowTask.State state = task.getAssignee() == null ? WorkflowTask.State.TODO : WorkflowTask.State.CLAIMED;
        return new WorkflowTask(task.getId(), task.getTaskDefinitionKey(), task.getName(), task
            .getProcessInstanceId(), task.getProcessDefinitionId(), process.definitionKey(), process
                .definitionVersion(), process.businessKey(), task.getTenantId(), task.getAssignee(), state, time(task
                    .getCreateTime()), time(task.getClaimTime()), time(task.getDueDate()), null);
    }

    private WorkflowTask historicTaskView(HistoricTaskInstance task, ProcessMetadata process) {
        WorkflowTask.State state = task.getEndTime() != null
            ? WorkflowTask.State.DONE
            : task.getAssignee() == null ? WorkflowTask.State.TODO : WorkflowTask.State.CLAIMED;
        return new WorkflowTask(task.getId(), task.getTaskDefinitionKey(), task.getName(), task
            .getProcessInstanceId(), task.getProcessDefinitionId(), process.definitionKey(), process
                .definitionVersion(), process.businessKey(), task.getTenantId(), task.getAssignee(), state, time(task
                    .getCreateTime()), time(task.getClaimTime()), time(task.getDueDate()), time(task.getEndTime()));
    }

    private WorkflowActivityHistory activityView(HistoricActivityInstance activity) {
        return new WorkflowActivityHistory(activity.getActivityId(), activity.getActivityName(), activity
            .getActivityType(), activity.getAssignee(), time(activity.getStartTime()), time(activity
                .getEndTime()), activity.getDurationInMillis());
    }

    private ProcessMetadata processMetadata(String processInstanceId,
                                            String processDefinitionId,
                                            Map<String, ProcessMetadata> cache) {
        return cache.computeIfAbsent(processInstanceId, ignored -> {
            HistoricProcessInstance process = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
            ProcessDefinition definition = requireDefinition(processDefinitionId);
            return new ProcessMetadata(process == null ? null : process.getBusinessKey(), definition
                .getKey(), definition.getVersion());
        });
    }

    private ProcessDefinition requireDefinition(String processDefinitionId) {
        ProcessDefinition definition = repositoryService.getProcessDefinition(processDefinitionId);
        if (definition == null) {
            throw new WorkflowOperationException(WorkflowOperationException.Code.NOT_FOUND);
        }
        return definition;
    }

    private void apply(TaskQuery query, String processDefinitionKey, String businessKey, String taskName) {
        if (text(processDefinitionKey)) {
            query.processDefinitionKey(required(processDefinitionKey, PROCESS_KEY));
        }
        if (text(businessKey)) {
            query.processInstanceBusinessKey(required(businessKey, BUSINESS_KEY));
        }
        if (text(taskName)) {
            query.taskNameLikeIgnoreCase("%" + filter(taskName, 100) + "%");
        }
    }

    private void apply(HistoricTaskInstanceQuery query,
                       String processDefinitionKey,
                       String businessKey,
                       String taskName) {
        if (text(processDefinitionKey)) {
            query.processDefinitionKey(required(processDefinitionKey, PROCESS_KEY));
        }
        if (text(businessKey)) {
            query.processInstanceBusinessKey(required(businessKey, BUSINESS_KEY));
        }
        if (text(taskName)) {
            query.taskNameLikeIgnoreCase("%" + filter(taskName, 100) + "%");
        }
    }

    private QueryContext queryContext(WorkflowActor actor, int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw new WorkflowOperationException(WorkflowOperationException.Code.INVALID_REQUEST);
        }
        return new QueryContext(tenant(actor.tenantId()), actor.flowableUserId(), page, size, (page - 1) * size);
    }

    private WorkflowPage<WorkflowTask> page(List<WorkflowTask> authorized, QueryContext context) {
        int fromIndex = Math.min(context.offset(), authorized.size());
        int toIndex = Math.min(fromIndex + context.size(), authorized.size());
        return new WorkflowPage<>(authorized.subList(fromIndex, toIndex), authorized.size(), context.page(), context
            .size());
    }

    private Set<String> groups(Set<String> values) {
        if (values == null || values.size() > 50) {
            throw new WorkflowOperationException(WorkflowOperationException.Code.INVALID_REQUEST);
        }
        return values.stream()
            .map(value -> required(value, GROUP_CODE))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private Long positive(Long value) {
        if (value == null || value <= 0) {
            throw new WorkflowOperationException(WorkflowOperationException.Code.INVALID_REQUEST);
        }
        return value;
    }

    private String tenant(Long tenantId) {
        return String.valueOf(tenantId);
    }

    private String required(String value, Pattern pattern) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || !pattern.matcher(normalized).matches()) {
            throw new WorkflowOperationException(WorkflowOperationException.Code.INVALID_REQUEST);
        }
        return normalized;
    }

    private String filter(String value, int maxLength) {
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > maxLength || normalized.chars()
            .anyMatch(Character::isISOControl)) {
            throw new WorkflowOperationException(WorkflowOperationException.Code.INVALID_REQUEST);
        }
        return normalized;
    }

    private boolean text(String value) {
        return value != null && !value.isBlank();
    }

    private String lockName(WorkflowBusinessKey businessKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest((businessKey.tenantId() + ":" + businessKey.value()).getBytes(StandardCharsets.UTF_8));
            return "WF_START_" + (Byte.toUnsignedInt(digest[0]) & 63);
        } catch (NoSuchAlgorithmException ex) {
            throw new WorkflowOperationException(WorkflowOperationException.Code.ENGINE_FAILURE);
        }
    }

    private LocalDateTime time(Date value) {
        return value == null ? null : LocalDateTime.ofInstant(value.toInstant(), DATABASE_ZONE);
    }

    private record QueryContext(String tenantId, String userId, int page, int size, int offset) {
    }

    private record ProcessMetadata(String businessKey, String definitionKey, Integer definitionVersion) {
    }
}
