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
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.common.enums.DisEnableStatusEnum;
import top.continew.admin.common.enums.GenderEnum;
import top.continew.admin.merchant.agent.application.AgentScopeAuthorizationService;
import top.continew.admin.merchant.agent.domain.Agent;
import top.continew.admin.merchant.master.application.MerchantMasterService;
import top.continew.admin.merchant.master.domain.Merchant;
import top.continew.admin.merchant.master.domain.MerchantDomainException;
import top.continew.admin.merchant.master.domain.MerchantRegistration;
import top.continew.admin.merchant.security.audit.application.SecurityAuditWriter;
import top.continew.admin.merchant.security.audit.domain.SecurityAuditRecord;
import top.continew.admin.merchant.security.audit.domain.SecurityAuditResult;
import top.continew.admin.merchant.security.crypto.SensitiveValueProtector;
import top.continew.admin.merchant.security.value.EncryptedMobileNumber;
import top.continew.admin.system.mapper.UserRoleMapper;
import top.continew.admin.system.mapper.user.UserMapper;
import top.continew.admin.system.model.entity.UserRoleDO;
import top.continew.admin.system.model.entity.user.UserDO;
import top.continew.starter.extension.tenant.context.TenantContextHolder;

import java.time.LocalDateTime;
import java.util.Locale;

/** Atomically provisions merchant master, distinct operator/reviewer users, roles, and encrypted mobile values. */
@Service
@RequiredArgsConstructor
public class MerchantProvisioningService {

    public static final String OPERATOR_ROLE_CODE = "MERCHANT_OPERATOR";
    public static final String REVIEWER_ROLE_CODE = "MERCHANT_REVIEWER";
    public static final String PASSWORD_CHANGE_REQUIRED = "PASSWORD_CHANGE_REQUIRED";

    private final AgentScopeAuthorizationService agentScopeAuthorizationService;
    private final MerchantMasterService merchantMasterService;
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final JdbcTemplate jdbcTemplate;
    private final IdentifierGenerator identifierGenerator;
    private final SensitiveValueProtector sensitiveValueProtector;
    private final SecurityAuditWriter securityAuditWriter;

    @Transactional(rollbackFor = Exception.class)
    public MerchantProvisioningResult create(MerchantCreateCommand command) {
        requireTenantContext(command.tenantId());
        AgentScopeAuthorizationService.AgentScope scope = agentScopeAuthorizationService.requireAccessible(command
            .tenantId(), command.actorUserId(), command.owningAgentId());
        Agent owningAgent = scope.target();
        if (!owningAgent.isEnabled() || owningAgent.deptId() == null) {
            throw new MerchantDomainException("Owning agent identity mapping is unavailable");
        }

        Long operatorRoleId = loadRoleId(OPERATOR_ROLE_CODE);
        Long reviewerRoleId = loadRoleId(REVIEWER_ROLE_CODE);
        if (operatorRoleId == null || reviewerRoleId == null || operatorRoleId.equals(reviewerRoleId)) {
            throw new MerchantDomainException("Merchant identity roles are unavailable");
        }

        Long merchantId = nextId();
        Long operatorUserId = nextId();
        Long reviewerUserId = nextId();
        String merchantNo = "M" + Long.toUnsignedString(merchantId, 36).toUpperCase(Locale.ROOT);
        String operatorUsername = username("mo", merchantId);
        String reviewerUsername = username("mr", merchantId);

        UserDO operator = createUser(operatorUserId, operatorUsername, nickname(command.shortName(), "操作员"), command
            .operatorTemporaryPassword(), owningAgent.deptId(), command
                .actorUserId(), "Merchant operator requires first-login password change");
        UserDO reviewer = createUser(reviewerUserId, reviewerUsername, nickname(command.shortName(), "复核员"), command
            .reviewerTemporaryPassword(), owningAgent.deptId(), command
                .actorUserId(), "Merchant reviewer requires first-login password change");
        bindRole(operator.getId(), operatorRoleId);
        bindRole(reviewer.getId(), reviewerRoleId);

        EncryptedMobileNumber contactMobile = EncryptedMobileNumber.fromPlaintext(command
            .contactMobile(), sensitiveValueProtector);
        EncryptedMobileNumber reviewerMobile = EncryptedMobileNumber.fromPlaintext(command
            .reviewerMobile(), sensitiveValueProtector);
        SensitiveValueProtector.ProtectedData legalIdentifier = protectLegalIdentifier(command.legalIdentifier());
        Merchant merchant = merchantMasterService.register(command
            .actorUserId(), new MerchantRegistration(merchantId, command.tenantId(), owningAgent
                .id(), merchantNo, command.merchantType(), command.legalName(), command.shortName(), legalIdentifier
                    .normalizedHash(), operatorUserId, reviewerUserId, reviewerMobile, command
                        .contactName(), contactMobile, command.industry(), command.productDescription()));
        audit(command, scope.actor().id(), merchant);
        return new MerchantProvisioningResult(merchant.id(), merchant
            .merchantNo(), operatorUserId, operatorUsername, reviewerUserId, reviewerUsername, PASSWORD_CHANGE_REQUIRED);
    }

    private SensitiveValueProtector.ProtectedData protectLegalIdentifier(String raw) {
        String normalized = raw.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9]{8,64}")) {
            throw new MerchantDomainException("Legal subject identifier is invalid");
        }
        String masked = normalized.length() <= 8
            ? "****"
            : normalized.substring(0, 4) + "****" + normalized.substring(normalized.length() - 4);
        return sensitiveValueProtector.protect("LEGAL_SUBJECT_IDENTIFIER", normalized, masked);
    }

    private UserDO createUser(Long userId,
                              String username,
                              String nickname,
                              String temporaryPassword,
                              Long deptId,
                              Long actorUserId,
                              String description) {
        if (userMapper.selectByUsername(username) != null) {
            throw new MerchantDomainException("Generated merchant username already exists");
        }
        UserDO user = new UserDO();
        user.setId(userId);
        user.setUsername(username);
        user.setNickname(nickname);
        user.setPassword(temporaryPassword);
        user.setGender(GenderEnum.UNKNOWN);
        user.setDeptId(deptId);
        user.setDescription(description);
        user.setStatus(DisEnableStatusEnum.ENABLE);
        user.setIsSystem(false);
        user.setPwdResetTime(LocalDateTime.now());
        user.setMustChangePassword(true);
        user.setCreateUser(actorUserId);
        user.setCreateTime(LocalDateTime.now());
        user.setDeleted(0L);
        try {
            if (userMapper.insert(user) != 1) {
                throw new MerchantDomainException("Merchant login identity creation failed");
            }
        } finally {
            user.setPassword(null);
        }
        return user;
    }

    private void bindRole(Long userId, Long roleId) {
        if (userRoleMapper.insert(new UserRoleDO(userId, roleId)) != 1) {
            throw new MerchantDomainException("Merchant role binding failed");
        }
    }

    private Long loadRoleId(String roleCode) {
        try {
            return jdbcTemplate
                .queryForObject("SELECT id FROM sys_role WHERE code = ? AND deleted = 0", Long.class, roleCode);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    private Long nextId() {
        return identifierGenerator.nextId(new Object()).longValue();
    }

    private String username(String prefix, Long merchantId) {
        return prefix + '_' + Long.toUnsignedString(merchantId, 36).toLowerCase(Locale.ROOT);
    }

    private String nickname(String shortName, String suffix) {
        String normalized = shortName.replaceAll("[^\\p{IsHan}A-Za-z0-9_-]", "-");
        String value = normalized + suffix;
        return value.substring(0, Math.min(value.length(), 30));
    }

    private void audit(MerchantCreateCommand command, Long actorAgentId, Merchant merchant) {
        String reason = "merchant created; owningAgentId=%s; operatorUserId=%s; reviewerUserId=%s".formatted(merchant
            .owningAgentId(), merchant.operatorUserId(), merchant.reviewerUserId());
        securityAuditWriter.append(new SecurityAuditRecord(command.tenantId(), command
            .actorUserId(), actorAgentId, "MERCHANT_CREATE", "MERCHANT", merchant.id(), merchant
                .rowVersion(), null, reason, command.ipAddress(), SecurityAuditResult.SUCCESS, null, LocalDateTime
                    .now()));
    }

    private void requireTenantContext(Long tenantId) {
        if (tenantId == null || !tenantId.equals(TenantContextHolder.getTenantId())) {
            throw new MerchantDomainException("Merchant tenant scope is not available");
        }
    }
}
