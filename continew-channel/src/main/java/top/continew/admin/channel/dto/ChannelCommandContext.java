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

/** Common idempotent, versioned, non-secret context for every outbound channel operation. */
public record ChannelCommandContext(Long tenantId, ChannelProductKey product, String configVersion,
                                    ChannelBusinessType businessType, Long businessId, Long businessVersion,
                                    String businessSerial, String traceId) {
    public ChannelCommandContext {
        tenantId = ChannelContracts.positive(tenantId, "tenantId");
        if (product == null || businessType == null)
            throw ChannelContracts.invalid("channel command context");
        configVersion = ChannelContracts.reference(configVersion, "configVersion");
        businessId = ChannelContracts.positive(businessId, "businessId");
        businessVersion = ChannelContracts.positive(businessVersion, "businessVersion");
        businessSerial = ChannelContracts.reference(businessSerial, "businessSerial");
        traceId = ChannelContracts.reference(traceId, "traceId");
    }
}
