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

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import top.continew.admin.channel.api.ChannelProductCatalog;
import top.continew.admin.channel.dto.ChannelProductKey;
import top.continew.admin.channel.dto.ChannelProductVersion;
import top.continew.admin.channel.dto.ChannelRequirementSummary;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** MyBatis channel product catalog selecting the latest effective row per channel/product dimension. */
@Repository
@RequiredArgsConstructor
public class MyBatisChannelProductCatalog implements ChannelProductCatalog {

    private static final TypeReference<Set<String>> MERCHANT_TYPES = new TypeReference<>() {};

    private final ChannelProductVersionMapper mapper;
    private final ObjectMapper objectMapper;

    @Override
    public List<ChannelProductVersion> listEffective(Long tenantId,
                                                     Set<ChannelProductKey> productKeys,
                                                     LocalDateTime effectiveAt) {
        if (tenantId == null || effectiveAt == null || productKeys == null || productKeys.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<ChannelProductVersionDO> query = new LambdaQueryWrapper<ChannelProductVersionDO>()
            .eq(ChannelProductVersionDO::getTenantId, tenantId)
            .le(ChannelProductVersionDO::getEffectiveTime, effectiveAt)
            .and(wrapper -> wrapper.isNull(ChannelProductVersionDO::getExpiresTime)
                .or()
                .gt(ChannelProductVersionDO::getExpiresTime, effectiveAt))
            .eq(ChannelProductVersionDO::getDeleted, 0L)
            .and(wrapper -> productKeys.forEach(key -> wrapper.or(pair -> pair
                .eq(ChannelProductVersionDO::getChannelCode, key.channelCode())
                .eq(ChannelProductVersionDO::getProductCode, key.productCode()))))
            .orderByDesc(ChannelProductVersionDO::getEffectiveTime)
            .orderByDesc(ChannelProductVersionDO::getId);
        Map<String, ChannelProductVersion> latest = new LinkedHashMap<>();
        mapper.selectList(query)
            .stream()
            .map(this::toDomain)
            .forEach(version -> latest.putIfAbsent(version.key().dimensionKey(), version));
        return List.copyOf(latest.values());
    }

    private ChannelProductVersion toDomain(ChannelProductVersionDO dataObject) {
        try {
            Set<String> merchantTypes = objectMapper.readValue(dataObject
                .getSupportedMerchantTypesJson(), MERCHANT_TYPES);
            ChannelRequirementSummary requirements = objectMapper.readValue(dataObject
                .getRequirementSummaryJson(), ChannelRequirementSummary.class);
            return new ChannelProductVersion(dataObject.getId(), dataObject
                .getTenantId(), new ChannelProductKey(dataObject.getChannelCode(), dataObject
                    .getProductCode()), dataObject.getConfigVersion(), dataObject
                        .getRequirementVersion(), merchantTypes, requirements, dataObject.getStatus(), dataObject
                            .getEffectiveTime(), dataObject.getExpiresTime(), dataObject.getCreateTime());
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw new IllegalStateException("Stored channel product configuration is invalid", ex);
        }
    }
}
