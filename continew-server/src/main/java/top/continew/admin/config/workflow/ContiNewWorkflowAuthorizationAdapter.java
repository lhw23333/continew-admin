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

package top.continew.admin.config.workflow;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import top.continew.admin.merchant.master.application.MerchantScopeAuthorizationService;
import top.continew.admin.workflow.api.WorkflowActor;
import top.continew.admin.workflow.api.WorkflowAuthorizationPort;
import top.continew.admin.workflow.api.WorkflowOperationException;
import top.continew.starter.extension.tenant.util.TenantUtils;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** ContiNew user/role authority plus merchant/agent-scope authorization for workflow operations. */
@Component
public class ContiNewWorkflowAuthorizationAdapter implements WorkflowAuthorizationPort {

    private final JdbcTemplate jdbcTemplate;
    private final MerchantScopeAuthorizationService merchantScopeAuthorizationService;

    public ContiNewWorkflowAuthorizationAdapter(JdbcTemplate jdbcTemplate,
                                                MerchantScopeAuthorizationService merchantScopeAuthorizationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.merchantScopeAuthorizationService = merchantScopeAuthorizationService;
    }

    @Override
    public WorkflowActor requireActor(Long tenantId, Long userId) {
        if (tenantId == null || tenantId <= 0 || userId == null || userId <= 0) {
            throw notFound();
        }
        Integer enabled = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM sys_user
            WHERE tenant_id = ? AND id = ? AND status = 1 AND deleted = 0
            """, Integer.class, tenantId, userId);
        if (enabled == null || enabled != 1) {
            throw notFound();
        }
        List<String> roleCodes = jdbcTemplate.queryForList("""
            SELECT r.code
            FROM sys_user_role ur
            JOIN sys_role r ON r.id = ur.role_id AND r.tenant_id = ur.tenant_id AND r.deleted = 0
            WHERE ur.tenant_id = ? AND ur.user_id = ?
            ORDER BY r.code
            """, String.class, tenantId, userId);
        return new WorkflowActor(tenantId, userId, Set.copyOf(new TreeSet<>(roleCodes)));
    }

    @Override
    public boolean canAccessBusiness(WorkflowActor actor, String businessType, Long businessId) {
        if (actor == null || businessType == null || businessId == null || businessId <= 0) {
            return false;
        }
        Long merchantId = switch (businessType) {
            case "MERCHANT_ONBOARDING" -> merchantId("biz_onboarding_application", actor.tenantId(), businessId);
            case "MERCHANT_REVERIFICATION" -> merchantId("biz_merchant_reverification_request", actor
                .tenantId(), businessId);
            case "MERCHANT_LIMIT_ADJUSTMENT" -> merchantId("biz_limit_adjustment", actor.tenantId(), businessId);
            default -> null;
        };
        if (merchantId == null || !owningAgentEnabled(actor.tenantId(), merchantId)) {
            return false;
        }
        boolean[] accessible = {false};
        TenantUtils.execute(actor.tenantId(), () -> accessible[0] = merchantScopeAuthorizationService.canAccess(actor
            .tenantId(), actor.userId(), merchantId));
        return accessible[0];
    }

    private Long merchantId(String table, Long tenantId, Long businessId) {
        List<Long> values = jdbcTemplate
            .queryForList("SELECT merchant_id FROM " + table + " WHERE tenant_id = ? AND id = ? AND deleted = 0", Long.class, tenantId, businessId);
        return values.size() == 1 ? values.get(0) : null;
    }

    private boolean owningAgentEnabled(Long tenantId, Long merchantId) {
        Integer count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM biz_merchant m
            JOIN biz_agent a ON a.tenant_id = m.tenant_id AND a.id = m.owning_agent_id AND a.deleted = 0
            WHERE m.tenant_id = ? AND m.id = ? AND m.deleted = 0 AND a.status = 'ENABLED'
            """, Integer.class, tenantId, merchantId);
        return count != null && count == 1;
    }

    private WorkflowOperationException notFound() {
        return new WorkflowOperationException(WorkflowOperationException.Code.NOT_FOUND);
    }
}
