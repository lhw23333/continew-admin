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

package top.continew.admin.channel.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.channel.api.ChannelEvidenceAuditPort;
import top.continew.admin.channel.dto.ChannelEvidenceAuditRecord;

/** Commits channel evidence access decisions independently from adapter transactions. */
@Repository
@RequiredArgsConstructor
public class MyBatisChannelEvidenceAuditRepository implements ChannelEvidenceAuditPort {

    private final ChannelEvidenceAuditMapper mapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long append(ChannelEvidenceAuditRecord record) {
        ChannelEvidenceAuditDO row = new ChannelEvidenceAuditDO();
        row.setTenantId(record.context().tenantId());
        row.setChannelCode(record.context().product().channelCode());
        row.setProductCode(record.context().product().productCode());
        row.setConfigVersion(record.context().configVersion());
        row.setOperation(record.operation());
        row.setBusinessType(record.context().businessType());
        row.setBusinessId(record.context().businessId());
        row.setBusinessVersion(record.context().businessVersion());
        row.setBusinessSerial(record.context().businessSerial());
        row.setTraceId(record.context().traceId());
        row.setKycVersionId(record.kycVersionId());
        row.setObjectId(record.objectId());
        row.setEvidenceType(record.evidenceType());
        row.setObjectSha256(record.objectSha256());
        row.setAccessMode(record.accessMode());
        row.setExpiresAt(record.expiresAt());
        row.setOutcome(record.outcome());
        row.setFailureCategory(record.failureCategory());
        row.setCreateTime(record.createTime());
        if (mapper.insert(row) != 1) {
            throw new IllegalStateException("Channel evidence audit persistence failed");
        }
        return row.getId();
    }
}
