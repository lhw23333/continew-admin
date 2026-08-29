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

package top.continew.admin.config.merchant;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import top.continew.admin.common.model.dto.TenantDTO;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MerchantTenantDataInitializerTest {

    private EmbeddedDatabase database;
    private JdbcTemplate jdbcTemplate;
    private MerchantTenantDataInitializer initializer;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).build();
        jdbcTemplate = new JdbcTemplate(database);
        createSchema();
        seedTemplates();
        IdentifierGenerator identifierGenerator = mock(IdentifierGenerator.class);
        AtomicLong ids = new AtomicLong(1000);
        when(identifierGenerator.nextId(any())).thenAnswer(invocation -> ids.incrementAndGet());
        initializer = new MerchantTenantDataInitializer(jdbcTemplate, identifierGenerator);
    }

    @AfterEach
    void tearDown() {
        database.shutdown();
    }

    @Test
    void initializesRolesAndOneRootAgentIdempotently() {
        TenantDTO tenant = new TenantDTO();
        tenant.setId(100L);
        tenant.setName("Acceptance Tenant");
        tenant.setAdminUsername("tenant-admin");

        initializer.initialize(tenant);
        initializer.initialize(tenant);

        assertEquals(5, count("SELECT COUNT(*) FROM sys_role WHERE tenant_id = 100"));
        assertEquals(5, count("SELECT COUNT(*) FROM sys_role_menu WHERE tenant_id = 100"));
        assertEquals(1, count("SELECT COUNT(*) FROM biz_agent WHERE tenant_id = 100 AND parent_id = 0"));
        assertEquals(1, count("SELECT COUNT(*) FROM biz_agent_closure WHERE tenant_id = 100 AND depth = 0"));
    }

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }

    private void createSchema() {
        jdbcTemplate.execute("""
            CREATE TABLE tenant (id BIGINT PRIMARY KEY, name VARCHAR(30), admin_username VARCHAR(64), package_id BIGINT,
                                 admin_user BIGINT, status INT, deleted BIGINT)
            """);
        jdbcTemplate.execute("""
            CREATE TABLE sys_user (id BIGINT PRIMARY KEY, tenant_id BIGINT, dept_id BIGINT, deleted BIGINT)
            """);
        jdbcTemplate.execute("""
            CREATE TABLE sys_role (id BIGINT PRIMARY KEY, name VARCHAR(30), code VARCHAR(30), data_scope INT,
                description VARCHAR(200), sort INT, is_system BOOLEAN, menu_check_strictly BOOLEAN,
                dept_check_strictly BOOLEAN, create_user BIGINT, create_time TIMESTAMP, deleted BIGINT, tenant_id BIGINT)
            """);
        jdbcTemplate.execute("""
            CREATE TABLE sys_role_menu (role_id BIGINT, menu_id BIGINT, tenant_id BIGINT,
                                        PRIMARY KEY (role_id, menu_id))
            """);
        jdbcTemplate.execute("""
            CREATE TABLE biz_agent (id BIGINT PRIMARY KEY, tenant_id BIGINT, parent_id BIGINT, path VARCHAR(1024),
                user_id BIGINT, dept_id BIGINT, agent_no VARCHAR(64), name VARCHAR(100), contact_name VARCHAR(100),
                promotion_code_status VARCHAR(32), status VARCHAR(32), row_version BIGINT, create_user BIGINT,
                create_time TIMESTAMP, deleted BIGINT)
            """);
        jdbcTemplate.execute("""
            CREATE TABLE biz_agent_closure (tenant_id BIGINT, ancestor_id BIGINT, descendant_id BIGINT,
                depth INT, create_time TIMESTAMP, PRIMARY KEY (tenant_id, ancestor_id, descendant_id))
            """);
    }

    private void seedTemplates() {
        jdbcTemplate.update("""
            INSERT INTO tenant VALUES (100, 'Acceptance Tenant', 'tenant-admin', 1, 200, 1, 0)
            """);
        jdbcTemplate.update("INSERT INTO sys_user VALUES (200, 100, 300, 0)");
        String[] codes = {"AGENT_ADMIN", "MERCHANT_OPERATOR", "MERCHANT_REVIEWER", "RISK_REVIEWER",
            "CHANNEL_OPERATIONS"};
        for (int index = 0; index < codes.length; index++) {
            long roleId = index + 1L;
            jdbcTemplate.update("""
                INSERT INTO sys_role VALUES (?, ?, ?, 4, ?, 1, TRUE, TRUE, TRUE, 1, CURRENT_TIMESTAMP, 0, 0)
                """, roleId, codes[index], codes[index], codes[index]);
            jdbcTemplate.update("INSERT INTO sys_role_menu VALUES (?, ?, 0)", roleId, 500 + index);
        }
    }
}
