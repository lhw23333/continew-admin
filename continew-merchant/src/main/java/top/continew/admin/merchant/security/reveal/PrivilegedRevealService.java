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

package top.continew.admin.merchant.security.reveal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.continew.admin.merchant.agent.application.AgentRepository;
import top.continew.admin.merchant.master.application.MerchantScopeAuthorizationService;
import top.continew.admin.merchant.master.domain.Merchant;
import top.continew.admin.merchant.master.domain.MerchantAccessDeniedException;
import top.continew.admin.merchant.security.audit.application.SecurityAuditWriter;
import top.continew.admin.merchant.security.audit.domain.SecurityAuditRecord;
import top.continew.admin.merchant.security.audit.domain.SecurityAuditResult;
import top.continew.admin.merchant.security.crypto.SensitiveValueProtector;

import java.time.Clock;
import java.time.LocalDateTime;

/** Applies permission, merchant scope, fresh authentication, decryption, and immutable audit in that order. */
@Service
@RequiredArgsConstructor
public class PrivilegedRevealService {

    private static final String ACTION = "SENSITIVE_REVEAL";
    private static final String OBJECT_TYPE = "MERCHANT";

    private final MerchantScopeAuthorizationService merchantScopeAuthorizationService;
    private final AgentRepository agentRepository;
    private final SensitiveRevealPermissionPort permissionPort;
    private final StepUpAuthenticationPort stepUpAuthenticationPort;
    private final SensitiveValueProtector sensitiveValueProtector;
    private final SecurityAuditWriter securityAuditWriter;
    private final Clock clock = Clock.systemDefaultZone();

    public PrivilegedRevealResult reveal(PrivilegedRevealCommand command) {
        Long actorAgentId = resolveActorAgentId(command);
        try {
            permissionPort.requireAllowed(command.actorUserId());
        } catch (RuntimeException ex) {
            audit(command, actorAgentId, null, SecurityAuditResult.DENIED, "PERMISSION_DENIED");
            throw new PrivilegedRevealDeniedException();
        }

        Merchant merchant;
        try {
            merchant = merchantScopeAuthorizationService.requireAccessible(command.tenantId(), command
                .actorUserId(), command.merchantId());
        } catch (MerchantAccessDeniedException ex) {
            audit(command, actorAgentId, null, SecurityAuditResult.DENIED, "SCOPE_DENIED");
            throw new PrivilegedRevealDeniedException();
        }

        boolean verified;
        try {
            verified = stepUpAuthenticationPort.verify(command.actorUserId(), command.encryptedPasswordProof(), command
                .ipAddress());
        } catch (RuntimeException ex) {
            verified = false;
        }
        if (!verified) {
            audit(command, actorAgentId, merchant.rowVersion(), SecurityAuditResult.DENIED, "STEP_UP_FAILED");
            throw new PrivilegedRevealDeniedException();
        }

        LocalDateTime revealedAt = LocalDateTime.now(clock);
        String value;
        try {
            value = command.field().reveal(merchant, sensitiveValueProtector);
        } catch (RuntimeException ex) {
            audit(command, actorAgentId, merchant.rowVersion(), SecurityAuditResult.FAILED, "FIELD_UNAVAILABLE");
            throw new PrivilegedRevealDeniedException();
        }
        audit(command, actorAgentId, merchant.rowVersion(), SecurityAuditResult.SUCCESS, null);
        return new PrivilegedRevealResult(command.field(), value, revealedAt);
    }

    public String maskedValue(Long tenantId, Long actorUserId, Long merchantId, MerchantSensitiveField field) {
        Merchant merchant = merchantScopeAuthorizationService.requireAccessible(tenantId, actorUserId, merchantId);
        return field.maskedValue(merchant);
    }

    private Long resolveActorAgentId(PrivilegedRevealCommand command) {
        try {
            return agentRepository.findByUserId(command.tenantId(), command.actorUserId())
                .map(agent -> agent.id())
                .orElse(null);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private void audit(PrivilegedRevealCommand command,
                       Long actorAgentId,
                       Long businessVersion,
                       SecurityAuditResult result,
                       String failureCode) {
        securityAuditWriter.append(new SecurityAuditRecord(command.tenantId(), command
            .actorUserId(), actorAgentId, ACTION, OBJECT_TYPE, command.merchantId(), businessVersion, command.field()
                .name(), sanitizeReason(command.reason()), command.ipAddress(), result, failureCode, LocalDateTime
                    .now(clock)));
    }

    private String sanitizeReason(String reason) {
        String sanitized = reason.replaceAll("[\\p{Cntrl}]", " ")
            .replaceAll("(?<!\\d)\\d{7,}(?!\\d)", "[REDACTED]")
            .replaceAll("\\s+", " ")
            .trim();
        return sanitized.length() <= 255 ? sanitized : sanitized.substring(0, 255);
    }
}
