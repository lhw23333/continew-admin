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
import top.continew.starter.extension.tenant.context.TenantContextHolder;

import java.util.List;

/** Enforces tenant plus agent-descendant scope without trusting client-provided paths or process variables. */
@Service
@RequiredArgsConstructor
public class AgentScopeAuthorizationService {

    private final AgentRepository agentRepository;
    private final AgentClosureRepository closureRepository;

    public AgentScope requireAccessible(Long tenantId, Long actorUserId, Long targetAgentId) {
        requireTenantContext(tenantId);
        Agent actor = resolveEnabledActor(tenantId, actorUserId);
        Agent target = agentRepository.findById(tenantId, targetAgentId).orElseThrow(AgentAccessDeniedException::new);
        if (!closureRepository.contains(tenantId, actor.id(), target.id())) {
            throw new AgentAccessDeniedException();
        }
        return new AgentScope(actor, target);
    }

    public boolean canAccess(Long tenantId, Long actorUserId, Long targetAgentId) {
        try {
            requireAccessible(tenantId, actorUserId, targetAgentId);
            return true;
        } catch (AgentAccessDeniedException ex) {
            return false;
        }
    }

    public List<Long> listAuthorizedAgentIds(Long tenantId, Long actorUserId) {
        requireTenantContext(tenantId);
        Agent actor = resolveEnabledActor(tenantId, actorUserId);
        return closureRepository.findDescendantIds(tenantId, actor.id());
    }

    private Agent resolveEnabledActor(Long tenantId, Long actorUserId) {
        Agent actor = agentRepository.findByUserId(tenantId, actorUserId).orElseThrow(AgentAccessDeniedException::new);
        if (!actor.isEnabled()) {
            throw new AgentAccessDeniedException();
        }
        return actor;
    }

    private void requireTenantContext(Long tenantId) {
        if (tenantId == null || !tenantId.equals(TenantContextHolder.getTenantId())) {
            throw new AgentAccessDeniedException();
        }
    }

    public record AgentScope(Agent actor, Agent target) {
    }
}
