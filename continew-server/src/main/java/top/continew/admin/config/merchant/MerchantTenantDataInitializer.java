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
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.common.api.tenant.TenantDataApi;
import top.continew.admin.common.model.dto.TenantDTO;
import top.continew.admin.merchant.master.domain.MerchantDomainException;
import top.continew.starter.extension.tenant.context.TenantContextHolder;
import top.continew.starter.extension.tenant.util.TenantUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/** Initializes the merchant identities and root agent required by a newly created tenant. */
@Component
@Order(200)
@RequiredArgsConstructor
public class MerchantTenantDataInitializer implements TenantDataApi {

    private static final List<String> BUSINESS_ROLE_CODES = List
        .of("AGENT_ADMIN", "MERCHANT_OPERATOR", "MERCHANT_REVIEWER", "RISK_REVIEWER", "CHANNEL_OPERATIONS");

    private final JdbcTemplate jdbcTemplate;
    private final IdentifierGenerator identifierGenerator;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void init(TenantDTO tenant) {
        if (tenant == null || tenant.getId() == null || tenant.getId() <= 0) {
            throw new IllegalArgumentException("Merchant tenant identifier is invalid");
        }
        TenantUtils.execute(tenant.getId(), () -> initialize(tenant));
    }

    @Transactional(rollbackFor = Exception.class)
    public void initializeExisting(Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            throw new MerchantDomainException("Merchant tenant identifier is invalid");
        }
        List<TenantDTO> tenants = jdbcTemplate.query("""
            SELECT id, name, admin_username, package_id
            FROM tenant WHERE id = ? AND deleted = 0
            """, (resultSet, rowNum) -> {
                TenantDTO tenant = new TenantDTO();
                tenant.setId(resultSet.getLong("id"));
                tenant.setName(resultSet.getString("name"));
                tenant.setAdminUsername(resultSet.getString("admin_username"));
                tenant.setPackageId(resultSet.getLong("package_id"));
                return tenant;
            }, tenantId);
        if (tenants.size() != 1) {
            throw new MerchantDomainException("Tenant is unavailable");
        }
        TenantUtils.execute(tenantId, () -> initialize(tenants.get(0)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clear() {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null || tenantId <= 0) {
            return;
        }
        jdbcTemplate.update("DELETE FROM biz_agent_closure WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM biz_agent WHERE tenant_id = ?", tenantId);
    }

    void initialize(TenantDTO tenant) {
        TenantAdmin admin = loadTenantAdmin(tenant.getId());
        BUSINESS_ROLE_CODES.forEach(code -> ensureRole(tenant.getId(), admin.userId(), code));
        ensureRootAgent(tenant, admin);
    }

    private TenantAdmin loadTenantAdmin(Long tenantId) {
        List<TenantAdmin> admins = jdbcTemplate.query("""
            SELECT t.admin_user, u.dept_id
            FROM tenant t
            JOIN sys_user u ON u.id = t.admin_user AND u.tenant_id = t.id AND u.deleted = 0
            WHERE t.id = ? AND t.deleted = 0
            """, (resultSet, rowNum) -> new TenantAdmin(resultSet.getLong("admin_user"), resultSet
            .getLong("dept_id")), tenantId);
        if (admins.size() != 1) {
            throw new IllegalStateException("Tenant administrator must be initialized before merchant data");
        }
        return admins.get(0);
    }

    private void ensureRole(Long tenantId, Long actorUserId, String code) {
        Long templateId = jdbcTemplate.queryForObject("""
            SELECT id FROM sys_role WHERE tenant_id = 0 AND code = ? AND deleted = 0
            """, Long.class, code);
        if (templateId == null) {
            throw new IllegalStateException("Merchant role template is unavailable: " + code);
        }
        List<Long> existing = jdbcTemplate.queryForList("""
            SELECT id FROM sys_role WHERE tenant_id = ? AND code = ? AND deleted = 0
            """, Long.class, tenantId, code);
        if (existing.size() > 1) {
            throw new IllegalStateException("Duplicate merchant role: " + code);
        }
        if (!existing.isEmpty()) {
            ensureRoleMenus(existing.get(0), tenantId, templateId);
            return;
        }
        Long roleId = nextId();
        int inserted = jdbcTemplate.update("""
            INSERT INTO sys_role
            (id, name, code, data_scope, description, sort, is_system, menu_check_strictly,
             dept_check_strictly, create_user, create_time, deleted, tenant_id)
            SELECT ?, name, code, data_scope, description, sort, is_system, menu_check_strictly,
                   dept_check_strictly, ?, ?, 0, ?
            FROM sys_role WHERE id = ? AND tenant_id = 0 AND deleted = 0
            """, roleId, actorUserId, LocalDateTime.now(), tenantId, templateId);
        if (inserted != 1) {
            throw new IllegalStateException("Merchant role initialization failed: " + code);
        }
        ensureRoleMenus(roleId, tenantId, templateId);
    }

    private void ensureRoleMenus(Long roleId, Long tenantId, Long templateId) {
        List<Long> menuIds = jdbcTemplate.queryForList("""
            SELECT menu_id FROM sys_role_menu WHERE role_id = ?
            """, Long.class, templateId);
        for (Long menuId : menuIds) {
            Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sys_role_menu WHERE role_id = ? AND menu_id = ?
                """, Integer.class, roleId, menuId);
            if (count != null && count == 0) {
                jdbcTemplate.update("""
                    INSERT INTO sys_role_menu (role_id, menu_id, tenant_id) VALUES (?, ?, ?)
                    """, roleId, menuId, tenantId);
            }
        }
    }

    private void ensureRootAgent(TenantDTO tenant, TenantAdmin admin) {
        Integer count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM biz_agent WHERE tenant_id = ? AND parent_id = 0 AND deleted = 0
            """, Integer.class, tenant.getId());
        if (count != null && count > 1) {
            throw new IllegalStateException("Tenant has multiple root agents");
        }
        if (count != null && count == 1) {
            return;
        }
        Long agentId = nextId();
        String agentNo = "ROOT-" + Long.toUnsignedString(tenant.getId(), 36).toUpperCase(Locale.ROOT);
        String tenantName = tenant.getName() == null || tenant.getName().isBlank() ? agentNo : tenant.getName().trim();
        String name = (tenantName + " Root Agent").substring(0, Math.min(tenantName.length() + 11, 100));
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
            INSERT INTO biz_agent
            (id, tenant_id, parent_id, path, user_id, dept_id, agent_no, name, contact_name,
             promotion_code_status, status, row_version, create_user, create_time, deleted)
            VALUES (?, ?, 0, ?, ?, ?, ?, ?, ?, 'DISABLED', 'ENABLED', 0, ?, ?, 0)
            """, agentId, tenant.getId(), "/" + agentId, admin.userId(), admin.deptId(), agentNo, name, tenant
                .getAdminUsername(), admin.userId(), now);
        jdbcTemplate.update("""
            INSERT INTO biz_agent_closure (tenant_id, ancestor_id, descendant_id, depth, create_time)
            VALUES (?, ?, ?, 0, ?)
            """, tenant.getId(), agentId, agentId, now);
    }

    private Long nextId() {
        return identifierGenerator.nextId(new Object()).longValue();
    }

    private record TenantAdmin(Long userId, Long deptId) {
    }
}
