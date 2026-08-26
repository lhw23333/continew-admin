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
import top.continew.admin.channel.api.ChannelCallbackSecurityAuditPort;
import top.continew.admin.channel.dto.ChannelCallbackSecurityAuditRecord;

/** Commits sanitized callback verification evidence independently from event processing. */
@Repository
@RequiredArgsConstructor
public class MyBatisChannelCallbackSecurityAuditRepository implements ChannelCallbackSecurityAuditPort {
    private final ChannelCallbackSecurityAuditMapper mapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long append(ChannelCallbackSecurityAuditRecord record) {
        ChannelCallbackSecurityAuditDO row = new ChannelCallbackSecurityAuditDO();
        row.setTenantId(record.tenantId());
        row.setChannelCode(record.product().channelCode());
        row.setProductCode(record.product().productCode());
        row.setConfigVersion(record.configVersion());
        row.setOutcome(record.outcome());
        row.setFailureCategory(record.failureCategory());
        row.setCallbackKeyVersion(record.callbackKeyVersion());
        row.setPresentedKeyFingerprint(record.presentedKeyFingerprint());
        row.setNonceFingerprint(record.nonceFingerprint());
        row.setPayloadHash(record.payloadHash());
        row.setSourceFingerprint(record.sourceFingerprint());
        row.setReceivedTime(record.receivedTime());
        row.setCreateTime(record.receivedTime());
        if (mapper.insert(row) != 1) {
            throw new IllegalStateException("Channel callback security audit persistence failed");
        }
        return row.getId();
    }
}
