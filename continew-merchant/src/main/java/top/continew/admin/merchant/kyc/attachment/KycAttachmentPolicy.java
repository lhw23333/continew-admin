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

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Configurable phase-one KYC attachment limits. */
public record KycAttachmentPolicy(long maxSizeBytes, int maxPerEvidenceType, int maxPerKycVersion,
                                  Duration accessExpiry, Set<String> allowedExtensions,
                                  Map<String, String> extensionMimeTypes) {

    public KycAttachmentPolicy {
        if (maxSizeBytes <= 0 || maxPerEvidenceType <= 0 || maxPerKycVersion <= 0 || accessExpiry == null || accessExpiry
            .isNegative() || accessExpiry.isZero() || allowedExtensions == null || extensionMimeTypes == null) {
            throw new IllegalArgumentException("KYC attachment policy is invalid");
        }
        allowedExtensions = allowedExtensions.stream()
            .map(value -> value.toLowerCase(Locale.ROOT))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        extensionMimeTypes = Map.copyOf(extensionMimeTypes);
    }
}
