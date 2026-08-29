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
import top.continew.admin.workflow.api.WorkflowMappingService;
import top.continew.admin.workflow.api.WorkflowOperationException;
import top.continew.admin.workflow.dto.WorkflowInstanceMapping;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Explicit-tenant JDBC repository for the project-owned workflow mapping table. */
@Repository
public class JdbcWorkflowMappingRepository implements WorkflowMappingService {

    private static final String SELECT_COLUMNS = """
        SELECT id, tenant_id, business_type, business_id, business_version, process_definition_id,
               process_definition_key, process_definition_version, process_instance_id, business_key,
               workflow_status, started_time, ended_time, row_version
        FROM biz_workflow_instance
        """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcWorkflowMappingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<WorkflowInstanceMapping> findByBusinessKey(Long tenantId, String businessKey) {
        if (tenantId == null || tenantId <= 0 || businessKey == null || businessKey.isBlank()) {
            return Optional.empty();
        }
        List<WorkflowInstanceMapping> mappings = jdbcTemplate.query(SELECT_COLUMNS + """
             WHERE tenant_id = ? AND business_key = ? AND deleted = 0
            """, this::map, tenantId, businessKey);
        return single(mappings);
    }

    Optional<WorkflowInstanceMapping> findByBusinessKeyForUpdate(Long tenantId, String businessKey) {
        List<WorkflowInstanceMapping> mappings = jdbcTemplate.query(SELECT_COLUMNS + """
             WHERE tenant_id = ? AND business_key = ? AND deleted = 0
             FOR UPDATE
            """, this::map, tenantId, businessKey);
        return single(mappings);
    }

    @Override
    public Optional<WorkflowInstanceMapping> findByProcessInstanceId(Long tenantId, String processInstanceId) {
        if (tenantId == null || tenantId <= 0 || processInstanceId == null || processInstanceId.isBlank()) {
            return Optional.empty();
        }
        List<WorkflowInstanceMapping> mappings = jdbcTemplate.query(SELECT_COLUMNS + """
             WHERE tenant_id = ? AND process_instance_id = ? AND deleted = 0
            """, this::map, tenantId, processInstanceId);
        return single(mappings);
    }

    @Override
    public boolean markEnded(Long tenantId, String processInstanceId, String workflowStatus, LocalDateTime endedTime) {
        if (tenantId == null || tenantId <= 0 || processInstanceId == null || processInstanceId
            .isBlank() || workflowStatus == null || workflowStatus.isBlank() || endedTime == null) {
            return false;
        }
        return jdbcTemplate.update("""
            UPDATE biz_workflow_instance
            SET workflow_status = ?, ended_time = ?, row_version = row_version + 1, update_time = ?
            WHERE tenant_id = ? AND process_instance_id = ? AND workflow_status = 'RUNNING' AND deleted = 0
            """, workflowStatus, endedTime, endedTime, tenantId, processInstanceId) == 1;
    }

    WorkflowInstanceMapping insert(Long mappingId,
                                   WorkflowBusinessKey businessKey,
                                   String processDefinitionId,
                                   String processDefinitionKey,
                                   Integer processDefinitionVersion,
                                   String processInstanceId,
                                   LocalDateTime startedTime) {
        try {
            int inserted = jdbcTemplate.update("""
                INSERT INTO biz_workflow_instance
                (id, tenant_id, business_type, business_id, business_version, process_definition_key,
                 process_definition_version, process_instance_id, business_key, workflow_status, started_time,
                 row_version, create_time, deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'RUNNING', ?, 0, ?, 0)
                """, mappingId, businessKey.tenantId(), businessKey.businessType(), businessKey
                .businessId(), businessKey
                    .businessVersion(), processDefinitionKey, processDefinitionVersion, processInstanceId, businessKey
                        .value(), startedTime, startedTime);
            if (inserted != 1) {
                throw engineFailure();
            }
        } catch (DataIntegrityViolationException ex) {
            throw engineFailure();
        }
        return new WorkflowInstanceMapping(mappingId, businessKey.tenantId(), businessKey.businessType(), businessKey
            .businessId(), businessKey
                .businessVersion(), processDefinitionId, processDefinitionKey, processDefinitionVersion, processInstanceId, businessKey
                    .value(), "RUNNING", startedTime, null, 0L);
    }

    private Optional<WorkflowInstanceMapping> single(List<WorkflowInstanceMapping> mappings) {
        if (mappings.size() > 1) {
            throw engineFailure();
        }
        return mappings.stream().findFirst();
    }

    private WorkflowInstanceMapping map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new WorkflowInstanceMapping(resultSet.getLong("id"), resultSet.getLong("tenant_id"), resultSet
            .getString("business_type"), resultSet.getLong("business_id"), resultSet
                .getLong("business_version"), resultSet.getString("process_definition_id"), resultSet
                    .getString("process_definition_key"), resultSet.getInt("process_definition_version"), resultSet
                        .getString("process_instance_id"), resultSet.getString("business_key"), resultSet
                            .getString("workflow_status"), resultSet.getTimestamp("started_time")
                                .toLocalDateTime(), resultSet.getTimestamp("ended_time") == null
                                    ? null
                                    : resultSet.getTimestamp("ended_time").toLocalDateTime(), resultSet
                                        .getLong("row_version"));
    }

    private WorkflowOperationException engineFailure() {
        return new WorkflowOperationException(WorkflowOperationException.Code.ENGINE_FAILURE);
    }
}
