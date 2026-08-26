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

/** Sanitized raw-code-retaining event candidate claimed before normalized state mutation. */
public record ChannelEventRecord(Long tenantId, String eventKey, String channelEventId, ChannelEventType eventType,
                                 ChannelProductKey product, String configVersion, ChannelBusinessType businessType,
                                 Long businessId, Long businessVersion, Long merchantId, String businessSerial,
                                 String channelRequestId, String rawStatusCode, String mappingVersion,
                                 String payloadHash, String sanitizedPayloadJson, String signatureKeyVersion,
                                 LocalDateTime occurredTime, LocalDateTime receivedTime, String traceId) {
    public ChannelEventRecord {
        tenantId = ChannelContracts.positive(tenantId, "tenantId");
        businessId = ChannelContracts.positive(businessId, "businessId");
        businessVersion = ChannelContracts.positive(businessVersion, "businessVersion");
        merchantId = ChannelContracts.positive(merchantId, "merchantId");
        if (eventType == null || product == null || businessType == null || occurredTime == null || receivedTime == null || receivedTime
            .isBefore(occurredTime)) {
            throw ChannelContracts.invalid("channel event record");
        }
        eventKey = ChannelContracts.reference(eventKey, "eventKey");
        channelEventId = ChannelContracts.reference(channelEventId, "channelEventId");
        configVersion = ChannelContracts.reference(configVersion, "configVersion");
        businessSerial = ChannelContracts.reference(businessSerial, "businessSerial");
        channelRequestId = channelRequestId == null
            ? null
            : ChannelContracts.reference(channelRequestId, "channelRequestId");
        rawStatusCode = ChannelContracts.optionalText(rawStatusCode, 64, "rawStatusCode");
        if (rawStatusCode == null) {
            throw ChannelContracts.invalid("rawStatusCode");
        }
        mappingVersion = ChannelContracts.reference(mappingVersion, "mappingVersion");
        payloadHash = ChannelContracts.reference(payloadHash, "payloadHash");
        if (sanitizedPayloadJson == null || sanitizedPayloadJson.isBlank() || sanitizedPayloadJson.length() > 4000) {
            throw ChannelContracts.invalid("sanitizedPayloadJson");
        }
        signatureKeyVersion = ChannelContracts.reference(signatureKeyVersion, "signatureKeyVersion");
        traceId = traceId == null ? null : ChannelContracts.reference(traceId, "traceId");
    }
}
