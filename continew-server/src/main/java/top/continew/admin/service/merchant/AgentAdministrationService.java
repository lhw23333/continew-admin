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
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import top.continew.admin.auth.service.OnlineUserService;
import top.continew.admin.common.enums.DisEnableStatusEnum;
import top.continew.admin.merchant.agent.application.AgentHierarchyService;
import top.continew.admin.merchant.agent.application.AgentRepository;
import top.continew.admin.merchant.agent.application.AgentScopeAuthorizationService;
import top.continew.admin.merchant.agent.application.AgentSummary;
import top.continew.admin.merchant.agent.domain.Agent;
import top.continew.admin.merchant.agent.domain.AgentAccessDeniedException;
import top.continew.admin.merchant.agent.domain.AgentConcurrentModificationException;
import top.continew.admin.merchant.agent.domain.AgentDomainException;
import top.continew.admin.merchant.agent.domain.AgentStatus;
import top.continew.admin.merchant.security.audit.application.SecurityAuditWriter;
import top.continew.admin.merchant.security.audit.domain.SecurityAuditRecord;
import top.continew.admin.merchant.security.audit.domain.SecurityAuditResult;
import top.continew.admin.merchant.security.crypto.SensitiveValueProtector;
import top.continew.admin.merchant.security.value.EncryptedMobileNumber;
import top.continew.admin.system.mapper.user.UserMapper;
import top.continew.admin.system.model.entity.user.UserDO;

import java.time.Clock;
import java.time.LocalDateTime;

/** Coordinates safe profile, lifecycle, and credential administration for scoped subordinate agents. */
@Service
@RequiredArgsConstructor
public class AgentAdministrationService {

    private final AgentRepository agentRepository;
    private final AgentScopeAuthorizationService scopeAuthorizationService;
    private final AgentHierarchyService agentHierarchyService;
    private final UserMapper userMapper;
    private final OnlineUserService onlineUserService;
    private final SensitiveValueProtector sensitiveValueProtector;
    private final SecurityAuditWriter securityAuditWriter;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock = Clock.systemDefaultZone();

    public AgentSummary updateProfile(Long tenantId,
                                      Long actorUserId,
                                      Long agentId,
                                      String name,
                                      String contactName,
                                      String contactMobile,
                                      String remarks,
                                      Long expectedVersion,
                                      String ipAddress) {
        AgentScopeAuthorizationService.AgentScope scope = scopeAuthorizationService
            .requireAccessible(tenantId, actorUserId, agentId);
        Agent current = scope.target();
        if (!current.rowVersion().equals(expectedVersion)) {
            throw new AgentConcurrentModificationException();
        }
        EncryptedMobileNumber mobile = contactMobile == null || contactMobile.isBlank()
            ? current.contactMobile()
            : EncryptedMobileNumber.fromPlaintext(contactMobile, sensitiveValueProtector);
        Agent changed = current.updateProfile(name, contactName, mobile, remarks, LocalDateTime.now(clock));
        transactionTemplate.executeWithoutResult(status -> {
            if (!agentRepository.updateProfile(changed, expectedVersion)) {
                throw new AgentConcurrentModificationException();
            }
            userMapper.lambdaUpdate()
                .eq(UserDO::getId, current.userId())
                .set(UserDO::getNickname, nickname(name))
                .update();
        });
        audit(tenantId, actorUserId, scope.actor()
            .id(), changed, "AGENT_PROFILE_UPDATE", maskedProfileReason(current, changed), null, ipAddress);
        return AgentSummary.from(changed);
    }

    public AgentSummary changeLifecycle(Long tenantId,
                                        Long actorUserId,
                                        Long agentId,
                                        AgentStatus status,
                                        String reason,
                                        Long expectedVersion,
                                        String ipAddress) {
        AgentScopeAuthorizationService.AgentScope scope = scopeAuthorizationService
            .requireAccessible(tenantId, actorUserId, agentId);
        Agent changed = transactionTemplate.execute(transactionStatus -> {
            Agent lifecycleChanged = agentHierarchyService
                .changeLifecycle(tenantId, actorUserId, agentId, status, reason, expectedVersion);
            boolean updated = userMapper.lambdaUpdate()
                .eq(UserDO::getId, lifecycleChanged.userId())
                .set(UserDO::getStatus, AgentStatus.ENABLED.equals(status)
                    ? DisEnableStatusEnum.ENABLE
                    : DisEnableStatusEnum.DISABLE)
                .update();
            if (!updated) {
                throw new AgentDomainException("Agent login identity lifecycle update failed");
            }
            return lifecycleChanged;
        });
        if (AgentStatus.DISABLED.equals(status)) {
            onlineUserService.kickOut(changed.userId());
        }
        audit(tenantId, actorUserId, scope.actor()
            .id(), changed, "AGENT_LIFECYCLE_CHANGE", sanitize(reason), null, ipAddress);
        return AgentSummary.from(changed);
    }

    public void resetTemporaryPassword(Long tenantId,
                                       Long actorUserId,
                                       Long agentId,
                                       String temporaryPassword,
                                       String reason,
                                       String ipAddress) {
        AgentScopeAuthorizationService.AgentScope scope = scopeAuthorizationService
            .requireAccessible(tenantId, actorUserId, agentId);
        Agent target = scope.target();
        if (scope.actor().id().equals(target.id())) {
            throw new AgentAccessDeniedException();
        }
        if (reason == null || reason.isBlank()) {
            throw new AgentDomainException("Password reset reason is required");
        }
        boolean updated = userMapper.lambdaUpdate()
            .eq(UserDO::getId, target.userId())
            .set(UserDO::getPassword, temporaryPassword)
            .set(UserDO::getPwdResetTime, LocalDateTime.now(clock))
            .set(UserDO::getMustChangePassword, true)
            .set(UserDO::getStatus, target.isEnabled() ? DisEnableStatusEnum.ENABLE : DisEnableStatusEnum.DISABLE)
            .update();
        temporaryPassword = null;
        if (!updated) {
            throw new AgentDomainException("Agent password reset failed");
        }
        onlineUserService.kickOut(target.userId());
        audit(tenantId, actorUserId, scope.actor()
            .id(), target, "AGENT_PASSWORD_RESET", sanitize(reason), null, ipAddress);
    }

    private void audit(Long tenantId,
                       Long actorUserId,
                       Long actorAgentId,
                       Agent target,
                       String action,
                       String reason,
                       String failureCode,
                       String ipAddress) {
        securityAuditWriter.append(new SecurityAuditRecord(tenantId, actorUserId, actorAgentId, action, "AGENT", target
            .id(), target.rowVersion(), null, reason, ipAddress, SecurityAuditResult.SUCCESS, failureCode, LocalDateTime
                .now(clock)));
    }

    private String maskedProfileReason(Agent before, Agent after) {
        String beforeMobile = before.contactMobile() == null ? "none" : before.contactMobile().maskedValue();
        String afterMobile = after.contactMobile() == null ? "none" : after.contactMobile().maskedValue();
        return "profile updated; mobile %s -> %s".formatted(beforeMobile, afterMobile);
    }

    private String sanitize(String value) {
        if (value == null) {
            return null;
        }
        String sanitized = value.replaceAll("[\\p{Cntrl}]", " ")
            .replaceAll("(?<!\\d)\\d{7,}(?!\\d)", "[REDACTED]")
            .replaceAll("\\s+", " ")
            .trim();
        return sanitized.substring(0, Math.min(sanitized.length(), 255));
    }

    private String nickname(String name) {
        String value = name.replaceAll("[^\\p{IsHan}A-Za-z0-9_-]", "-");
        value = value.substring(0, Math.min(value.length(), 30));
        return value.length() >= 2 ? value : "AG-" + value;
    }
}
