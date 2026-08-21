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

package top.continew.admin.merchant.agent.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import top.continew.admin.merchant.agent.application.AgentClosureRepository;
import top.continew.admin.merchant.agent.domain.AgentClosureLink;
import top.continew.admin.merchant.agent.domain.AgentDomainException;

import java.util.List;

/** MyBatis closure repository with tenant-explicit queries. */
@Repository
@RequiredArgsConstructor
public class MyBatisAgentClosureRepository implements AgentClosureRepository {

    private final AgentClosureMapper mapper;

    @Override
    public List<AgentClosureLink> findAncestors(Long tenantId, Long descendantId) {
        return mapper.lambdaQuery()
            .eq(AgentClosureDO::getTenantId, tenantId)
            .eq(AgentClosureDO::getDescendantId, descendantId)
            .orderByAsc(AgentClosureDO::getDepth)
            .list()
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public List<Long> findDescendantIds(Long tenantId, Long ancestorId) {
        return mapper.lambdaQuery()
            .select(AgentClosureDO::getDescendantId)
            .eq(AgentClosureDO::getTenantId, tenantId)
            .eq(AgentClosureDO::getAncestorId, ancestorId)
            .orderByAsc(AgentClosureDO::getDepth)
            .orderByAsc(AgentClosureDO::getDescendantId)
            .list()
            .stream()
            .map(AgentClosureDO::getDescendantId)
            .toList();
    }

    @Override
    public boolean contains(Long tenantId, Long ancestorId, Long descendantId) {
        return mapper.lambdaQuery()
            .eq(AgentClosureDO::getTenantId, tenantId)
            .eq(AgentClosureDO::getAncestorId, ancestorId)
            .eq(AgentClosureDO::getDescendantId, descendantId)
            .exists();
    }

    @Override
    public void insertAll(List<AgentClosureLink> links) {
        for (AgentClosureLink link : links) {
            AgentClosureDO dataObject = new AgentClosureDO();
            dataObject.setTenantId(link.tenantId());
            dataObject.setAncestorId(link.ancestorId());
            dataObject.setDescendantId(link.descendantId());
            dataObject.setDepth(link.depth());
            dataObject.setCreateTime(link.createTime());
            if (mapper.insert(dataObject) != 1) {
                throw new AgentDomainException("Agent hierarchy persistence failed");
            }
        }
    }

    private AgentClosureLink toDomain(AgentClosureDO dataObject) {
        return new AgentClosureLink(dataObject.getTenantId(), dataObject.getAncestorId(), dataObject
            .getDescendantId(), dataObject.getDepth(), dataObject.getCreateTime());
    }
}
