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

import java.util.List;

/** Channel requirement snapshot and current private-evidence completeness for one draft. */
public record OnboardingEvidenceSummary(Long applicationId, Long kycVersionId, String requirementVersion,
                                        boolean complete, List<EvidenceTypeStatus> evidenceTypes,
                                        List<EvidenceAttachmentView> attachments) {

    public OnboardingEvidenceSummary {
        evidenceTypes = List.copyOf(evidenceTypes);
        attachments = List.copyOf(attachments);
    }

    public record EvidenceTypeStatus(String evidenceType, boolean required, int totalCount, int cleanCount,
                                     int pendingScanCount, int invalidCount, boolean complete) {
    }

    public record EvidenceAttachmentView(Long attachmentId, String evidenceType, String originalName,
                                         String detectedMime, Long sizeBytes, String scanStatus,
                                         String validationStatus, Integer sort) {
    }
}
