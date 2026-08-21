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

package top.continew.admin.merchant.security.reveal;

/** Sensitive reveal request from an authenticated channel. */
public record PrivilegedRevealCommand(Long tenantId, Long actorUserId, Long merchantId, MerchantSensitiveField field,
                                      String reason, String encryptedPasswordProof, String ipAddress) {

    public PrivilegedRevealCommand {
        requirePositive(tenantId, "tenantId");
        requirePositive(actorUserId, "actorUserId");
        requirePositive(merchantId, "merchantId");
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        requireText(reason, 255, "reason");
        requireText(encryptedPasswordProof, 4096, "encryptedPasswordProof");
        if (ipAddress != null && ipAddress.length() > 64) {
            throw new IllegalArgumentException("ipAddress is too long");
        }
        reason = reason.trim();
        ipAddress = ipAddress == null || ipAddress.isBlank() ? null : ipAddress.trim();
    }

    @Override
    public String toString() {
        return "PrivilegedRevealCommand[tenantId=%s, actorUserId=%s, merchantId=%s, field=%s, proof=<redacted>]"
            .formatted(tenantId, actorUserId, merchantId, field);
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
}
