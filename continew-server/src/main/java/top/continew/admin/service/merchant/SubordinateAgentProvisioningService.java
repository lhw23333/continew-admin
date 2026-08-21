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

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import top.continew.admin.common.enums.DisEnableStatusEnum;
import top.continew.admin.common.enums.GenderEnum;
import top.continew.admin.merchant.agent.application.AgentHierarchyService;
import top.continew.admin.merchant.agent.application.AgentPromotionCodeService;
import top.continew.admin.merchant.agent.application.AgentRepository;
import top.continew.admin.merchant.agent.domain.Agent;
import top.continew.admin.merchant.agent.domain.AgentAccessDeniedException;
import top.continew.admin.merchant.agent.domain.AgentDomainException;
import top.continew.admin.merchant.agent.domain.AgentRegistration;
import top.continew.admin.merchant.security.crypto.SensitiveValueProtector;
import top.continew.admin.merchant.security.value.EncryptedMobileNumber;
import top.continew.admin.system.mapper.DeptMapper;
import top.continew.admin.system.mapper.UserRoleMapper;
import top.continew.admin.system.mapper.user.UserMapper;
import top.continew.admin.system.model.entity.DeptDO;
import top.continew.admin.system.model.entity.UserRoleDO;
import top.continew.admin.system.model.entity.user.UserDO;
import top.continew.starter.extension.tenant.context.TenantContextHolder;

import java.time.LocalDateTime;
import java.util.Locale;

/** Atomically provisions department, temporary-password identity, role binding, agent, and closure rows. */
@Service
@RequiredArgsConstructor
public class SubordinateAgentProvisioningService {

    public static final String AGENT_ADMIN_ROLE_CODE = "AGENT_ADMIN";
    public static final String PASSWORD_CHANGE_REQUIRED = "PASSWORD_CHANGE_REQUIRED";

    private final AgentRepository agentRepository;
    private final AgentHierarchyService agentHierarchyService;
    private final DeptMapper deptMapper;
    private final JdbcTemplate jdbcTemplate;
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final IdentifierGenerator identifierGenerator;
    private final SensitiveValueProtector sensitiveValueProtector;
    private final AgentPromotionCodeService promotionCodeService;

    @Transactional(rollbackFor = Exception.class)
    public SubordinateAgentProvisioningResult create(SubordinateAgentCreateCommand command) {
        requireTenantContext(command.tenantId());
        Agent parentAgent = agentRepository.findByUserId(command.tenantId(), command.actorUserId())
            .filter(Agent::isEnabled)
            .orElseThrow(AgentAccessDeniedException::new);
        if (agentRepository.existsByAgentNo(command.tenantId(), command.agentNo())) {
            throw new AgentDomainException("Agent number already exists");
        }
        Long parentDeptId = resolveParentDepartment(command, parentAgent);
        ParentDepartment parentDept = loadParentDepartment(parentDeptId);
        if (parentDept == null || DisEnableStatusEnum.DISABLE.getValue().equals(parentDept.status())) {
            throw new AgentDomainException("Parent agent department is unavailable");
        }

        Long agentId = identifierGenerator.nextId(new Object()).longValue();
        DeptDO department = createDepartment(command, parentDept, agentId);
        Long roleId = loadAgentAdminRoleId();
        if (roleId == null) {
            throw new AgentDomainException("Agent administrator role is unavailable");
        }
        String username = generateUsername(command.agentNo(), agentId);
        UserDO user = createTemporaryPasswordUser(command, department.getId(), username);
        if (userRoleMapper.insert(new UserRoleDO(user.getId(), roleId)) != 1) {
            throw new AgentDomainException("Agent role binding failed");
        }

        EncryptedMobileNumber encryptedMobile = EncryptedMobileNumber.fromPlaintext(command
            .contactMobile(), sensitiveValueProtector);
        String promotionCode = promotionCodeService.generateUniqueCodeForProvisioning(command.tenantId());
        Agent agent = agentHierarchyService.register(new AgentRegistration(agentId, command.tenantId(), parentAgent
            .id(), user.getId(), department.getId(), command.agentNo(), command.name(), command
                .contactName(), encryptedMobile, promotionCode));
        return new SubordinateAgentProvisioningResult(agent.id(), user.getId(), department
            .getId(), username, PASSWORD_CHANGE_REQUIRED);
    }

    private Long resolveParentDepartment(SubordinateAgentCreateCommand command, Agent parentAgent) {
        if (parentAgent.deptId() != null) {
            return parentAgent.deptId();
        }
        UserDO parentUser = userMapper.selectById(command.actorUserId());
        if (parentUser == null || parentUser.getDeptId() == null) {
            throw new AgentDomainException("Parent agent department mapping is unavailable");
        }
        if (!agentRepository.bindDepartment(command.tenantId(), parentAgent.id(), parentUser.getDeptId())) {
            Agent reloaded = agentRepository.findById(command.tenantId(), parentAgent.id())
                .orElseThrow(AgentAccessDeniedException::new);
            if (reloaded.deptId() == null || !reloaded.deptId().equals(parentUser.getDeptId())) {
                throw new AgentDomainException("Parent agent department mapping conflicted");
            }
        }
        return parentUser.getDeptId();
    }

    private DeptDO createDepartment(SubordinateAgentCreateCommand command, ParentDepartment parentDept, Long agentId) {
        String departmentName = departmentName(command.agentNo(), agentId);
        if (deptMapper.lambdaQuery()
            .eq(DeptDO::getParentId, parentDept.id())
            .eq(DeptDO::getName, departmentName)
            .eq(DeptDO::getDeleted, 0L)
            .exists()) {
            throw new AgentDomainException("Agent department already exists");
        }
        DeptDO department = new DeptDO();
        department.setName(departmentName);
        department.setParentId(parentDept.id());
        department.setAncestors(parentDept.ancestors() + ',' + parentDept.id());
        department.setDescription("Agent department for " + command.agentNo());
        department.setSort(999);
        department.setStatus(DisEnableStatusEnum.ENABLE);
        department.setIsSystem(false);
        department.setCreateUser(command.actorUserId());
        department.setCreateTime(LocalDateTime.now());
        department.setDeleted(0L);
        if (deptMapper.insert(department) != 1) {
            throw new AgentDomainException("Agent department creation failed");
        }
        return department;
    }

    private ParentDepartment loadParentDepartment(Long parentDeptId) {
        try {
            return jdbcTemplate.queryForObject("""
                SELECT id, ancestors, status FROM sys_dept WHERE id = ? AND deleted = 0
                """, (resultSet, rowNum) -> new ParentDepartment(resultSet.getLong("id"), resultSet
                .getString("ancestors"), resultSet.getInt("status")), parentDeptId);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    private Long loadAgentAdminRoleId() {
        try {
            return jdbcTemplate.queryForObject("""
                SELECT id FROM sys_role WHERE code = ? AND deleted = 0
                """, Long.class, AGENT_ADMIN_ROLE_CODE);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    private UserDO createTemporaryPasswordUser(SubordinateAgentCreateCommand command, Long deptId, String username) {
        if (userMapper.selectByUsername(username) != null) {
            throw new AgentDomainException("Generated agent username already exists");
        }
        UserDO user = new UserDO();
        user.setUsername(username);
        user.setNickname(nickname(command.name()));
        user.setPassword(command.temporaryPassword());
        user.setGender(GenderEnum.UNKNOWN);
        user.setDeptId(deptId);
        user.setDescription("Agent account requires first-login password change");
        user.setStatus(DisEnableStatusEnum.ENABLE);
        user.setIsSystem(false);
        user.setPwdResetTime(LocalDateTime.now());
        user.setMustChangePassword(true);
        user.setCreateUser(command.actorUserId());
        user.setCreateTime(LocalDateTime.now());
        user.setDeleted(0L);
        try {
            if (userMapper.insert(user) != 1) {
                throw new AgentDomainException("Agent login identity creation failed");
            }
        } finally {
            user.setPassword(null);
        }
        return user;
    }

    private String generateUsername(String agentNo, Long agentId) {
        String normalized = agentNo.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        if (normalized.isBlank() || !Character.isLetter(normalized.charAt(0))) {
            normalized = "a_" + normalized;
        }
        normalized = normalized.substring(0, Math.min(normalized.length(), 44));
        String suffix = Long.toUnsignedString(agentId, 36);
        suffix = suffix.substring(Math.max(0, suffix.length() - 12));
        return "ag_" + normalized + '_' + suffix;
    }

    private String departmentName(String agentNo, Long agentId) {
        String suffix = Long.toUnsignedString(agentId, 36);
        suffix = suffix.substring(Math.max(0, suffix.length() - 8));
        String prefix = agentNo.substring(0, Math.min(agentNo.length(), 17));
        return "AG-" + prefix + '-' + suffix;
    }

    private String nickname(String name) {
        String value = name.replaceAll("[^\\p{IsHan}A-Za-z0-9_-]", "-");
        value = value.substring(0, Math.min(value.length(), 30));
        return value.length() >= 2 ? value : "AG-" + value;
    }

    private void requireTenantContext(Long tenantId) {
        if (tenantId == null || !tenantId.equals(TenantContextHolder.getTenantId())) {
            throw new AgentAccessDeniedException();
        }
    }

    private record ParentDepartment(Long id, String ancestors, Integer status) {
    }
}
