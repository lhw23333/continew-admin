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

package top.continew.admin.merchant.security.audit.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import top.continew.admin.merchant.security.audit.application.SecurityAuditRepository;
import top.continew.admin.merchant.security.audit.domain.SecurityAuditRecord;

/** MyBatis append-only security-audit repository. */
@Repository
@RequiredArgsConstructor
public class MyBatisSecurityAuditRepository implements SecurityAuditRepository {

    private final SecurityAuditMapper mapper;

    @Override
    public Long append(SecurityAuditRecord record) {
        SecurityAuditDO dataObject = new SecurityAuditDO();
        dataObject.setTenantId(record.tenantId());
        dataObject.setActorUserId(record.actorUserId());
        dataObject.setActorAgentId(record.actorAgentId());
        dataObject.setAction(record.action());
        dataObject.setObjectType(record.objectType());
        dataObject.setObjectId(record.objectId());
        dataObject.setBusinessVersion(record.businessVersion());
        dataObject.setFieldName(record.fieldName());
        dataObject.setReason(record.reason());
        dataObject.setIpAddress(record.ipAddress());
        dataObject.setResult(record.result());
        dataObject.setFailureCode(record.failureCode());
        dataObject.setCreateTime(record.createTime());
        if (mapper.insert(dataObject) != 1) {
            throw new IllegalStateException("Security audit persistence failed");
        }
        return dataObject.getId();
    }
}
