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

package top.continew.admin.merchant.master.domain;

import top.continew.admin.merchant.security.value.EncryptedMobileNumber;

import java.time.LocalDateTime;

/** Merchant master aggregate. Channel onboarding states live outside this aggregate. */
public record Merchant(Long id, Long tenantId, Long owningAgentId, String merchantNo, MerchantType merchantType,
                       String legalName, String shortName, String legalSubjectHash, Long operatorUserId,
                       Long reviewerUserId, String contactName, EncryptedMobileNumber contactMobile, String industry,
                       String productDescription, MerchantStatus status, String disabledReason,
                       Long certifiedKycVersionId, Long rowVersion, LocalDateTime createTime,
                       LocalDateTime updateTime) {

    public Merchant {
        if (id == null || tenantId == null || owningAgentId == null || operatorUserId == null || reviewerUserId == null || merchantType == null || status == null || rowVersion == null || createTime == null) {
            throw new IllegalArgumentException("Required merchant fields must not be null");
        }
    }

    public static Merchant create(MerchantRegistration registration, LocalDateTime now) {
        return new Merchant(registration.id(), registration.tenantId(), registration.owningAgentId(), registration
            .merchantNo()
            .trim(), registration.merchantType(), registration.legalName().trim(), registration.shortName()
                .trim(), normalizeOptional(registration.legalSubjectHash()), registration.operatorUserId(), registration
                    .reviewerUserId(), normalizeOptional(registration.contactName()), registration
                        .contactMobile(), normalizeOptional(registration.industry()), normalizeOptional(registration
                            .productDescription()), MerchantStatus.DRAFT, null, null, 0L, now, null);
    }

    public Merchant changeStatus(MerchantStatus newStatus, String reason, LocalDateTime now) {
        if (newStatus == status) {
            throw new MerchantDomainException("Merchant is already in the requested lifecycle state");
        }
        String normalizedReason = normalizeOptional(reason);
        if (newStatus == MerchantStatus.DISABLED && normalizedReason == null) {
            throw new MerchantDomainException("Disable reason is required");
        }
        return new Merchant(id, tenantId, owningAgentId, merchantNo, merchantType, legalName, shortName, legalSubjectHash, operatorUserId, reviewerUserId, contactName, contactMobile, industry, productDescription, newStatus, newStatus == MerchantStatus.DISABLED
            ? normalizedReason
            : null, certifiedKycVersionId, rowVersion + 1, createTime, now);
    }

    public boolean isDirectIdentity(Long userId) {
        return operatorUserId.equals(userId) || reviewerUserId.equals(userId);
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
