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

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import top.continew.admin.config.merchant.MerchantTenantDataInitializer;
import top.continew.admin.merchant.master.domain.MerchantDomainException;
import top.continew.admin.workflow.api.WorkflowDeploymentService;
import top.continew.admin.workflow.api.WorkflowMappingService;
import top.continew.admin.workflow.definition.MerchantLimitAdjustmentWorkflowDefinition;
import top.continew.admin.workflow.definition.MerchantOnboardingReviewWorkflowDefinition;
import top.continew.admin.workflow.dto.WorkflowDeploymentRef;

import java.util.List;
import java.time.LocalDateTime;

/** Explicit platform-operations boundary for merchant tenant bootstrap and reviewed workflow deployment. */
@Service
@RequiredArgsConstructor
public class MerchantFoundationOperationsService {

    private static final int REQUIRED_ROLE_COUNT = 5;
    private static final int REQUIRED_WORKFLOW_COUNT = 2;

    private final MerchantTenantDataInitializer tenantDataInitializer;
    private final WorkflowDeploymentService deploymentService;
    private final MerchantOnboardingReviewWorkflowDefinition onboardingDefinition;
    private final MerchantLimitAdjustmentWorkflowDefinition limitDefinition;
    private final WorkflowMappingService mappingService;
    private final JdbcTemplate jdbcTemplate;

    public MerchantFoundationReadiness bootstrap(Long tenantId) {
        tenantDataInitializer.initializeExisting(tenantId);
        return readiness(tenantId);
    }

    public List<WorkflowDeploymentRef> deployWorkflows(Long tenantId) {
        Long actorUserId = tenantAdmin(tenantId);
        return List.of(deploymentService.deploy(onboardingDefinition.deploymentCommand(tenantId, actorUserId)), deploymentService
            .deploy(limitDefinition.deploymentCommand(tenantId, actorUserId)));
    }

    public MerchantFoundationReadiness readiness(Long tenantId) {
        Long actorUserId = tenantAdmin(tenantId);
        Integer roles = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM sys_role
            WHERE tenant_id = ? AND deleted = 0
              AND code IN ('AGENT_ADMIN', 'MERCHANT_OPERATOR', 'MERCHANT_REVIEWER', 'RISK_REVIEWER', 'CHANNEL_OPERATIONS')
            """, Integer.class, tenantId);
        Integer roots = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM biz_agent WHERE tenant_id = ? AND parent_id = 0 AND deleted = 0
            """, Integer.class, tenantId);
        Integer workflows = jdbcTemplate.queryForObject("""
            SELECT COUNT(DISTINCT process_definition_key) FROM biz_workflow_deployment
            WHERE tenant_id = ? AND process_definition_key IN (?, ?)
            """, Integer.class, tenantId, MerchantOnboardingReviewWorkflowDefinition.PROCESS_KEY, MerchantLimitAdjustmentWorkflowDefinition.PROCESS_KEY);
        return new MerchantFoundationReadiness(tenantId, actorUserId, roles != null && roles == REQUIRED_ROLE_COUNT, roots != null && roots == 1, workflows != null && workflows == REQUIRED_WORKFLOW_COUNT);
    }

    public int reconcileWorkflowMappings(Long tenantId) {
        tenantAdmin(tenantId);
        List<EndedProcess> ended = jdbcTemplate.query("""
            SELECT mapping.process_instance_id, history.END_TIME_ AS ended_time
            FROM biz_workflow_instance mapping
            JOIN ACT_HI_PROCINST history ON history.PROC_INST_ID_ = mapping.process_instance_id
            WHERE mapping.tenant_id = ? AND mapping.workflow_status = 'RUNNING'
              AND mapping.deleted = 0 AND history.END_TIME_ IS NOT NULL
            """, (resultSet, rowNum) -> new EndedProcess(resultSet.getString("process_instance_id"), resultSet
            .getTimestamp("ended_time")
            .toLocalDateTime()), tenantId);
        return (int)ended.stream()
            .filter(process -> mappingService.markEnded(tenantId, process.processInstanceId(), "COMPLETED", process
                .endedTime()))
            .count();
    }

    private Long tenantAdmin(Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            throw new MerchantDomainException("Tenant identifier is invalid");
        }
        List<Long> admins = jdbcTemplate.queryForList("""
            SELECT admin_user FROM tenant
            WHERE id = ? AND admin_user IS NOT NULL AND status = 1 AND deleted = 0
            """, Long.class, tenantId);
        if (admins.size() != 1) {
            throw new MerchantDomainException("Tenant administrator is unavailable");
        }
        return admins.get(0);
    }

    public record MerchantFoundationReadiness(Long tenantId, Long tenantAdminUserId, boolean businessRolesReady,
                                              boolean rootAgentReady, boolean workflowsReady) {
        public boolean ready() {
            return businessRolesReady && rootAgentReady && workflowsReady;
        }
    }

    private record EndedProcess(String processInstanceId, LocalDateTime endedTime) {
    }
}
