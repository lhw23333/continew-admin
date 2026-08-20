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
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

abstract class AbstractMigrationRoundTripIT {

    protected abstract DataSource createDataSource();

    protected abstract String databaseFolder();

    @Test
    void phaseOneSchemaSupportsForwardRollbackForwardRoundTrip() {
        DataSource dataSource = createDataSource();
        applyForward(dataSource);
        assertSchemaPresent(dataSource);

        execute(dataSource, "db/rollback/" + databaseFolder() + "/phase1-schema-rollback.sql");
        assertSchemaAbsent(dataSource);

        applyForward(dataSource);
        assertSchemaPresent(dataSource);
    }

    private void applyForward(DataSource dataSource) {
        String prefix = "db/changelog/" + databaseFolder();
        execute(dataSource, prefix + "/merchant/merchant-core.sql", prefix + "/merchant/sensitive-key-versions.sql", prefix + "/merchant/merchant-operations.sql", prefix + "/merchant/merchant-constraints.sql", prefix + "/merchant/merchant-indexes.sql", prefix + "/flowable/flowable-7.1.0.sql");
    }

    private void execute(DataSource dataSource, String... paths) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.setContinueOnError(false);
        for (String path : paths) {
            populator.addScript(new ClassPathResource(path));
        }
        populator.execute(dataSource);
    }

    private void assertSchemaPresent(DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM biz_agent", Integer.class));
        assertEquals("7.1.0.2", jdbcTemplate
            .queryForObject("SELECT VALUE_ FROM ACT_GE_PROPERTY WHERE NAME_ = 'schema.version'", String.class));
    }

    private void assertSchemaAbsent(DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        assertThrows(DataAccessException.class, () -> jdbcTemplate
            .queryForObject("SELECT COUNT(*) FROM biz_agent", Integer.class));
        assertThrows(DataAccessException.class, () -> jdbcTemplate
            .queryForObject("SELECT COUNT(*) FROM ACT_GE_PROPERTY", Integer.class));
    }
}
