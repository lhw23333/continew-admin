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

/** Verified, payload-free normalized channel event. Raw callback bodies remain controlled evidence elsewhere. */
public record ChannelEvent(Long tenantId, String eventId, ChannelEventType eventType, ChannelProductKey product,
                           ChannelBusinessType businessType, Long businessId, Long businessVersion,
                           String businessSerial, String channelRequestId, String rawStatusCode,
                           String statusMappingVersion, ChannelOperationStatus operationStatus,
                           ChannelOnboardingState onboardingState, LocalDateTime occurredTime,
                           LocalDateTime receivedTime) {
    public ChannelEvent {
        tenantId = ChannelContracts.positive(tenantId, "tenantId");
        eventId = ChannelContracts.reference(eventId, "eventId");
        if (eventType == null || product == null || businessType == null || operationStatus == null || occurredTime == null || receivedTime == null || receivedTime
            .isBefore(occurredTime)) {
            throw ChannelContracts.invalid("channel event");
        }
        businessId = ChannelContracts.positive(businessId, "businessId");
        businessVersion = ChannelContracts.positive(businessVersion, "businessVersion");
        businessSerial = ChannelContracts.reference(businessSerial, "businessSerial");
        channelRequestId = channelRequestId == null
            ? null
            : ChannelContracts.reference(channelRequestId, "channelRequestId");
        rawStatusCode = ChannelContracts.optionalText(rawStatusCode, 64, "rawStatusCode");
        statusMappingVersion = ChannelContracts.reference(statusMappingVersion, "statusMappingVersion");
        if (businessType == ChannelBusinessType.ONBOARDING && onboardingState == null) {
            throw ChannelContracts.invalid("onboarding event state");
        }
    }
}
