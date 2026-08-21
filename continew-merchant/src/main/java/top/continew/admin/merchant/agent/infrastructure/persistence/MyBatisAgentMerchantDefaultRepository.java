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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import top.continew.admin.merchant.agent.application.AgentMerchantDefaultRepository;
import top.continew.admin.merchant.agent.application.AgentMerchantDefaultVersionDraft;
import top.continew.admin.merchant.agent.domain.AgentDomainException;
import top.continew.admin.merchant.agent.domain.AgentMerchantDefaultConflictException;
import top.continew.admin.merchant.agent.domain.AgentMerchantDefaultStatus;
import top.continew.admin.merchant.agent.domain.AgentMerchantDefaultVersion;
import top.continew.admin.merchant.agent.domain.AgentMerchantDefaults;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** MyBatis append-only repository for agent merchant defaults. */
@Repository
@RequiredArgsConstructor
public class MyBatisAgentMerchantDefaultRepository implements AgentMerchantDefaultRepository {

    private final AgentMerchantDefaultVersionMapper mapper;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<AgentMerchantDefaultVersion> findEffective(Long tenantId, Long agentId, LocalDateTime effectiveAt) {
        return Optional.ofNullable(mapper.lambdaQuery()
            .eq(AgentMerchantDefaultVersionDO::getTenantId, tenantId)
            .eq(AgentMerchantDefaultVersionDO::getAgentId, agentId)
            .eq(AgentMerchantDefaultVersionDO::getStatus, AgentMerchantDefaultStatus.PUBLISHED)
            .le(AgentMerchantDefaultVersionDO::getEffectiveTime, effectiveAt)
            .and(wrapper -> wrapper.isNull(AgentMerchantDefaultVersionDO::getExpiresTime)
                .or()
                .gt(AgentMerchantDefaultVersionDO::getExpiresTime, effectiveAt))
            .eq(AgentMerchantDefaultVersionDO::getDeleted, 0L)
            .orderByDesc(AgentMerchantDefaultVersionDO::getEffectiveTime)
            .orderByDesc(AgentMerchantDefaultVersionDO::getVersionNo)
            .orderByDesc(AgentMerchantDefaultVersionDO::getId)
            .last("LIMIT 1")
            .one()).map(this::toDomain);
    }

    @Override
    public List<AgentMerchantDefaultVersion> list(Long tenantId, Long agentId) {
        return mapper.lambdaQuery()
            .eq(AgentMerchantDefaultVersionDO::getTenantId, tenantId)
            .eq(AgentMerchantDefaultVersionDO::getAgentId, agentId)
            .eq(AgentMerchantDefaultVersionDO::getDeleted, 0L)
            .orderByDesc(AgentMerchantDefaultVersionDO::getVersionNo)
            .orderByDesc(AgentMerchantDefaultVersionDO::getId)
            .list()
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public int nextVersionNo(Long tenantId, Long agentId) {
        AgentMerchantDefaultVersionDO latest = mapper.lambdaQuery()
            .select(AgentMerchantDefaultVersionDO::getVersionNo)
            .eq(AgentMerchantDefaultVersionDO::getTenantId, tenantId)
            .eq(AgentMerchantDefaultVersionDO::getAgentId, agentId)
            .eq(AgentMerchantDefaultVersionDO::getDeleted, 0L)
            .orderByDesc(AgentMerchantDefaultVersionDO::getVersionNo)
            .last("LIMIT 1")
            .one();
        return latest == null ? 1 : latest.getVersionNo() + 1;
    }

    @Override
    public AgentMerchantDefaultVersion insert(AgentMerchantDefaultVersionDraft draft) {
        AgentMerchantDefaultVersionDO dataObject = new AgentMerchantDefaultVersionDO();
        dataObject.setTenantId(draft.tenantId());
        dataObject.setAgentId(draft.agentId());
        dataObject.setVersionNo(draft.versionNo());
        dataObject.setDefaultPayloadJson(writeDefaults(draft.defaults()));
        dataObject.setEffectiveTime(draft.effectiveTime());
        dataObject.setExpiresTime(draft.expiresTime());
        dataObject.setStatus(draft.status());
        dataObject.setCreateUser(draft.createUser());
        dataObject.setCreateTime(draft.createTime());
        dataObject.setDeleted(0L);
        try {
            if (mapper.insert(dataObject) != 1) {
                throw new AgentDomainException("Agent merchant-default persistence failed");
            }
        } catch (DataIntegrityViolationException ex) {
            throw new AgentMerchantDefaultConflictException();
        }
        return toDomain(dataObject);
    }

    private AgentMerchantDefaultVersion toDomain(AgentMerchantDefaultVersionDO dataObject) {
        return new AgentMerchantDefaultVersion(dataObject.getId(), dataObject.getTenantId(), dataObject
            .getAgentId(), dataObject.getVersionNo(), readDefaults(dataObject.getDefaultPayloadJson()), dataObject
                .getEffectiveTime(), dataObject.getExpiresTime(), dataObject.getStatus(), dataObject
                    .getCreateUser(), dataObject.getCreateTime());
    }

    private String writeDefaults(AgentMerchantDefaults defaults) {
        try {
            return objectMapper.writeValueAsString(defaults);
        } catch (JsonProcessingException ex) {
            throw new AgentDomainException("Agent merchant-default serialization failed");
        }
    }

    private AgentMerchantDefaults readDefaults(String json) {
        try {
            return objectMapper.readValue(json, AgentMerchantDefaults.class);
        } catch (JsonProcessingException ex) {
            throw new AgentDomainException("Stored agent merchant defaults are invalid");
        }
    }
}
