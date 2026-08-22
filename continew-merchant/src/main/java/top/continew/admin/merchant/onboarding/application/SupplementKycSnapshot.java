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

import java.time.LocalDate;
import java.util.Arrays;

/** Stored-only values used for sanitized supplement comparison; ciphertext is compared but never returned. */
public record SupplementKycSnapshot(Long id, Integer versionNo, Long previousVersionId, String supplementTaskId,
                                    String status, Long rowVersion, String legalName, String legalIdentifierMasked,
                                    LocalDate licenseIssueDate, LocalDate licenseExpiryDate, String businessScope,
                                    byte[] addressPayload, byte[] personPayload, byte[] shareholderPayload,
                                    Long pricingVersionId, String settlementAccountMasked, String settlementMode,
                                    String settlementVerificationStatus) {

    public SupplementKycSnapshot {
        addressPayload = copy(addressPayload);
        personPayload = copy(personPayload);
        shareholderPayload = copy(shareholderPayload);
    }

    private static byte[] copy(byte[] value) {
        return value == null ? null : Arrays.copyOf(value, value.length);
    }
}
