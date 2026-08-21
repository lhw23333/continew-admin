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
import top.continew.admin.merchant.agent.domain.AgentClosureLink;
import top.continew.admin.merchant.agent.domain.AgentConcurrentModificationException;
import top.continew.admin.merchant.agent.domain.AgentDomainException;
import top.continew.admin.merchant.agent.domain.AgentRegistration;
import top.continew.admin.merchant.agent.domain.AgentStatus;
import top.continew.starter.extension.tenant.context.TenantContextHolder;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Maintains agent aggregate and closure rows in one transaction. */
@Service
@RequiredArgsConstructor
public class AgentHierarchyService {

    private final AgentRepository agentRepository;
    private final AgentClosureRepository closureRepository;
    private final AgentScopeAuthorizationService scopeAuthorizationService;
    private final Clock clock = Clock.systemDefaultZone();

    @Transactional
    public Agent register(AgentRegistration registration) {
        requireTenantContext(registration.tenantId());
        if (agentRepository.existsById(registration.tenantId(), registration.id())) {
            throw new AgentDomainException("Agent ID already exists");
        }
        if (agentRepository.existsByAgentNo(registration.tenantId(), registration.agentNo())) {
            throw new AgentDomainException("Agent number already exists");
        }
        if (agentRepository.existsByUserId(registration.tenantId(), registration.userId())) {
            throw new AgentDomainException("User is already bound to an agent");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        Agent parent = null;
        String path;
        if (registration.parentId() == 0) {
            path = "/" + registration.id();
        } else {
            parent = agentRepository.findById(registration.tenantId(), registration.parentId())
                .orElseThrow(() -> new AgentDomainException("Parent agent does not exist"));
            if (!parent.isEnabled()) {
                throw new AgentDomainException("Parent agent is disabled");
            }
            path = parent.path() + "/" + registration.id();
        }
        Agent agent = Agent.create(registration, path, now);
        List<AgentClosureLink> links = buildClosureLinks(agent, parent, now);
        agentRepository.insert(agent);
        closureRepository.insertAll(links);
        return agent;
    }

    @Transactional
    public Agent changeLifecycle(Long tenantId,
                                 Long actorUserId,
                                 Long targetAgentId,
                                 AgentStatus status,
                                 String reason,
                                 Long expectedVersion) {
        requireTenantContext(tenantId);
        AgentScopeAuthorizationService.AgentScope scope = scopeAuthorizationService
            .requireAccessible(tenantId, actorUserId, targetAgentId);
        Agent current = scope.target();
        if (AgentStatus.DISABLED.equals(status) && scope.actor().id().equals(current.id())) {
            throw new AgentDomainException("An agent cannot disable itself through subordinate management");
        }
        if (!current.rowVersion().equals(expectedVersion)) {
            throw new AgentConcurrentModificationException();
        }
        Agent changed = current.changeStatus(status, reason, LocalDateTime.now(clock));
        if (!agentRepository.updateLifecycle(changed, expectedVersion)) {
            throw new AgentConcurrentModificationException();
        }
        return changed;
    }

    private List<AgentClosureLink> buildClosureLinks(Agent agent, Agent parent, LocalDateTime now) {
        List<AgentClosureLink> links = new ArrayList<>();
        if (parent != null) {
            List<AgentClosureLink> ancestors = closureRepository.findAncestors(agent.tenantId(), parent.id());
            if (ancestors.stream().noneMatch(link -> link.ancestorId().equals(parent.id()) && link.depth() == 0)) {
                throw new AgentDomainException("Parent hierarchy is inconsistent");
            }
            ancestors.forEach(link -> links.add(new AgentClosureLink(agent.tenantId(), link.ancestorId(), agent
                .id(), link.depth() + 1, now)));
        }
        links.add(new AgentClosureLink(agent.tenantId(), agent.id(), agent.id(), 0, now));
        return links;
    }

    private void requireTenantContext(Long tenantId) {
        if (tenantId == null || !tenantId.equals(TenantContextHolder.getTenantId())) {
            throw new top.continew.admin.merchant.agent.domain.AgentAccessDeniedException();
        }
    }
}
