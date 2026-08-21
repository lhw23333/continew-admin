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

package top.continew.admin.service.merchant;

import top.continew.admin.merchant.master.domain.MerchantType;

/** Sensitive merchant creation command; identities and ownership are resolved server-side. */
public record MerchantCreateCommand(Long tenantId, Long actorUserId, Long owningAgentId, MerchantType merchantType,
                                    String legalName, String shortName, String legalIdentifier, String contactName,
                                    String contactMobile, String reviewerMobile, String industry,
                                    String productDescription, String operatorTemporaryPassword,
                                    String reviewerTemporaryPassword, String ipAddress) {

    public MerchantCreateCommand {
        requirePositive(tenantId, "tenantId");
        requirePositive(actorUserId, "actorUserId");
        requirePositive(owningAgentId, "owningAgentId");
        if (merchantType == null) {
            throw new IllegalArgumentException("merchantType is required");
        }
        legalName = requireText(legalName, 200, "legalName");
        shortName = requireText(shortName, 100, "shortName");
        legalIdentifier = requireText(legalIdentifier, 64, "legalIdentifier");
        contactName = requireText(contactName, 100, "contactName");
        contactMobile = requireText(contactMobile, 32, "contactMobile");
        reviewerMobile = requireText(reviewerMobile, 32, "reviewerMobile");
        industry = optionalText(industry, 100, "industry");
        productDescription = optionalText(productDescription, 255, "productDescription");
        operatorTemporaryPassword = requireText(operatorTemporaryPassword, 255, "operatorTemporaryPassword");
        reviewerTemporaryPassword = requireText(reviewerTemporaryPassword, 255, "reviewerTemporaryPassword");
    }

    @Override
    public String toString() {
        return "MerchantCreateCommand[tenantId=%s, actorUserId=%s, owningAgentId=%s, merchantType=%s, legalName=%s, legalIdentifier=<redacted>, contactMobile=<redacted>, reviewerMobile=<redacted>, passwords=<redacted>]"
            .formatted(tenantId, actorUserId, owningAgentId, merchantType, legalName);
    }

    private static void requirePositive(Long value, String name) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static String requireText(String value, int maxLength, String name) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value.trim();
    }

    private static String optionalText(String value, int maxLength, String name) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.trim().length() > maxLength) {
            throw new IllegalArgumentException(name + " is too long");
        }
        return value.trim();
    }
}
