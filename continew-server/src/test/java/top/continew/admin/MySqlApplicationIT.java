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

package top.continew.admin;

import org.junit.jupiter.api.Test;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles({"integration-test", "integration-mysql"})
class MySqlApplicationIT extends AbstractApplicationIT {

    @Container
    @ServiceConnection
    private static final MySQLContainer<?> DATABASE = new MySQLContainer<>("mysql:8.4.0");

    @Test
    void representativeQueriesUseCompositeIndexes() {
        seedRepresentativeQueryData();
        jdbcTemplate
            .execute("ANALYZE TABLE biz_merchant, biz_onboarding_application, biz_outbox_event, biz_channel_event");
        assertUsesIndex("idx_merchant_scope_status_time", """
            EXPLAIN SELECT id FROM biz_merchant
            WHERE tenant_id = 18 AND owning_agent_id = 18 AND status = 'ENABLED' AND deleted = 0
            ORDER BY create_time DESC, id DESC LIMIT 20
            """);
        assertUsesIndex("idx_onboarding_scope_status_time", """
            EXPLAIN SELECT id FROM biz_onboarding_application
            WHERE tenant_id = 18 AND owning_agent_id = 18 AND status = 'CHANNEL_PROCESSING'
            ORDER BY submitted_time DESC, id DESC LIMIT 20
            """);
        assertUsesIndex("idx_outbox_status_retry", """
            EXPLAIN SELECT id FROM biz_outbox_event
            WHERE status = 'PENDING' AND next_retry_time <= CURRENT_TIMESTAMP
            ORDER BY next_retry_time, id LIMIT 50
            """);
        assertUsesIndex("idx_channel_event_status_time", """
            EXPLAIN SELECT id FROM biz_channel_event
            WHERE tenant_id = 18 AND channel_code = 'CH2' AND processing_status = 'FAILED'
            ORDER BY received_time DESC, id DESC LIMIT 20
            """);
    }

    private void assertUsesIndex(String expectedIndex, String explainSql) {
        boolean used = jdbcTemplate.queryForList(explainSql)
            .stream()
            .map(this::getPlanKey)
            .anyMatch(expectedIndex::equalsIgnoreCase);
        assertTrue(used, () -> "MySQL plan did not use " + expectedIndex + ": " + jdbcTemplate
            .queryForList(explainSql));
    }

    private String getPlanKey(Map<String, Object> row) {
        return row.entrySet()
            .stream()
            .filter(entry -> "key".equalsIgnoreCase(entry.getKey()))
            .map(Map.Entry::getValue)
            .map(String::valueOf)
            .findFirst()
            .orElse("");
    }
}
