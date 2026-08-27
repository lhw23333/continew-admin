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
import java.util.Locale;
import java.util.regex.Pattern;

/** URL-free and storage-key-free evidence describing one channel attachment access decision. */
public record ChannelEvidenceAuditRecord(ChannelCommandContext context, ChannelOperation operation, Long kycVersionId,
                                         Long objectId, String evidenceType, String objectSha256,
                                         ChannelEvidenceAccessMode accessMode, LocalDateTime expiresAt,
                                         ChannelEvidenceAuditOutcome outcome, String failureCategory,
                                         LocalDateTime createTime) {

    private static final Pattern SHA_256 = Pattern.compile("[a-f0-9]{64}");

    public ChannelEvidenceAuditRecord {
        if (context == null || context
            .businessType() != ChannelBusinessType.ONBOARDING || operation != ChannelOperation.SUBMIT_ONBOARDING || accessMode == null || outcome == null || createTime == null) {
            throw ChannelContracts.invalid("channel evidence audit");
        }
        kycVersionId = ChannelContracts.positive(kycVersionId, "kycVersionId");
        objectId = ChannelContracts.positive(objectId, "objectId");
        evidenceType = evidenceType == null ? null : ChannelContracts.code(evidenceType, "evidenceType");
        objectSha256 = objectSha256 == null ? null : objectSha256.trim().toLowerCase(Locale.ROOT);
        if (objectSha256 != null && !SHA_256.matcher(objectSha256).matches())
            throw ChannelContracts.invalid("objectSha256");
        failureCategory = ChannelContracts.optionalText(failureCategory, 64, "failureCategory");
        if (outcome == ChannelEvidenceAuditOutcome.GRANTED && (evidenceType == null || objectSha256 == null || expiresAt == null || failureCategory != null)) {
            throw ChannelContracts.invalid("granted channel evidence audit");
        }
        if (outcome == ChannelEvidenceAuditOutcome.DENIED && failureCategory == null)
            throw ChannelContracts.invalid("denied channel evidence audit");
    }
}
