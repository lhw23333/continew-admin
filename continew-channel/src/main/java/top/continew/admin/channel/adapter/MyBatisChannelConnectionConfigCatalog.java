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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import top.continew.admin.channel.api.ChannelConnectionConfigCatalog;
import top.continew.admin.channel.dto.ChannelConnectionConfig;
import top.continew.admin.channel.dto.ChannelEndpointConfiguration;
import top.continew.admin.channel.dto.ChannelKeyPurpose;
import top.continew.admin.channel.dto.ChannelKeyReference;
import top.continew.admin.channel.dto.ChannelKeyReferences;
import top.continew.admin.channel.dto.ChannelProductKey;
import top.continew.admin.channel.dto.ChannelStatusMapping;
import top.continew.admin.channel.dto.ChannelTimeoutPolicy;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MyBatisChannelConnectionConfigCatalog implements ChannelConnectionConfigCatalog {
    private final ChannelConnectionVersionMapper mapper;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<ChannelConnectionConfig> findVersion(Long tenantId,
                                                         ChannelProductKey product,
                                                         String configVersion) {
        if (tenantId == null || tenantId <= 0 || product == null || configVersion == null || configVersion.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.lambdaQuery()
            .eq(ChannelConnectionVersionDO::getTenantId, tenantId)
            .eq(ChannelConnectionVersionDO::getChannelCode, product.channelCode())
            .eq(ChannelConnectionVersionDO::getProductCode, product.productCode())
            .eq(ChannelConnectionVersionDO::getConfigVersion, configVersion.trim())
            .eq(ChannelConnectionVersionDO::getDeleted, 0L)
            .one()).map(this::toDomain);
    }

    @Override
    public Optional<ChannelConnectionConfig> findEffective(Long tenantId,
                                                           ChannelProductKey product,
                                                           LocalDateTime effectiveAt) {
        if (tenantId == null || tenantId <= 0 || product == null || effectiveAt == null)
            return Optional.empty();
        return Optional.ofNullable(mapper.lambdaQuery()
            .eq(ChannelConnectionVersionDO::getTenantId, tenantId)
            .eq(ChannelConnectionVersionDO::getChannelCode, product.channelCode())
            .eq(ChannelConnectionVersionDO::getProductCode, product.productCode())
            .eq(ChannelConnectionVersionDO::getStatus, top.continew.admin.channel.dto.ChannelConnectionStatus.ENABLED)
            .le(ChannelConnectionVersionDO::getEffectiveTime, effectiveAt)
            .and(wrapper -> wrapper.isNull(ChannelConnectionVersionDO::getExpiresTime)
                .or()
                .gt(ChannelConnectionVersionDO::getExpiresTime, effectiveAt))
            .eq(ChannelConnectionVersionDO::getDeleted, 0L)
            .orderByDesc(ChannelConnectionVersionDO::getEffectiveTime)
            .orderByDesc(ChannelConnectionVersionDO::getId)
            .last("LIMIT 1")
            .one()).map(this::toDomain);
    }

    private ChannelConnectionConfig toDomain(ChannelConnectionVersionDO row) {
        try {
            ChannelEndpointConfiguration endpoints = objectMapper.readValue(row
                .getEndpointJson(), ChannelEndpointConfiguration.class);
            ChannelTimeoutPolicy timeouts = objectMapper.readValue(row.getTimeoutJson(), ChannelTimeoutPolicy.class);
            ChannelStatusMapping statusMapping = objectMapper.readValue(row
                .getStatusMappingJson(), ChannelStatusMapping.class);
            ChannelKeyReferences keys = new ChannelKeyReferences(new ChannelKeyReference(ChannelKeyPurpose.SIGNING, row
                .getSigningKeyRef()), row.getEncryptionKeyRef() == null
                    ? null
                    : new ChannelKeyReference(ChannelKeyPurpose.ENCRYPTION, row
                        .getEncryptionKeyRef()), new ChannelKeyReference(ChannelKeyPurpose.CALLBACK_VERIFICATION, row
                            .getCallbackVerificationKeyRef()));
            return new ChannelConnectionConfig(row.getId(), row.getTenantId(), new ChannelProductKey(row
                .getChannelCode(), row.getProductCode()), row.getConfigVersion(), endpoints, timeouts, row
                    .getStatusMappingVersion(), statusMapping, keys, row.getStatus(), row.getEffectiveTime(), row
                        .getExpiresTime(), row.getCreateTime());
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw new IllegalStateException("Stored channel connection configuration is invalid", ex);
        }
    }
}
