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

package top.continew.admin.merchant.onboarding.application;

import java.time.LocalDateTime;
import java.util.List;

/** Independently versioned operating-platform record within one KYC draft. */
public record OperatingPlatform(Long id, Long kycVersionId, String platformCode, String storeName, String storeUrl,
                                String storeIdentifier, CertificationStatus certificationStatus, Long rowVersion,
                                LocalDateTime createTime, LocalDateTime updateTime,
                                List<ProofAttachment> proofAttachments) {

    public OperatingPlatform {
        proofAttachments = List.copyOf(proofAttachments);
    }

    public enum CertificationStatus { UNVERIFIED, CERTIFIED, REJECTED }

    public record ProofAttachment(Long attachmentId, String evidenceType, String originalName, String scanStatus,
                                  String validationStatus) {
    }
}
