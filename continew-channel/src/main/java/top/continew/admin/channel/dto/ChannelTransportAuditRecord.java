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

/** Payload-free immutable evidence for one outbound preparation or result. */
public record ChannelTransportAuditRecord(ChannelCommandContext context, ChannelOperation operation,
                                          ChannelTransportOutcome outcome, LocalDateTime requestTime,
                                          LocalDateTime responseTime, Long durationMillis, String nonceFingerprint,
                                          String signingKeyVersion, String encryptionKeyVersion, Integer statusCode,
                                          String failureCategory) {
    public ChannelTransportAuditRecord {
        if (context == null || operation == null || outcome == null || requestTime == null || durationMillis != null && durationMillis < 0 || statusCode != null && (statusCode < 100 || statusCode > 599) || tooLong(nonceFingerprint, 64) || tooLong(signingKeyVersion, 64) || tooLong(encryptionKeyVersion, 64) || tooLong(failureCategory, 64)) {
            throw ChannelContracts.invalid("transport audit");
        }
    }

    private static boolean tooLong(String value, int maxLength) {
        return value != null && value.length() > maxLength;
    }
}
