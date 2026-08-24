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

/** Traceable sanitized result metadata retaining the raw code and mapping version. */
public record ChannelResultMeta(ChannelProductKey product, String configVersion, String businessSerial,
                                String channelRequestId, String rawStatusCode, String statusMappingVersion,
                                ChannelOperationStatus operationStatus, String safeMessage, LocalDateTime resultTime) {
    public ChannelResultMeta {
        if (product == null || operationStatus == null || resultTime == null)
            throw ChannelContracts.invalid("channel result metadata");
        configVersion = ChannelContracts.reference(configVersion, "configVersion");
        businessSerial = ChannelContracts.reference(businessSerial, "businessSerial");
        channelRequestId = channelRequestId == null
            ? null
            : ChannelContracts.reference(channelRequestId, "channelRequestId");
        rawStatusCode = ChannelContracts.optionalText(rawStatusCode, 64, "rawStatusCode");
        statusMappingVersion = ChannelContracts.reference(statusMappingVersion, "statusMappingVersion");
        safeMessage = ChannelContracts.optionalText(safeMessage, 255, "safeMessage");
    }
}
