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
import top.continew.admin.merchant.agent.domain.Agent;
import top.continew.admin.merchant.agent.domain.AgentAccessDeniedException;
import top.continew.admin.merchant.security.audit.application.SecurityAuditWriter;
import top.continew.admin.merchant.security.audit.domain.SecurityAuditRecord;
import top.continew.admin.merchant.security.audit.domain.SecurityAuditResult;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** Scope-aware agent list and detail application service. */
@Service
@RequiredArgsConstructor
public class AgentQueryService {

    private final AgentRepository agentRepository;
    private final AgentScopeAuthorizationService scopeAuthorizationService;
    private final SecurityAuditWriter securityAuditWriter;
    private final Clock clock = Clock.systemDefaultZone();

    public AgentPage page(Long tenantId, Long actorUserId, AgentListQuery query) {
        List<Long> authorizedIds;
        try {
            authorizedIds = scopeAuthorizationService.listAuthorizedAgentIds(tenantId, actorUserId);
        } catch (AgentAccessDeniedException ex) {
            auditDenied(tenantId, actorUserId, query.agentId(), "AGENT_LIST_SCOPE_DENIED", query.ipAddress());
            return AgentPage.empty(query.page(), query.size());
        }
        if (query.agentId() != null && !authorizedIds.contains(query.agentId())) {
            auditDenied(tenantId, actorUserId, query.agentId(), "AGENT_LIST_FILTER_DENIED", query.ipAddress());
            return AgentPage.empty(query.page(), query.size());
        }
        return agentRepository.page(tenantId, authorizedIds, query);
    }

    public AgentSummary get(Long tenantId, Long actorUserId, Long agentId, String ipAddress) {
        try {
            Agent agent = scopeAuthorizationService.requireAccessible(tenantId, actorUserId, agentId).target();
            return AgentSummary.from(agent);
        } catch (AgentAccessDeniedException ex) {
            auditDenied(tenantId, actorUserId, agentId, "AGENT_DETAIL_DENIED", ipAddress);
            throw ex;
        }
    }

    private void auditDenied(Long tenantId,
                             Long actorUserId,
                             Long targetAgentId,
                             String failureCode,
                             String ipAddress) {
        Long actorAgentId = agentRepository.findByUserId(tenantId, actorUserId).map(Agent::id).orElse(null);
        Long objectId = targetAgentId != null && targetAgentId > 0 ? targetAgentId : actorUserId;
        securityAuditWriter
            .append(new SecurityAuditRecord(tenantId, actorUserId, actorAgentId, "AGENT_READ_DENIED", "AGENT", objectId, null, null, null, ipAddress, SecurityAuditResult.DENIED, failureCode, LocalDateTime
                .now(clock)));
    }
}
