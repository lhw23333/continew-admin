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

/** Plaintext command held only in controlled request memory before field and payload encryption. */
public record KycProfileSaveCommand(Long tenantId, Long actorUserId, Long merchantId, Long applicationId,
                                    String legalName, String legalIdentifier, LocalDate licenseIssueDate,
                                    LocalDate licenseExpiryDate, String businessScope, Address address,
                                    List<Person> persons, List<Shareholder> shareholders, Long expectedVersion,
                                    String ipAddress) {

    public record Address(String registeredAddress, String operatingRegion, String operatingAddress) {
    }

    public record Person(PersonRole role, String name, String identityNumber, String mobile,
                         LocalDate documentValidFrom, LocalDate documentValidTo) {
    }

    public record Shareholder(ShareholderType type, String name, String identifier, BigDecimal ownershipPercent) {
    }

    public enum PersonRole { LEGAL_REPRESENTATIVE, OPERATOR, BENEFICIAL_OWNER }

    public enum ShareholderType { INDIVIDUAL, CORPORATE }

    @Override
    public String toString() {
        return "KycProfileSaveCommand[tenantId=%s, actorUserId=%s, merchantId=%s, applicationId=%s, payload=<redacted>]"
            .formatted(tenantId, actorUserId, merchantId, applicationId);
    }
}
