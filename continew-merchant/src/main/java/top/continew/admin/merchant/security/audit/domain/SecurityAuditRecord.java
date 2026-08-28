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

package top.continew.admin.merchant.security.audit.domain;

import java.time.LocalDateTime;

/** Sanitized append-only security event. Complete sensitive values are never accepted. */
public record SecurityAuditRecord(Long tenantId, Long actorUserId, Long actorAgentId, String action, String objectType,
                                  Long objectId, Long businessVersion, String fieldName, String reason,
                                  String ipAddress, SecurityAuditResult result, String failureCode,
                                  LocalDateTime createTime) {

    public SecurityAuditRecord {
        requireNonNegative(tenantId, "tenantId");
        requirePositive(actorUserId, "actorUserId");
        requirePositive(objectId, "objectId");
        requireText(action, 64, "action");
        requireText(objectType, 64, "objectType");
        optionalText(fieldName, 64, "fieldName");
        optionalText(reason, 255, "reason");
        optionalText(ipAddress, 64, "ipAddress");
        optionalText(failureCode, 64, "failureCode");
        if (result == null || createTime == null) {
            throw new IllegalArgumentException("Security audit result and time are required");
        }
    }

    private static void requireNonNegative(Long value, String name) {
        if (value == null || value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }

    private static void requirePositive(Long value, String name) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requireText(String value, int maxLength, String name) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(name + " is invalid");
        }
    }

    private static void optionalText(String value, int maxLength, String name) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(name + " is too long");
        }
    }
}
