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

/** Payload-free immutable evidence for one callback verification attempt. */
public record ChannelCallbackSecurityAuditRecord(Long tenantId, ChannelProductKey product, String configVersion,
                                                 ChannelCallbackSecurityOutcome outcome, String failureCategory,
                                                 String callbackKeyVersion, String presentedKeyFingerprint,
                                                 String nonceFingerprint, String payloadHash, String sourceFingerprint,
                                                 LocalDateTime receivedTime) {
    public ChannelCallbackSecurityAuditRecord {
        tenantId = ChannelContracts.positive(tenantId, "tenantId");
        if (product == null || outcome == null || receivedTime == null || tooLong(failureCategory, 64) || tooLong(callbackKeyVersion, 64) || tooLong(presentedKeyFingerprint, 64) || tooLong(nonceFingerprint, 64) || tooLong(payloadHash, 64) || tooLong(sourceFingerprint, 64)) {
            throw ChannelContracts.invalid("callback security audit");
        }
        configVersion = ChannelContracts.reference(configVersion, "configVersion");
    }

    private static boolean tooLong(String value, int maxLength) {
        return value != null && value.length() > maxLength;
    }
}
