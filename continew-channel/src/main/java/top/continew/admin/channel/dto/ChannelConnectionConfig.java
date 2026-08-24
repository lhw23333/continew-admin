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

package top.continew.admin.channel.dto;

import java.time.LocalDateTime;

/** Immutable versioned connection metadata with external secret references only. */
public record ChannelConnectionConfig(Long id, Long tenantId, ChannelProductKey product, String configVersion,
                                      ChannelEndpointConfiguration endpoints, ChannelTimeoutPolicy timeouts,
                                      String statusMappingVersion, ChannelStatusMapping statusMapping,
                                      ChannelKeyReferences keyReferences, ChannelConnectionStatus status,
                                      LocalDateTime effectiveTime, LocalDateTime expiresTime,
                                      LocalDateTime createTime) {
    public ChannelConnectionConfig {
        id = ChannelContracts.positive(id, "id");
        tenantId = ChannelContracts.positive(tenantId, "tenantId");
        if (product == null || endpoints == null || timeouts == null || statusMapping == null || keyReferences == null || status == null || effectiveTime == null || createTime == null) {
            throw ChannelContracts.invalid("connection config");
        }
        configVersion = ChannelContracts.reference(configVersion, "configVersion");
        statusMappingVersion = ChannelContracts.reference(statusMappingVersion, "statusMappingVersion");
        if (expiresTime != null && !expiresTime.isAfter(effectiveTime))
            throw ChannelContracts.invalid("expiresTime");
    }

    public boolean isEffectiveAt(LocalDateTime time) {
        return status == ChannelConnectionStatus.ENABLED && !effectiveTime
            .isAfter(time) && (expiresTime == null || expiresTime.isAfter(time));
    }
}
