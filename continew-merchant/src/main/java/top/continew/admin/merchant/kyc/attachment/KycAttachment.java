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

import java.time.LocalDateTime;

/** Private KYC attachment metadata; storage URLs and file contents are never persisted here. */
public record KycAttachment(Long id, Long tenantId, Long kycVersionId, String evidenceType, String storageObjectId,
                            String originalName, String extension, String declaredMime, String detectedMime,
                            Long sizeBytes, String sha256, KycAttachmentScanStatus scanStatus,
                            KycAttachmentValidationStatus validationStatus, Integer sort, LocalDateTime createTime) {

    public KycAttachment {
        if (id == null || tenantId == null || kycVersionId == null || evidenceType == null || storageObjectId == null || originalName == null || sizeBytes == null || sha256 == null || scanStatus == null || validationStatus == null || sort == null || createTime == null) {
            throw new IllegalArgumentException("Required KYC attachment metadata must not be null");
        }
    }

    public boolean isAccessible() {
        return KycAttachmentValidationStatus.VALID.equals(validationStatus) && KycAttachmentScanStatus.CLEAN
            .equals(scanStatus);
    }
}
