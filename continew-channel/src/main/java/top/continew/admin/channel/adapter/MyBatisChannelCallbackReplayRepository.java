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
import top.continew.admin.channel.api.ChannelCallbackReplayPort;
import top.continew.admin.channel.dto.ChannelCallbackReplayClaim;

/** Persists each nonce hash once, relying on a database unique key for race-safe replay rejection. */
@Repository
@RequiredArgsConstructor
public class MyBatisChannelCallbackReplayRepository implements ChannelCallbackReplayPort {
    private final ChannelCallbackNonceMapper mapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claim(ChannelCallbackReplayClaim claim) {
        ChannelCallbackNonceDO row = new ChannelCallbackNonceDO();
        row.setTenantId(claim.tenantId());
        row.setChannelCode(claim.product().channelCode());
        row.setProductCode(claim.product().productCode());
        row.setConfigVersion(claim.configVersion());
        row.setCallbackKeyVersion(claim.keyVersion());
        row.setNonceHash(claim.nonceHash());
        row.setReceivedTime(claim.receivedTime());
        row.setExpiresTime(claim.expiresTime());
        row.setCreateTime(claim.receivedTime());
        return mapper.claim(row) == 1;
    }
}
