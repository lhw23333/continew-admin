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
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Repository;
import top.continew.admin.merchant.agent.application.AgentListQuery;
import top.continew.admin.merchant.agent.application.AgentPage;
import top.continew.admin.merchant.agent.application.AgentRepository;
import top.continew.admin.merchant.agent.application.AgentSummary;
import top.continew.admin.merchant.agent.domain.Agent;
import top.continew.admin.merchant.agent.domain.AgentDomainException;
import top.continew.admin.merchant.security.value.EncryptedMobileNumber;

import java.util.Optional;
import java.util.List;

/** MyBatis agent repository with mandatory tenant predicates. */
@Repository
@RequiredArgsConstructor
public class MyBatisAgentRepository implements AgentRepository {

    private final AgentMapper mapper;

    @Override
    public Optional<Agent> findById(Long tenantId, Long agentId) {
        return Optional.ofNullable(mapper.lambdaQuery()
            .eq(AgentDO::getTenantId, tenantId)
            .eq(AgentDO::getId, agentId)
            .eq(AgentDO::getDeleted, 0L)
            .one()).map(this::toDomain);
    }

    @Override
    public Optional<Agent> findByUserId(Long tenantId, Long userId) {
        return Optional.ofNullable(mapper.lambdaQuery()
            .eq(AgentDO::getTenantId, tenantId)
            .eq(AgentDO::getUserId, userId)
            .eq(AgentDO::getDeleted, 0L)
            .one()).map(this::toDomain);
    }

    @Override
    public boolean existsById(Long tenantId, Long agentId) {
        return mapper.lambdaQuery()
            .eq(AgentDO::getTenantId, tenantId)
            .eq(AgentDO::getId, agentId)
            .eq(AgentDO::getDeleted, 0L)
            .exists();
    }

    @Override
    public boolean existsByAgentNo(Long tenantId, String agentNo) {
        return mapper.lambdaQuery()
            .eq(AgentDO::getTenantId, tenantId)
            .eq(AgentDO::getAgentNo, agentNo.trim())
            .eq(AgentDO::getDeleted, 0L)
            .exists();
    }

    @Override
    public boolean existsByUserId(Long tenantId, Long userId) {
        return mapper.lambdaQuery()
            .eq(AgentDO::getTenantId, tenantId)
            .eq(AgentDO::getUserId, userId)
            .eq(AgentDO::getDeleted, 0L)
            .exists();
    }

    @Override
    public AgentPage page(Long tenantId, List<Long> authorizedAgentIds, AgentListQuery query) {
        if (authorizedAgentIds.isEmpty()) {
            return AgentPage.empty(query.page(), query.size());
        }
        LambdaQueryWrapper<AgentDO> wrapper = new LambdaQueryWrapper<AgentDO>().eq(AgentDO::getTenantId, tenantId)
            .in(AgentDO::getId, authorizedAgentIds)
            .eq(AgentDO::getDeleted, 0L)
            .eq(query.agentId() != null, AgentDO::getId, query.agentId())
            .like(query.name() != null, AgentDO::getName, query.name())
            .eq(query.status() != null, AgentDO::getStatus, query.status())
            .orderByDesc(AgentDO::getCreateTime)
            .orderByDesc(AgentDO::getId);
        Page<AgentDO> page = mapper.selectPage(new Page<>(query.page(), query.size()), wrapper);
        return new AgentPage(page.getRecords().stream().map(this::toDomain).map(AgentSummary::from).toList(), page
            .getTotal(), query.page(), query.size());
    }

    @Override
    public boolean bindDepartment(Long tenantId, Long agentId, Long deptId) {
        return mapper.lambdaUpdate()
            .eq(AgentDO::getTenantId, tenantId)
            .eq(AgentDO::getId, agentId)
            .isNull(AgentDO::getDeptId)
            .eq(AgentDO::getDeleted, 0L)
            .set(AgentDO::getDeptId, deptId)
            .update();
    }

    @Override
    public boolean updateProfile(Agent agent, Long expectedVersion) {
        var update = mapper.lambdaUpdate()
            .eq(AgentDO::getTenantId, agent.tenantId())
            .eq(AgentDO::getId, agent.id())
            .eq(AgentDO::getRowVersion, expectedVersion)
            .eq(AgentDO::getDeleted, 0L)
            .set(AgentDO::getName, agent.name())
            .set(AgentDO::getContactName, agent.contactName())
            .set(AgentDO::getRemarks, agent.remarks())
            .set(AgentDO::getRowVersion, agent.rowVersion())
            .set(AgentDO::getUpdateTime, agent.updateTime());
        if (agent.contactMobile() == null) {
            update.set(AgentDO::getContactMobileCiphertext, null)
                .set(AgentDO::getContactMobileHash, null)
                .set(AgentDO::getContactMobileHashKeyVersion, null)
                .set(AgentDO::getContactMobileMasked, null)
                .set(AgentDO::getContactMobileKeyVersion, null);
        } else {
            update.set(AgentDO::getContactMobileCiphertext, agent.contactMobile().ciphertext())
                .set(AgentDO::getContactMobileHash, agent.contactMobile().normalizedHash())
                .set(AgentDO::getContactMobileHashKeyVersion, agent.contactMobile().hashKeyVersion())
                .set(AgentDO::getContactMobileMasked, agent.contactMobile().maskedValue())
                .set(AgentDO::getContactMobileKeyVersion, agent.contactMobile().keyVersion());
        }
        return update.update();
    }

    @Override
    public void insert(Agent agent) {
        if (mapper.insert(toDataObject(agent)) != 1) {
            throw new AgentDomainException("Agent persistence failed");
        }
    }

    @Override
    public boolean updateLifecycle(Agent agent, Long expectedVersion) {
        return mapper.lambdaUpdate()
            .eq(AgentDO::getTenantId, agent.tenantId())
            .eq(AgentDO::getId, agent.id())
            .eq(AgentDO::getRowVersion, expectedVersion)
            .eq(AgentDO::getDeleted, 0L)
            .set(AgentDO::getStatus, agent.status())
            .set(AgentDO::getDisabledReason, agent.disabledReason())
            .set(AgentDO::getRowVersion, agent.rowVersion())
            .set(AgentDO::getUpdateTime, agent.updateTime())
            .update();
    }

    private Agent toDomain(AgentDO dataObject) {
        return new Agent(dataObject.getId(), dataObject.getTenantId(), dataObject.getParentId(), dataObject
            .getPath(), dataObject.getUserId(), dataObject.getDeptId(), dataObject.getAgentNo(), dataObject
                .getName(), dataObject.getContactName(), restoreMobile(dataObject), dataObject.getRemarks(), dataObject
                    .getPromotionCode(), dataObject.getStatus(), dataObject.getDisabledReason(), dataObject
                        .getRowVersion(), dataObject.getCreateTime(), dataObject.getUpdateTime());
    }

    private AgentDO toDataObject(Agent agent) {
        AgentDO dataObject = new AgentDO();
        dataObject.setId(agent.id());
        dataObject.setTenantId(agent.tenantId());
        dataObject.setParentId(agent.parentId());
        dataObject.setPath(agent.path());
        dataObject.setUserId(agent.userId());
        dataObject.setDeptId(agent.deptId());
        dataObject.setAgentNo(agent.agentNo());
        dataObject.setName(agent.name());
        dataObject.setContactName(agent.contactName());
        if (agent.contactMobile() != null) {
            dataObject.setContactMobileCiphertext(agent.contactMobile().ciphertext());
            dataObject.setContactMobileHash(agent.contactMobile().normalizedHash());
            dataObject.setContactMobileHashKeyVersion(agent.contactMobile().hashKeyVersion());
            dataObject.setContactMobileMasked(agent.contactMobile().maskedValue());
            dataObject.setContactMobileKeyVersion(agent.contactMobile().keyVersion());
        }
        dataObject.setRemarks(agent.remarks());
        dataObject.setPromotionCode(agent.promotionCode());
        dataObject.setStatus(agent.status());
        dataObject.setDisabledReason(agent.disabledReason());
        dataObject.setRowVersion(agent.rowVersion());
        dataObject.setCreateTime(agent.createTime());
        dataObject.setUpdateTime(agent.updateTime());
        dataObject.setDeleted(0L);
        return dataObject;
    }

    private EncryptedMobileNumber restoreMobile(AgentDO dataObject) {
        if (dataObject.getContactMobileCiphertext() == null) {
            return null;
        }
        try {
            return EncryptedMobileNumber.restore(dataObject.getContactMobileCiphertext(), dataObject
                .getContactMobileKeyVersion(), dataObject.getContactMobileHash(), dataObject
                    .getContactMobileHashKeyVersion(), dataObject.getContactMobileMasked());
        } catch (IllegalArgumentException ex) {
            throw new AgentDomainException("Stored agent mobile protection metadata is incomplete");
        }
    }
}
