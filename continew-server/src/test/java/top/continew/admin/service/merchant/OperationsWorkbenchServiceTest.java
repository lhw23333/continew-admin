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

package top.continew.admin.service.merchant;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import top.continew.admin.merchant.agent.application.AgentScopeAuthorizationService;
import top.continew.admin.merchant.workbench.application.OperationsWorkbenchMetrics;
import top.continew.admin.merchant.workbench.application.OperationsWorkbenchService;
import top.continew.starter.extension.tenant.context.TenantContext;
import top.continew.starter.extension.tenant.context.TenantContextHolder;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OperationsWorkbenchServiceTest {

    @Test
    void aggregatesOnlyAuthorizedMerchantAndTaskScope() {
        DataSource dataSource = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).build();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        createSchema(jdbc);
        seed(jdbc);
        AgentScopeAuthorizationService authorization = mock(AgentScopeAuthorizationService.class);
        when(authorization.listAuthorizedAgentIds(11L, 101L)).thenReturn(List.of(201L));
        OperationsWorkbenchService service = new OperationsWorkbenchService(authorization, jdbc);

        TenantContext context = new TenantContext();
        context.setTenantId(11L);
        TenantContextHolder.setContext(context);
        OperationsWorkbenchMetrics metrics;
        try {
            metrics = service.metrics(11L, 101L);
        } finally {
            TenantContextHolder.clear();
        }

        assertEquals(1, metrics.drafts().value());
        assertEquals(1, metrics.submitted().value());
        assertEquals(2, metrics.pendingReviews().value());
        assertEquals(1, metrics.supplementTasks().value());
        assertEquals(1, metrics.channelProcessing().value());
        assertEquals(1, metrics.succeeded().value());
        assertEquals(1, metrics.failed().value());
        assertEquals(1, metrics.overdueTasks().value());
        assertEquals(OperationsWorkbenchMetrics.WorkbenchAvailability.AVAILABLE, metrics.availability());
        assertEquals(OperationsWorkbenchService.BUSINESS_TIMEZONE, metrics.businessTimezone());

        jdbc.execute("DROP TABLE ACT_RU_TASK");
        OperationsWorkbenchMetrics partial = withTenant(() -> service.metrics(11L, 101L));
        assertEquals(OperationsWorkbenchMetrics.WorkbenchAvailability.PARTIAL, partial.availability());
        assertEquals(OperationsWorkbenchMetrics.MetricAvailability.AVAILABLE, partial.drafts().availability());
        assertEquals(OperationsWorkbenchMetrics.MetricAvailability.STALE, partial.pendingReviews().availability());
        assertEquals(2, partial.pendingReviews().value());

        jdbc.execute("DROP TABLE biz_onboarding_application");
        OperationsWorkbenchMetrics stale = withTenant(() -> service.metrics(11L, 101L));
        assertEquals(OperationsWorkbenchMetrics.WorkbenchAvailability.STALE, stale.availability());
        OperationsWorkbenchService emptyService = new OperationsWorkbenchService(authorization, jdbc);
        OperationsWorkbenchMetrics unavailable = withTenant(() -> emptyService.metrics(11L, 101L));
        assertEquals(OperationsWorkbenchMetrics.WorkbenchAvailability.UNAVAILABLE, unavailable.availability());
        assertEquals(OperationsWorkbenchMetrics.MetricAvailability.UNAVAILABLE, unavailable.drafts().availability());
    }

    private OperationsWorkbenchMetrics withTenant(java.util.function.Supplier<OperationsWorkbenchMetrics> action) {
        TenantContext context = new TenantContext();
        context.setTenantId(11L);
        TenantContextHolder.setContext(context);
        try {
            return action.get();
        } finally {
            TenantContextHolder.clear();
        }
    }

    private void createSchema(JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE biz_merchant (id BIGINT PRIMARY KEY, tenant_id BIGINT, owning_agent_id BIGINT, operator_user_id BIGINT, reviewer_user_id BIGINT, deleted BIGINT)");
        jdbc.execute("CREATE TABLE biz_onboarding_application (id BIGINT PRIMARY KEY, tenant_id BIGINT, merchant_id BIGINT, status VARCHAR(32), deleted BIGINT)");
        jdbc.execute("CREATE TABLE biz_workflow_instance (id BIGINT PRIMARY KEY, tenant_id BIGINT, business_type VARCHAR(64), business_id BIGINT, process_instance_id VARCHAR(64), workflow_status VARCHAR(32), deleted BIGINT)");
        jdbc.execute("CREATE TABLE ACT_RU_TASK (ID_ VARCHAR(64) PRIMARY KEY, PROC_INST_ID_ VARCHAR(64), TASK_DEF_KEY_ VARCHAR(64), DUE_DATE_ TIMESTAMP)");
    }

    private void seed(JdbcTemplate jdbc) {
        jdbc.update("INSERT INTO biz_merchant VALUES (1, 11, 201, 501, 502, 0)");
        jdbc.update("INSERT INTO biz_merchant VALUES (2, 11, 999, 601, 602, 0)");
        List<String> statuses = List.of("DRAFT", "SUBMITTED", "CHANNEL_PROCESSING", "SUCCEEDED", "FAILED");
        for (int index = 0; index < statuses.size(); index++) {
            jdbc.update("INSERT INTO biz_onboarding_application VALUES (?, 11, 1, ?, 0)", 10 + index, statuses
                .get(index));
        }
        jdbc.update("INSERT INTO biz_onboarding_application VALUES (20, 11, 2, 'DRAFT', 0)");
        insertTask(jdbc, 31L, 10L, "P31", "reviewTask", LocalDateTime.now().plusDays(1));
        insertTask(jdbc, 32L, 11L, "P32", "supplementTask", LocalDateTime.now().plusDays(1));
        insertTask(jdbc, 33L, 12L, "P33", "escalatedReviewTask", LocalDateTime.now().minusDays(1));
        insertTask(jdbc, 34L, 20L, "P34", "reviewTask", LocalDateTime.now().minusDays(1));
    }

    private void insertTask(JdbcTemplate jdbc,
                            Long mappingId,
                            Long applicationId,
                            String processInstanceId,
                            String taskDefinitionKey,
                            LocalDateTime dueTime) {
        jdbc.update("INSERT INTO biz_workflow_instance VALUES (?, 11, 'MERCHANT_ONBOARDING', ?, ?, 'RUNNING', 0)", mappingId, applicationId, processInstanceId);
        jdbc.update("INSERT INTO ACT_RU_TASK VALUES (?, ?, ?, ?)", "T" + mappingId, processInstanceId, taskDefinitionKey, Timestamp
            .valueOf(dueTime));
    }
}
