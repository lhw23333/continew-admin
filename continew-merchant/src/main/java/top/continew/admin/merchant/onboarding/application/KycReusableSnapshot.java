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
import java.time.LocalDateTime;

/** Internal protected snapshot used only while applying same-merchant KYC reuse. */
public record KycReusableSnapshot(Long id, Long merchantId, Long onboardingApplicationId, Integer versionNo,
                                  String sourceChannelCode, String requirementVersion, String status, String legalName,
                                  byte[] legalIdentifierCiphertext, String legalIdentifierHash,
                                  String legalIdentifierHashKeyVersion, String legalIdentifierMasked,
                                  String legalIdentifierKeyVersion, LocalDate licenseIssueDate,
                                  LocalDate licenseExpiryDate, String businessScope, LocalDateTime frozenTime,
                                  LocalDateTime updateTime, LocalDateTime createTime) {

    public KycReusableSnapshot {
        legalIdentifierCiphertext = legalIdentifierCiphertext == null ? null : legalIdentifierCiphertext.clone();
    }

    @Override
    public byte[] legalIdentifierCiphertext() {
        return legalIdentifierCiphertext == null ? null : legalIdentifierCiphertext.clone();
    }

    public LocalDateTime sourceTime() {
        return updateTime != null ? updateTime : frozenTime != null ? frozenTime : createTime;
    }
}
