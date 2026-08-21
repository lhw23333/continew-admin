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

package top.continew.admin.merchant.agent.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.merchant.agent.domain.Agent;
import top.continew.admin.merchant.agent.domain.AgentConcurrentModificationException;
import top.continew.admin.merchant.agent.domain.AgentPromotionCodeStatus;
import top.continew.admin.merchant.agent.domain.PromotionOwnershipDeniedException;
import top.continew.admin.merchant.security.audit.application.SecurityAuditWriter;
import top.continew.admin.merchant.security.audit.domain.SecurityAuditRecord;
import top.continew.admin.merchant.security.audit.domain.SecurityAuditResult;
import top.continew.starter.extension.tenant.context.TenantContextHolder;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;

/** Generates unique promotion references and resolves immutable merchant-registration ownership. */
@Service
@RequiredArgsConstructor
public class AgentPromotionCodeService {

    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int CODE_LENGTH = 12;

    private final AgentRepository agentRepository;
    private final AgentScopeAuthorizationService scopeAuthorizationService;
    private final SecurityAuditWriter securityAuditWriter;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Clock clock = Clock.systemDefaultZone();

    public String generateUniqueCodeForProvisioning(Long tenantId) {
        requireTenantContext(tenantId);
        for (int attempt = 0; attempt < 32; attempt++) {
            String code = randomCode();
            if (!agentRepository.existsByPromotionCode(tenantId, code)) {
                return code;
            }
        }
        throw new IllegalStateException("Unique agent promotion code generation failed");
    }

    @Transactional
    public AgentPromotionCodeView issue(Long tenantId,
                                        Long actorUserId,
                                        Long agentId,
                                        Long expectedVersion,
                                        String ipAddress) {
        AgentScopeAuthorizationService.AgentScope scope = scopeAuthorizationService
            .requireAccessible(tenantId, actorUserId, agentId);
        Agent current = scope.target();
        if (current.promotionCode() != null) {
            return toView(current);
        }
        if (!current.rowVersion().equals(expectedVersion)) {
            throw new AgentConcurrentModificationException();
        }
        Agent changed = current.assignPromotionCode(generateUniqueCodeForProvisioning(tenantId), LocalDateTime
            .now(clock));
        if (!agentRepository.updatePromotionCode(changed, expectedVersion)) {
            throw new AgentConcurrentModificationException();
        }
        audit(tenantId, actorUserId, scope.actor()
            .id(), changed, "AGENT_PROMOTION_CODE_ISSUE", "promotion code assigned; status UNASSIGNED -> ACTIVE", ipAddress);
        return toView(changed);
    }

    @Transactional
    public AgentPromotionCodeView changeStatus(Long tenantId,
                                               Long actorUserId,
                                               Long agentId,
                                               AgentPromotionCodeStatus status,
                                               Long expectedVersion,
                                               String ipAddress) {
        AgentScopeAuthorizationService.AgentScope scope = scopeAuthorizationService
            .requireAccessible(tenantId, actorUserId, agentId);
        Agent current = scope.target();
        if (!current.rowVersion().equals(expectedVersion)) {
            throw new AgentConcurrentModificationException();
        }
        Agent changed = current.changePromotionCodeStatus(status, LocalDateTime.now(clock));
        if (!agentRepository.updatePromotionCode(changed, expectedVersion)) {
            throw new AgentConcurrentModificationException();
        }
        audit(tenantId, actorUserId, scope.actor()
            .id(), changed, "AGENT_PROMOTION_CODE_STATUS", "promotion code status %s -> %s".formatted(current
                .promotionCodeStatus(), changed.promotionCodeStatus()), ipAddress);
        return toView(changed);
    }

    public PromotionOwnership resolveOwnership(Long tenantId, String promotionCode, Long requestedAgentId) {
        requireTenantContext(tenantId);
        String normalizedCode = promotionCode == null ? "" : promotionCode.trim().toUpperCase(Locale.ROOT);
        Agent agent = agentRepository.findByPromotionCode(tenantId, normalizedCode)
            .filter(Agent::isEnabled)
            .filter(item -> AgentPromotionCodeStatus.ACTIVE.equals(item.promotionCodeStatus()))
            .orElseThrow(PromotionOwnershipDeniedException::new);
        if (requestedAgentId != null && !requestedAgentId.equals(agent.id())) {
            throw new PromotionOwnershipDeniedException();
        }
        return new PromotionOwnership(agent.id(), agent.name(), agent.promotionCode());
    }

    private void audit(Long tenantId,
                       Long actorUserId,
                       Long actorAgentId,
                       Agent target,
                       String action,
                       String reason,
                       String ipAddress) {
        securityAuditWriter.append(new SecurityAuditRecord(tenantId, actorUserId, actorAgentId, action, "AGENT", target
            .id(), target
                .rowVersion(), "PROMOTION_CODE", reason, ipAddress, SecurityAuditResult.SUCCESS, null, LocalDateTime
                    .now(clock)));
    }

    private AgentPromotionCodeView toView(Agent agent) {
        return new AgentPromotionCodeView(agent.id(), agent.name(), agent.promotionCode(), agent
            .promotionCodeStatus(), agent.rowVersion());
    }

    private String randomCode() {
        char[] result = new char[CODE_LENGTH];
        for (int index = 0; index < result.length; index++) {
            result[index] = ALPHABET[secureRandom.nextInt(ALPHABET.length)];
        }
        return new String(result);
    }

    private void requireTenantContext(Long tenantId) {
        if (tenantId == null || !tenantId.equals(TenantContextHolder.getTenantId())) {
            throw new PromotionOwnershipDeniedException();
        }
    }
}
