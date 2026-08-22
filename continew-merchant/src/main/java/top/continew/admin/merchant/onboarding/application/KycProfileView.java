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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Masked KYC profile response; complete identity and mobile values are never returned. */
public record KycProfileView(Long kycVersionId, Long rowVersion, String legalName, String legalIdentifierMasked,
                             LocalDate licenseIssueDate, LocalDate licenseExpiryDate, String businessScope,
                             AddressView address, List<PersonView> persons, List<ShareholderView> shareholders) {

    public KycProfileView {
        persons = List.copyOf(persons);
        shareholders = List.copyOf(shareholders);
    }

    public record AddressView(String registeredAddress, String operatingRegion, String operatingAddress) {
    }

    public record PersonView(KycProfileSaveCommand.PersonRole role, String name, String identityNumberMasked,
                             String mobileMasked, LocalDate documentValidFrom, LocalDate documentValidTo) {
    }

    public record ShareholderView(KycProfileSaveCommand.ShareholderType type, String name, String identifierMasked,
                                  BigDecimal ownershipPercent) {
    }
}
