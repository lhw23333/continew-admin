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

/** Authenticated callback boundary consumed by channel-specific parsing and event persistence. */
public record VerifiedChannelCallback(Long tenantId, ChannelProductKey product, String configVersion,
                                      long timestampEpochMillis, String nonceFingerprint, String keyVersion,
                                      String payloadHash, byte[] payload, LocalDateTime receivedTime) {
    public VerifiedChannelCallback {
        tenantId = ChannelContracts.positive(tenantId, "tenantId");
        if (product == null || timestampEpochMillis <= 0 || receivedTime == null || payload == null || payload.length == 0) {
            throw ChannelContracts.invalid("verified callback");
        }
        configVersion = ChannelContracts.reference(configVersion, "configVersion");
        nonceFingerprint = ChannelContracts.reference(nonceFingerprint, "nonceFingerprint");
        keyVersion = ChannelContracts.reference(keyVersion, "keyVersion");
        payloadHash = ChannelContracts.reference(payloadHash, "payloadHash");
        payload = Arrays.copyOf(payload, payload.length);
    }

    @Override
    public byte[] payload() {
        return Arrays.copyOf(payload, payload.length);
    }

    @Override
    public String toString() {
        return "VerifiedChannelCallback[tenantId=%s, product=%s, configVersion=%s, timestamp=%s, nonceFingerprint=%s, keyVersion=%s, payloadHash=%s, payload=<redacted>, receivedTime=%s]"
            .formatted(tenantId, product
                .dimensionKey(), configVersion, timestampEpochMillis, nonceFingerprint, keyVersion, payloadHash, receivedTime);
    }
}
