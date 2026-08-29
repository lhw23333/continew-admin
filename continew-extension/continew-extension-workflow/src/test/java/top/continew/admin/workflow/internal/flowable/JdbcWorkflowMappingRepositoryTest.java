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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcWorkflowMappingRepositoryTest {

    private EmbeddedDatabase database;
    private JdbcTemplate jdbcTemplate;
    private JdbcWorkflowMappingRepository repository;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).build();
        jdbcTemplate = new JdbcTemplate(database);
        jdbcTemplate.execute("""
            CREATE TABLE biz_workflow_instance (
                id BIGINT PRIMARY KEY, tenant_id BIGINT, business_type VARCHAR(64), business_id BIGINT,
                business_version BIGINT, process_definition_id VARCHAR(128), process_definition_key VARCHAR(128),
                process_definition_version INT, process_instance_id VARCHAR(64), business_key VARCHAR(255),
                workflow_status VARCHAR(32), started_time TIMESTAMP, ended_time TIMESTAMP, row_version BIGINT,
                update_time TIMESTAMP, deleted BIGINT)
            """);
        jdbcTemplate.update("""
            INSERT INTO biz_workflow_instance VALUES
            (1, 101, 'MERCHANT_ONBOARDING', 201, 3, 'definition-1', 'merchant-onboarding-review-v1', 1,
             'process-1', '101:MERCHANT_ONBOARDING:201:3', 'RUNNING', CURRENT_TIMESTAMP, NULL, 0, NULL, 0)
            """);
        repository = new JdbcWorkflowMappingRepository(jdbcTemplate);
    }

    @AfterEach
    void tearDown() {
        database.shutdown();
    }

    @Test
    void marksRunningMappingCompletedOnce() {
        LocalDateTime endedTime = LocalDateTime.of(2026, 8, 29, 2, 10);

        assertTrue(repository.markEnded(101L, "process-1", "COMPLETED", endedTime));
        assertFalse(repository.markEnded(101L, "process-1", "COMPLETED", endedTime));

        assertEquals("COMPLETED", jdbcTemplate.queryForObject("""
            SELECT workflow_status FROM biz_workflow_instance WHERE id = 1
            """, String.class));
        assertEquals(1L, jdbcTemplate.queryForObject("""
            SELECT row_version FROM biz_workflow_instance WHERE id = 1
            """, Long.class));
    }
}
