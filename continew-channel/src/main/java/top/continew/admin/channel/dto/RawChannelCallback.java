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

import java.util.Arrays;

/** Untrusted callback envelope. Sensitive headers and body are redacted from string rendering. */
public record RawChannelCallback(Long tenantId, ChannelProductKey product, String configVersion, String timestamp,
                                 String nonce, String keyVersion, String signature, byte[] payload,
                                 String sourceAddress) {
    public RawChannelCallback {
        tenantId = ChannelContracts.positive(tenantId, "tenantId");
        if (product == null) {
            throw ChannelContracts.invalid("product");
        }
        configVersion = ChannelContracts.reference(configVersion, "configVersion");
        payload = payload == null ? new byte[0] : Arrays.copyOf(payload, payload.length);
        sourceAddress = ChannelContracts.optionalText(sourceAddress, 255, "sourceAddress");
    }

    @Override
    public byte[] payload() {
        return Arrays.copyOf(payload, payload.length);
    }

    @Override
    public String toString() {
        return "RawChannelCallback[tenantId=%s, product=%s, configVersion=%s, timestamp=<redacted>, nonce=<redacted>, keyVersion=<redacted>, signature=<redacted>, payload=<redacted>, sourceAddress=<redacted>]"
            .formatted(tenantId, product.dimensionKey(), configVersion);
    }
}
