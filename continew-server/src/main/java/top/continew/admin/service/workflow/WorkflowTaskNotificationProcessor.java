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

package top.continew.admin.service.workflow;

import lombok.RequiredArgsConstructor;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.api.Task;
import org.flowable.engine.TaskService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import top.continew.admin.merchant.review.application.WorkflowNotificationDraft;
import top.continew.admin.workflow.api.WorkflowActor;
import top.continew.admin.workflow.api.WorkflowAuthorizationPort;
import top.continew.admin.workflow.api.WorkflowMappingService;
import top.continew.admin.workflow.api.WorkflowOperationException;
import top.continew.admin.workflow.dto.WorkflowInstanceMapping;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Scans active Flowable tasks and idempotently queues assignment and overdue ContiNew messages. */
@Component
@RequiredArgsConstructor
public class WorkflowTaskNotificationProcessor {

    private static final String BUSINESS_TYPE = "MERCHANT_ONBOARDING";

    private final TaskService taskService;
    private final WorkflowMappingService mappingService;
    private final WorkflowAuthorizationPort authorizationPort;
    private final WorkflowNotificationQueue queue;
    private final WorkflowNotificationDispatcher dispatcher;
    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public WorkflowNotificationBatchResult process(int scanLimit, int sendLimit) {
        if (scanLimit < 1 || scanLimit > 500) {
            throw new IllegalArgumentException("Invalid workflow task notification scan limit");
        }
        List<Task> tasks = taskService.createTaskQuery().active().orderByTaskCreateTime().asc().listPage(0, scanLimit);
        int enqueued = 0;
        for (Task task : tasks) {
            enqueued += enqueue(task);
        }
        return new WorkflowNotificationBatchResult(tasks.size(), enqueued, dispatcher.dispatchPending(sendLimit));
    }

    private int enqueue(Task task) {
        Long tenantId = tenantId(task.getTenantId());
        if (tenantId == null) {
            return 0;
        }
        WorkflowInstanceMapping mapping = mappingService.findByProcessInstanceId(tenantId, task.getProcessInstanceId())
            .orElse(null);
        if (mapping == null || !BUSINESS_TYPE.equals(mapping.businessType())) {
            return 0;
        }
        ApplicationNotificationContext context = context(tenantId, mapping.businessId());
        if (context == null) {
            return 0;
        }
        boolean overdue = "escalatedReviewTask".equals(task.getTaskDefinitionKey()) || task.getDueDate() != null && task
            .getDueDate()
            .toInstant()
            .isBefore(java.time.Instant.now());
        int count = 0;
        for (Long recipient : recipients(tenantId, task, mapping, context.submittedBy())) {
            String eventType = overdue ? "TASK_OVERDUE" : "TASK_ASSIGNED";
            String key = "WORKFLOW_%s:%s:%s".formatted(eventType, task.getId(), recipient);
            String title = overdue ? "审核任务已逾期升级" : "您有新的审核任务";
            String content = "进件 %s 的任务“%s”等待处理。".formatted(context.applicationNo(), task.getName());
            String tab = task.getAssignee() == null ? "todo" : "claimed";
            if (queue.enqueueIfAbsent(new WorkflowNotificationDraft(tenantId, key, eventType, recipient, task
                .getProcessInstanceId(), task.getId(), title, content, taskPath(tab, task.getId())))) {
                count++;
            }
        }
        return count;
    }

    private Set<Long> recipients(Long tenantId, Task task, WorkflowInstanceMapping mapping, Long submittedBy) {
        Set<Long> recipients = new LinkedHashSet<>();
        if (task.getAssignee() != null) {
            try {
                recipients.add(Long.valueOf(task.getAssignee()));
            } catch (NumberFormatException ignored) {
                return Set.of();
            }
        } else {
            Set<String> groups = taskService.getIdentityLinksForTask(task.getId())
                .stream()
                .filter(link -> "candidate".equals(link.getType()))
                .map(IdentityLink::getGroupId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            if (!groups.isEmpty()) {
                recipients.addAll(candidateUsers(tenantId, groups));
            }
        }
        if (Set.of("reviewTask", "escalatedReviewTask").contains(task.getTaskDefinitionKey())) {
            recipients.remove(submittedBy);
        }
        recipients.removeIf(userId -> !canAccess(tenantId, userId, mapping));
        return recipients;
    }

    private List<Long> candidateUsers(Long tenantId, Set<String> groups) {
        return namedParameterJdbcTemplate.queryForList("""
            SELECT DISTINCT u.id
            FROM sys_user u
            JOIN sys_user_role ur ON ur.tenant_id = u.tenant_id AND ur.user_id = u.id
            JOIN sys_role r ON r.tenant_id = ur.tenant_id AND r.id = ur.role_id AND r.deleted = 0
            WHERE u.tenant_id = :tenantId AND u.status = 1 AND u.deleted = 0 AND r.code IN (:groups)
            ORDER BY u.id
            """, new MapSqlParameterSource("tenantId", tenantId).addValue("groups", groups), Long.class);
    }

    private boolean canAccess(Long tenantId, Long userId, WorkflowInstanceMapping mapping) {
        try {
            WorkflowActor actor = authorizationPort.requireActor(tenantId, userId);
            return authorizationPort.canAccessBusiness(actor, mapping.businessType(), mapping.businessId());
        } catch (WorkflowOperationException ex) {
            return false;
        }
    }

    private ApplicationNotificationContext context(Long tenantId, Long applicationId) {
        List<ApplicationNotificationContext> values = jdbcTemplate.query("""
            SELECT application_no, submitted_by
            FROM biz_onboarding_application
            WHERE tenant_id = ? AND id = ? AND deleted = 0
            """, (resultSet, rowNumber) -> new ApplicationNotificationContext(resultSet
            .getString("application_no"), resultSet.getLong("submitted_by")), tenantId, applicationId);
        return values.size() == 1 ? values.get(0) : null;
    }

    private Long tenantId(String value) {
        try {
            long tenantId = Long.parseLong(value);
            return tenantId > 0 ? tenantId : null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String taskPath(String tab, String taskId) {
        return "/merchant/workflow?tab=%s&taskId=%s".formatted(tab, taskId);
    }

    private record ApplicationNotificationContext(String applicationNo, Long submittedBy) {
    }
}
