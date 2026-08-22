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

/** Sanitized category-only diff between submitted and supplement KYC versions. */
public record OnboardingSupplementDiff(Long previousKycVersionId, Long currentKycVersionId, List<String> changedFields,
                                       List<AttachmentChange> attachmentChanges, List<PlatformChange> platformChanges) {
    public OnboardingSupplementDiff {
        changedFields = List.copyOf(changedFields);
        attachmentChanges = List.copyOf(attachmentChanges);
        platformChanges = List.copyOf(platformChanges);
    }

    public record AttachmentChange(String evidenceType, String originalName, String changeType) {}

    public record PlatformChange(String platformCode, String storeIdentifier, String changeType) {}
}
