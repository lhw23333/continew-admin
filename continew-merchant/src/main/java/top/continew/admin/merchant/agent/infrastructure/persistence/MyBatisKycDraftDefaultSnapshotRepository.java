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
import top.continew.admin.merchant.agent.application.KycDraftDefaultContext;
import top.continew.admin.merchant.agent.application.KycDraftDefaultSnapshotDraft;
import top.continew.admin.merchant.agent.application.KycDraftDefaultSnapshotRepository;
import top.continew.admin.merchant.agent.domain.AgentDomainException;
import top.continew.admin.merchant.agent.domain.AgentMerchantDefaultConflictException;
import top.continew.admin.merchant.agent.domain.AgentMerchantDefaults;
import top.continew.admin.merchant.agent.domain.KycDraftDefaultSnapshot;

import java.util.Optional;

/** Tenant-explicit immutable KYC draft-default snapshot repository. */
@Repository
@RequiredArgsConstructor
public class MyBatisKycDraftDefaultSnapshotRepository implements KycDraftDefaultSnapshotRepository {

    private final KycDraftDefaultSnapshotMapper mapper;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<KycDraftDefaultContext> findDraftContext(Long tenantId, Long kycVersionId) {
        return Optional.ofNullable(mapper.selectDraftContext(tenantId, kycVersionId))
            .map(dataObject -> new KycDraftDefaultContext(dataObject.getKycVersionId(), dataObject
                .getMerchantId(), dataObject.getOwningAgentId(), dataObject.getStatus()));
    }

    @Override
    public Optional<KycDraftDefaultSnapshot> findByKycVersionId(Long tenantId, Long kycVersionId) {
        return Optional.ofNullable(mapper.lambdaQuery()
            .eq(KycDraftDefaultSnapshotDO::getTenantId, tenantId)
            .eq(KycDraftDefaultSnapshotDO::getKycVersionId, kycVersionId)
            .eq(KycDraftDefaultSnapshotDO::getDeleted, 0L)
            .one()).map(this::toDomain);
    }

    @Override
    public KycDraftDefaultSnapshot insert(KycDraftDefaultSnapshotDraft draft) {
        KycDraftDefaultSnapshotDO dataObject = new KycDraftDefaultSnapshotDO();
        dataObject.setTenantId(draft.tenantId());
        dataObject.setKycVersionId(draft.kycVersionId());
        dataObject.setAgentDefaultVersionId(draft.agentDefaultVersionId());
        dataObject.setDefaultPayloadJson(writeDefaults(draft.defaults()));
        dataObject.setCopiedTime(draft.copiedTime());
        dataObject.setCreateUser(draft.createUser());
        dataObject.setCreateTime(draft.createTime());
        dataObject.setDeleted(0L);
        try {
            if (mapper.insert(dataObject) != 1) {
                throw new AgentDomainException("KYC draft-default snapshot persistence failed");
            }
            return toDomain(dataObject);
        } catch (DataIntegrityViolationException ex) {
            throw new AgentMerchantDefaultConflictException();
        }
    }

    private KycDraftDefaultSnapshot toDomain(KycDraftDefaultSnapshotDO dataObject) {
        return new KycDraftDefaultSnapshot(dataObject.getId(), dataObject.getTenantId(), dataObject
            .getKycVersionId(), dataObject.getAgentDefaultVersionId(), readDefaults(dataObject
                .getDefaultPayloadJson()), dataObject.getCopiedTime(), dataObject.getCreateUser(), dataObject
                    .getCreateTime());
    }

    private String writeDefaults(AgentMerchantDefaults defaults) {
        try {
            return objectMapper.writeValueAsString(defaults);
        } catch (JsonProcessingException ex) {
            throw new AgentDomainException("KYC draft-default serialization failed");
        }
    }

    private AgentMerchantDefaults readDefaults(String json) {
        try {
            return objectMapper.readValue(json, AgentMerchantDefaults.class);
        } catch (JsonProcessingException ex) {
            throw new AgentDomainException("Stored KYC draft defaults are invalid");
        }
    }
}
