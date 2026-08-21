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

package top.continew.admin.merchant.kyc.attachment;

import java.util.Arrays;
import java.util.Locale;

/** In-memory upload command bounded by {@link KycAttachmentPolicy#maxSizeBytes()}. */
public record KycAttachmentUploadCommand(Long tenantId, Long actorUserId, Long kycVersionId, String evidenceType,
                                         String originalName, String declaredMime, byte[] content, Integer sort) {

    public KycAttachmentUploadCommand {
        if (tenantId == null || tenantId <= 0 || actorUserId == null || actorUserId <= 0 || kycVersionId == null || kycVersionId <= 0) {
            throw new IllegalArgumentException("Tenant, actor, and KYC version must be positive");
        }
        if (evidenceType == null || evidenceType.isBlank() || evidenceType.trim()
            .length() > 64 || originalName == null || originalName.isBlank() || originalName.trim()
                .length() > 255 || content == null || content.length == 0) {
            throw new IllegalArgumentException("KYC attachment upload command is invalid");
        }
        evidenceType = evidenceType.trim();
        originalName = originalName.trim();
        declaredMime = declaredMime == null || declaredMime.isBlank()
            ? null
            : declaredMime.trim().toLowerCase(Locale.ROOT);
        content = Arrays.copyOf(content, content.length);
        sort = sort == null ? 999 : sort;
    }

    @Override
    public byte[] content() {
        return Arrays.copyOf(content, content.length);
    }

    @Override
    public String toString() {
        return "KycAttachmentUploadCommand[tenantId=%s, actorUserId=%s, kycVersionId=%s, evidenceType=%s, content=<redacted>]"
            .formatted(tenantId, actorUserId, kycVersionId, evidenceType);
    }
}
