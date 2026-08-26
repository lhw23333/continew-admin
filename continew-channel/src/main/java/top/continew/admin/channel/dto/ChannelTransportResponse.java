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
import java.util.Arrays;

/** Controlled provider response; body is available to the adapter but redacted from string rendering. */
public record ChannelTransportResponse(int statusCode, String providerRequestId, byte[] payload,
                                       LocalDateTime receivedTime) {
    public ChannelTransportResponse {
        if (statusCode < 100 || statusCode > 599 || payload == null || receivedTime == null) {
            throw ChannelContracts.invalid("transport response");
        }
        providerRequestId = providerRequestId == null || providerRequestId.isBlank()
            ? null
            : ChannelContracts.reference(providerRequestId, "providerRequestId");
        payload = Arrays.copyOf(payload, payload.length);
    }

    @Override
    public byte[] payload() {
        return Arrays.copyOf(payload, payload.length);
    }

    @Override
    public String toString() {
        return "ChannelTransportResponse[statusCode=%s, providerRequestId=%s, payload=<redacted>, receivedTime=%s]"
            .formatted(statusCode, providerRequestId, receivedTime);
    }
}
