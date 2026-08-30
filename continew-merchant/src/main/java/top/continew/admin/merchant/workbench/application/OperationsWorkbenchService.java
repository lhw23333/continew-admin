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

package top.continew.admin.merchant.workbench.application;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import top.continew.admin.merchant.agent.application.AgentScopeAuthorizationService;
import top.continew.admin.merchant.agent.domain.AgentAccessDeniedException;
import top.continew.admin.merchant.master.domain.MerchantAccessDeniedException;
import top.continew.starter.extension.tenant.context.TenantContextHolder;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Aggregates tenant and merchant-scope-safe phase-one operational metrics. */
@Service
@RequiredArgsConstructor
public class OperationsWorkbenchService {

    public static final String BUSINESS_TIMEZONE = "Asia/Shanghai";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of(BUSINESS_TIMEZONE);

    private final AgentScopeAuthorizationService agentScopeAuthorizationService;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock = Clock.system(BUSINESS_ZONE);

    public OperationsWorkbenchMetrics metrics(Long tenantId, Long actorUserId) {
        requireTenantContext(tenantId);
        OffsetDateTime asOfTime = OffsetDateTime.now(clock);
        Scope scope = scope(tenantId, actorUserId);
        Map<String, Object> applications = jdbcTemplate.queryForMap(applicationSql(scope), applicationArgs(tenantId, actorUserId, scope));
        Map<String, Object> tasks = jdbcTemplate.queryForMap(taskSql(scope), taskArgs(tenantId, actorUserId, asOfTime
            .toLocalDateTime(), scope));
        return new OperationsWorkbenchMetrics(number(applications, "drafts"), number(applications, "submitted"), number(tasks, "pending_reviews"), number(tasks, "supplement_tasks"), number(applications, "channel_processing"), number(applications, "succeeded"), number(applications, "failed"), number(tasks, "overdue_tasks"), BUSINESS_TIMEZONE, asOfTime);
    }

    private Scope scope(Long tenantId, Long actorUserId) {
        try {
            return new Scope(agentScopeAuthorizationService.listAuthorizedAgentIds(tenantId, actorUserId));
        } catch (AgentAccessDeniedException ex) {
            return new Scope(List.of());
        }
    }

    private String applicationSql(Scope scope) {
        return """
            SELECT
              COALESCE(SUM(CASE WHEN a.status = 'DRAFT' THEN 1 ELSE 0 END), 0) AS drafts,
              COALESCE(SUM(CASE WHEN a.status = 'SUBMITTED' THEN 1 ELSE 0 END), 0) AS submitted,
              COALESCE(SUM(CASE WHEN a.status = 'CHANNEL_PROCESSING' THEN 1 ELSE 0 END), 0) AS channel_processing,
              COALESCE(SUM(CASE WHEN a.status = 'SUCCEEDED' THEN 1 ELSE 0 END), 0) AS succeeded,
              COALESCE(SUM(CASE WHEN a.status IN ('FAILED', 'REJECTED') THEN 1 ELSE 0 END), 0) AS failed
            FROM biz_onboarding_application a
            JOIN biz_merchant m ON m.id = a.merchant_id AND m.tenant_id = a.tenant_id AND m.deleted = 0
            WHERE a.tenant_id = ? AND a.deleted = 0 AND %s
            """.formatted(scope.predicate());
    }

    private String taskSql(Scope scope) {
        return """
            SELECT
              COALESCE(SUM(CASE WHEN t.TASK_DEF_KEY_ IN ('reviewTask', 'escalatedReviewTask') THEN 1 ELSE 0 END), 0) AS pending_reviews,
              COALESCE(SUM(CASE WHEN t.TASK_DEF_KEY_ = 'supplementTask' THEN 1 ELSE 0 END), 0) AS supplement_tasks,
              COALESCE(SUM(CASE WHEN t.DUE_DATE_ IS NOT NULL AND t.DUE_DATE_ < ? THEN 1 ELSE 0 END), 0) AS overdue_tasks
            FROM ACT_RU_TASK t
            JOIN biz_workflow_instance w ON w.process_instance_id = t.PROC_INST_ID_
              AND w.business_type = 'MERCHANT_ONBOARDING' AND w.workflow_status = 'RUNNING' AND w.deleted = 0
            JOIN biz_onboarding_application a ON a.id = w.business_id AND a.tenant_id = w.tenant_id AND a.deleted = 0
            JOIN biz_merchant m ON m.id = a.merchant_id AND m.tenant_id = a.tenant_id AND m.deleted = 0
            WHERE w.tenant_id = ? AND %s
            """.formatted(scope.predicate());
    }

    private Object[] applicationArgs(Long tenantId, Long actorUserId, Scope scope) {
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.addAll(scope.agentIds());
        args.add(actorUserId);
        args.add(actorUserId);
        return args.toArray();
    }

    private Object[] taskArgs(Long tenantId, Long actorUserId, LocalDateTime asOfTime, Scope scope) {
        List<Object> args = new ArrayList<>();
        args.add(asOfTime);
        args.add(tenantId);
        args.addAll(scope.agentIds());
        args.add(actorUserId);
        args.add(actorUserId);
        return args.toArray();
    }

    private long number(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) {
            value = row.get(key.toUpperCase(java.util.Locale.ROOT));
        }
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private void requireTenantContext(Long tenantId) {
        if (tenantId == null || tenantId <= 0 || !tenantId.equals(TenantContextHolder.getTenantId())) {
            throw new MerchantAccessDeniedException();
        }
    }

    private record Scope(List<Long> agentIds) {
        private Scope {
            agentIds = List.copyOf(agentIds);
        }

        private String predicate() {
            String agentPredicate = agentIds.isEmpty()
                ? "1 = 0"
                : "m.owning_agent_id IN (" + String.join(",", java.util.Collections
                    .nCopies(agentIds.size(), "?")) + ")";
            return "(" + agentPredicate + " OR m.operator_user_id = ? OR m.reviewer_user_id = ?)";
        }
    }
}
