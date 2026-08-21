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
import top.continew.admin.merchant.agent.domain.AgentDomainException;
import top.continew.admin.merchant.agent.domain.AgentPricingBoundaryException;
import top.continew.admin.merchant.agent.domain.AgentPricingStatus;
import top.continew.admin.merchant.agent.domain.AgentPricingVersion;
import top.continew.admin.merchant.security.audit.application.SecurityAuditWriter;
import top.continew.admin.merchant.security.audit.domain.SecurityAuditRecord;
import top.continew.admin.merchant.security.audit.domain.SecurityAuditResult;
import top.continew.starter.extension.tenant.context.TenantContextHolder;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** Publishes immutable pricing versions after resolving and enforcing the parent boundary server-side. */
@Service
@RequiredArgsConstructor
public class AgentPricingService {

    private final AgentRepository agentRepository;
    private final AgentPricingRepository pricingRepository;
    private final AgentScopeAuthorizationService scopeAuthorizationService;
    private final SecurityAuditWriter securityAuditWriter;
    private final Clock clock = Clock.systemDefaultZone();

    @Transactional
    public AgentPricingVersion create(AgentPricingCreateCommand command) {
        requireTenantContext(command.tenantId());
        AgentScopeAuthorizationService.AgentScope scope = scopeAuthorizationService.requireAccessible(command
            .tenantId(), command.actorUserId(), command.agentId());
        Agent target = scope.target();
        if (!target.isEnabled()) {
            throw new AgentDomainException("Disabled agent cannot receive a new pricing version");
        }
        AgentPricingVersion parentVersion = resolveParentVersion(command, target);
        if (parentVersion != null) {
            command.rules().requireWithin(parentVersion.rules());
        }
        AgentPricingVersion previousVersion = pricingRepository.findEffective(command.tenantId(), target.id(), command
            .channelCode(), command.productCode(), command.currency(), command.effectiveTime()).orElse(null);
        int versionNo = pricingRepository.nextVersionNo(command.tenantId(), target.id(), command.channelCode(), command
            .productCode(), command.currency());
        AgentPricingVersion created = pricingRepository.insert(new AgentPricingVersionDraft(command.tenantId(), target
            .id(), parentVersion == null ? null : parentVersion.id(), versionNo, command.channelCode(), command
                .productCode(), command.currency(), command.rules(), command.effectiveTime(), command
                    .expiresTime(), AgentPricingStatus.PUBLISHED, command.actorUserId(), LocalDateTime.now(clock)));
        audit(command, scope.actor().id(), previousVersion, created);
        return created;
    }

    public List<AgentPricingVersion> list(Long tenantId,
                                          Long actorUserId,
                                          Long agentId,
                                          String channelCode,
                                          String productCode,
                                          String currency) {
        requireTenantContext(tenantId);
        scopeAuthorizationService.requireAccessible(tenantId, actorUserId, agentId);
        return pricingRepository.list(tenantId, agentId, AgentPricingCreateCommand
            .normalizeCode(channelCode, "channelCode"), AgentPricingCreateCommand
                .normalizeCode(productCode, "productCode"), AgentPricingCreateCommand.normalizeCurrency(currency));
    }

    private AgentPricingVersion resolveParentVersion(AgentPricingCreateCommand command, Agent target) {
        if (target.parentId() == 0) {
            return null;
        }
        Agent parent = agentRepository.findById(command.tenantId(), target.parentId())
            .filter(Agent::isEnabled)
            .orElseThrow(() -> new AgentPricingBoundaryException("Parent agent is not available"));
        return pricingRepository.findEffective(command.tenantId(), parent.id(), command.channelCode(), command
            .productCode(), command.currency(), command.effectiveTime())
            .orElseThrow(() -> new AgentPricingBoundaryException("Parent has no effective pricing version"));
    }

    private void audit(AgentPricingCreateCommand command,
                       Long actorAgentId,
                       AgentPricingVersion previous,
                       AgentPricingVersion created) {
        String previousId = previous == null ? "none" : previous.id().toString();
        String previousRules = previous == null ? "none" : previous.rules().auditSummary();
        String reason = "previousId=%s;currentId=%s;rules=%s->%s;effective=%s;reason=%s".formatted(previousId, created
            .id(), previousRules, created.rules().auditSummary(), created.effectiveTime(), command.reason());
        securityAuditWriter.append(new SecurityAuditRecord(command.tenantId(), command
            .actorUserId(), actorAgentId, "AGENT_PRICING_VERSION_CREATE", "AGENT_PRICING", created.id(), created
                .versionNo()
                .longValue(), "PRICING_RULES", truncate(reason), command
                    .ipAddress(), SecurityAuditResult.SUCCESS, null, LocalDateTime.now(clock)));
    }

    private String truncate(String value) {
        return value.substring(0, Math.min(value.length(), 255));
    }

    private void requireTenantContext(Long tenantId) {
        if (tenantId == null || !tenantId.equals(TenantContextHolder.getTenantId())) {
            throw new AgentPricingBoundaryException("Pricing tenant scope is not available");
        }
    }
}
