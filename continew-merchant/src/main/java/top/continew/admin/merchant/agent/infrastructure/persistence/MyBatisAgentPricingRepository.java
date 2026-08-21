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
import top.continew.admin.merchant.agent.application.AgentPricingRepository;
import top.continew.admin.merchant.agent.application.AgentPricingVersionDraft;
import top.continew.admin.merchant.agent.domain.AgentDomainException;
import top.continew.admin.merchant.agent.domain.AgentPricingConflictException;
import top.continew.admin.merchant.agent.domain.AgentPricingRules;
import top.continew.admin.merchant.agent.domain.AgentPricingStatus;
import top.continew.admin.merchant.agent.domain.AgentPricingVersion;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** MyBatis append-only pricing repository with tenant and dimension predicates on every query. */
@Repository
@RequiredArgsConstructor
public class MyBatisAgentPricingRepository implements AgentPricingRepository {

    private final AgentPricingVersionMapper mapper;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<AgentPricingVersion> findById(Long tenantId, Long pricingVersionId) {
        return Optional.ofNullable(mapper.lambdaQuery()
            .eq(AgentPricingVersionDO::getTenantId, tenantId)
            .eq(AgentPricingVersionDO::getId, pricingVersionId)
            .eq(AgentPricingVersionDO::getDeleted, 0L)
            .one()).map(this::toDomain);
    }

    @Override
    public Optional<AgentPricingVersion> findEffective(Long tenantId,
                                                       Long agentId,
                                                       String channelCode,
                                                       String productCode,
                                                       String currency,
                                                       LocalDateTime effectiveAt) {
        return Optional.ofNullable(mapper.lambdaQuery()
            .eq(AgentPricingVersionDO::getTenantId, tenantId)
            .eq(AgentPricingVersionDO::getAgentId, agentId)
            .eq(AgentPricingVersionDO::getChannelCode, channelCode)
            .eq(AgentPricingVersionDO::getProductCode, productCode)
            .eq(AgentPricingVersionDO::getCurrency, currency)
            .eq(AgentPricingVersionDO::getStatus, AgentPricingStatus.PUBLISHED)
            .le(AgentPricingVersionDO::getEffectiveTime, effectiveAt)
            .and(wrapper -> wrapper.isNull(AgentPricingVersionDO::getExpiresTime)
                .or()
                .gt(AgentPricingVersionDO::getExpiresTime, effectiveAt))
            .eq(AgentPricingVersionDO::getDeleted, 0L)
            .orderByDesc(AgentPricingVersionDO::getEffectiveTime)
            .orderByDesc(AgentPricingVersionDO::getVersionNo)
            .orderByDesc(AgentPricingVersionDO::getId)
            .last("LIMIT 1")
            .one()).map(this::toDomain);
    }

    @Override
    public List<AgentPricingVersion> list(Long tenantId,
                                          Long agentId,
                                          String channelCode,
                                          String productCode,
                                          String currency) {
        return mapper.lambdaQuery()
            .eq(AgentPricingVersionDO::getTenantId, tenantId)
            .eq(AgentPricingVersionDO::getAgentId, agentId)
            .eq(AgentPricingVersionDO::getChannelCode, channelCode)
            .eq(AgentPricingVersionDO::getProductCode, productCode)
            .eq(AgentPricingVersionDO::getCurrency, currency)
            .eq(AgentPricingVersionDO::getDeleted, 0L)
            .orderByDesc(AgentPricingVersionDO::getVersionNo)
            .orderByDesc(AgentPricingVersionDO::getId)
            .list()
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public int nextVersionNo(Long tenantId, Long agentId, String channelCode, String productCode, String currency) {
        AgentPricingVersionDO latest = mapper.lambdaQuery()
            .select(AgentPricingVersionDO::getVersionNo)
            .eq(AgentPricingVersionDO::getTenantId, tenantId)
            .eq(AgentPricingVersionDO::getAgentId, agentId)
            .eq(AgentPricingVersionDO::getChannelCode, channelCode)
            .eq(AgentPricingVersionDO::getProductCode, productCode)
            .eq(AgentPricingVersionDO::getCurrency, currency)
            .eq(AgentPricingVersionDO::getDeleted, 0L)
            .orderByDesc(AgentPricingVersionDO::getVersionNo)
            .last("LIMIT 1")
            .one();
        return latest == null ? 1 : latest.getVersionNo() + 1;
    }

    @Override
    public AgentPricingVersion insert(AgentPricingVersionDraft draft) {
        AgentPricingVersionDO dataObject = new AgentPricingVersionDO();
        dataObject.setTenantId(draft.tenantId());
        dataObject.setAgentId(draft.agentId());
        dataObject.setParentPricingVersionId(draft.parentPricingVersionId());
        dataObject.setVersionNo(draft.versionNo());
        dataObject.setChannelCode(draft.channelCode());
        dataObject.setProductCode(draft.productCode());
        dataObject.setCurrency(draft.currency());
        dataObject.setPricingRulesJson(writeRules(draft.rules()));
        dataObject.setEffectiveTime(draft.effectiveTime());
        dataObject.setExpiresTime(draft.expiresTime());
        dataObject.setStatus(draft.status());
        dataObject.setCreateUser(draft.createUser());
        dataObject.setCreateTime(draft.createTime());
        dataObject.setDeleted(0L);
        try {
            if (mapper.insert(dataObject) != 1) {
                throw new AgentDomainException("Agent pricing persistence failed");
            }
        } catch (DataIntegrityViolationException ex) {
            throw new AgentPricingConflictException();
        }
        return toDomain(dataObject);
    }

    private AgentPricingVersion toDomain(AgentPricingVersionDO dataObject) {
        return new AgentPricingVersion(dataObject.getId(), dataObject.getTenantId(), dataObject.getAgentId(), dataObject
            .getParentPricingVersionId(), dataObject.getVersionNo(), dataObject.getChannelCode(), dataObject
                .getProductCode(), dataObject.getCurrency(), readRules(dataObject.getPricingRulesJson()), dataObject
                    .getEffectiveTime(), dataObject.getExpiresTime(), dataObject.getStatus(), dataObject
                        .getCreateUser(), dataObject.getCreateTime());
    }

    private String writeRules(AgentPricingRules rules) {
        try {
            return objectMapper.writeValueAsString(rules);
        } catch (JsonProcessingException ex) {
            throw new AgentDomainException("Agent pricing serialization failed");
        }
    }

    private AgentPricingRules readRules(String json) {
        try {
            return objectMapper.readValue(json, AgentPricingRules.class);
        } catch (JsonProcessingException ex) {
            throw new AgentDomainException("Stored agent pricing rules are invalid");
        }
    }
}
