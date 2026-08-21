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

import java.util.Locale;

/** Merchant registration after ContiNew operator/reviewer identities have been resolved. */
public record MerchantRegistration(Long id, Long tenantId, Long owningAgentId, String merchantNo,
                                   MerchantType merchantType, String legalName, String shortName,
                                   String legalSubjectHash, Long operatorUserId, Long reviewerUserId,
                                   EncryptedMobileNumber reviewerMobile, String contactName,
                                   EncryptedMobileNumber contactMobile, String industry, String productDescription) {

    public MerchantRegistration(Long id,
                                Long tenantId,
                                Long owningAgentId,
                                String merchantNo,
                                MerchantType merchantType,
                                String legalName,
                                String shortName,
                                String legalSubjectHash,
                                Long operatorUserId,
                                Long reviewerUserId,
                                String contactName,
                                EncryptedMobileNumber contactMobile,
                                String industry,
                                String productDescription) {
        this(id, tenantId, owningAgentId, merchantNo, merchantType, legalName, shortName, legalSubjectHash, operatorUserId, reviewerUserId, null, contactName, contactMobile, industry, productDescription);
    }

    public MerchantRegistration {
        requirePositive(id, "id");
        requirePositive(tenantId, "tenantId");
        requirePositive(owningAgentId, "owningAgentId");
        requirePositive(operatorUserId, "operatorUserId");
        requirePositive(reviewerUserId, "reviewerUserId");
        if (operatorUserId.equals(reviewerUserId)) {
            throw new IllegalArgumentException("Operator and reviewer identities must be distinct");
        }
        requireText(merchantNo, 64, "merchantNo");
        requireText(legalName, 200, "legalName");
        requireText(shortName, 100, "shortName");
        if (merchantType == null) {
            throw new IllegalArgumentException("merchantType must not be null");
        }
        optionalText(industry, 100, "industry");
        optionalText(productDescription, 255, "productDescription");
        if (legalSubjectHash != null && !legalSubjectHash.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("legalSubjectHash must be a SHA-256/HMAC hex value");
        }
        legalSubjectHash = legalSubjectHash == null ? null : legalSubjectHash.toLowerCase(Locale.ROOT);
    }

    private static void requirePositive(Long value, String name) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requireText(String value, int maxLength, String name) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new IllegalArgumentException(name + " is invalid");
        }
    }

    private static void optionalText(String value, int maxLength, String name) {
        if (value != null && value.trim().length() > maxLength) {
            throw new IllegalArgumentException(name + " is too long");
        }
    }
}
