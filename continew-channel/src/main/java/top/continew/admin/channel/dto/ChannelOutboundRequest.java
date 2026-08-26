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

import java.net.URI;
import java.util.Arrays;

/** Fully authenticated outbound request. Payload, signature, endpoint, and nonce are redacted from logs. */
public record ChannelOutboundRequest(ChannelCommandContext context, ChannelOperation operation, URI endpoint,
                                     long timestampEpochMillis, String nonce, String nonceFingerprint,
                                     String signingKeyVersion, String encryptionKeyVersion, boolean encrypted,
                                     byte[] payload, String signature) {
    public ChannelOutboundRequest {
        if (context == null || operation == null || endpoint == null || !"https".equalsIgnoreCase(endpoint
            .getScheme()) || timestampEpochMillis <= 0 || nonce == null || nonce
                .isBlank() || nonceFingerprint == null || nonceFingerprint
                    .isBlank() || signingKeyVersion == null || signingKeyVersion
                        .isBlank() || payload == null || payload.length == 0 || signature == null || signature
                            .isBlank() || encrypted && (encryptionKeyVersion == null || encryptionKeyVersion
                                .isBlank())) {
            throw ChannelContracts.invalid("outbound request");
        }
        payload = Arrays.copyOf(payload, payload.length);
    }

    @Override
    public byte[] payload() {
        return Arrays.copyOf(payload, payload.length);
    }

    @Override
    public String toString() {
        return "ChannelOutboundRequest[channel=%s, operation=%s, businessSerial=%s, traceId=%s, timestamp=%s, nonce=<redacted>, endpoint=<redacted>, payload=<redacted>, signature=<redacted>]"
            .formatted(context.product().channelCode(), operation, context.businessSerial(), context
                .traceId(), timestampEpochMillis);
    }
}
