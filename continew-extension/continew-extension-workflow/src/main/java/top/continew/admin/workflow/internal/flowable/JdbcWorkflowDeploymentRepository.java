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

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import top.continew.admin.workflow.api.WorkflowOperationException;
import top.continew.admin.workflow.dto.WorkflowDeploymentRef;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Explicit-tenant, append-only repository for validated BPMN deployment metadata. */
@Repository
public class JdbcWorkflowDeploymentRepository {

    private static final String SELECT_COLUMNS = """
        SELECT id, tenant_id, deployment_id, process_definition_id, process_definition_key,
               process_definition_version, contract_version, resource_name, resource_sha256, deployed_time
        FROM biz_workflow_deployment
        """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcWorkflowDeploymentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    Optional<WorkflowDeploymentRef> findByContractVersion(Long tenantId,
                                                          String processDefinitionKey,
                                                          Integer contractVersion) {
        List<WorkflowDeploymentRef> deployments = jdbcTemplate.query(SELECT_COLUMNS + """
             WHERE tenant_id = ? AND process_definition_key = ? AND contract_version = ?
            """, this::map, tenantId, processDefinitionKey, contractVersion);
        return single(deployments);
    }

    Optional<WorkflowDeploymentRef> findByResourceHash(Long tenantId,
                                                       String processDefinitionKey,
                                                       String resourceSha256) {
        List<WorkflowDeploymentRef> deployments = jdbcTemplate.query(SELECT_COLUMNS + """
             WHERE tenant_id = ? AND process_definition_key = ? AND resource_sha256 = ?
            """, this::map, tenantId, processDefinitionKey, resourceSha256);
        return single(deployments);
    }

    WorkflowDeploymentRef insert(Long metadataId,
                                 Long tenantId,
                                 String deploymentId,
                                 String processDefinitionId,
                                 String processDefinitionKey,
                                 Integer processDefinitionVersion,
                                 Integer contractVersion,
                                 String resourceName,
                                 String resourceSha256,
                                 Long deployedBy,
                                 LocalDateTime deployedTime) {
        try {
            int inserted = jdbcTemplate
                .update("""
                    INSERT INTO biz_workflow_deployment
                    (id, tenant_id, deployment_id, process_definition_id, process_definition_key,
                     process_definition_version, contract_version, resource_name, resource_sha256,
                     deployed_by, deployed_time)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, metadataId, tenantId, deploymentId, processDefinitionId, processDefinitionKey, processDefinitionVersion, contractVersion, resourceName, resourceSha256, deployedBy, deployedTime);
            if (inserted != 1) {
                throw engineFailure();
            }
        } catch (DataIntegrityViolationException ex) {
            throw new WorkflowOperationException(WorkflowOperationException.Code.DEPLOYMENT_CONFLICT);
        }
        return new WorkflowDeploymentRef(metadataId, tenantId, deploymentId, processDefinitionId, processDefinitionKey, processDefinitionVersion, contractVersion, resourceName, resourceSha256, deployedTime);
    }

    private Optional<WorkflowDeploymentRef> single(List<WorkflowDeploymentRef> deployments) {
        if (deployments.size() > 1) {
            throw engineFailure();
        }
        return deployments.stream().findFirst();
    }

    private WorkflowDeploymentRef map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new WorkflowDeploymentRef(resultSet.getLong("id"), resultSet.getLong("tenant_id"), resultSet
            .getString("deployment_id"), resultSet.getString("process_definition_id"), resultSet
                .getString("process_definition_key"), resultSet.getInt("process_definition_version"), resultSet
                    .getInt("contract_version"), resultSet.getString("resource_name"), resultSet
                        .getString("resource_sha256"), resultSet.getTimestamp("deployed_time").toLocalDateTime());
    }

    private WorkflowOperationException engineFailure() {
        return new WorkflowOperationException(WorkflowOperationException.Code.ENGINE_FAILURE);
    }
}
