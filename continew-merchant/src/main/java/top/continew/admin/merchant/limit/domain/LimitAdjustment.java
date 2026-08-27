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

package top.continew.admin.merchant.limit.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;

/** Versioned limit-adjustment aggregate with independent approval, channel, and effective states. */
public record LimitAdjustment(Long id, Long tenantId, String requestNo, Long merchantId, Long owningAgentId,
                              String channelCode, String platformCode, String currency, BigDecimal originalLimit,
                              BigDecimal requestedLimit, BigDecimal normalizedLimit, BigDecimal effectiveLimit,
                              String reason, String eligibilityVersion, String channelConfigVersion,
                              String amountPolicyVersion, String processInstanceId, LimitApprovalStatus approvalStatus,
                              LimitChannelStatus channelStatus, LimitEffectiveStatus effectiveStatus,
                              String activeRequestGuard, Long applicantId, LocalDateTime applicationTime,
                              LocalDateTime approvalTime, LocalDateTime effectiveTime, String opinion,
                              String channelResultCode, String channelResultMessage, Long rowVersion,
                              LocalDateTime createTime, LocalDateTime updateTime) {

    public LimitAdjustment {
        if (id == null || id <= 0 || tenantId == null || tenantId <= 0 || merchantId == null || merchantId <= 0 || owningAgentId == null || owningAgentId <= 0 || applicantId == null || applicantId <= 0 || approvalStatus == null || channelStatus == null || effectiveStatus == null || rowVersion == null || rowVersion < 0 || applicationTime == null || createTime == null) {
            throw new IllegalArgumentException("Required limit adjustment fields are invalid");
        }
        requestNo = text(requestNo, 64, "requestNo");
        channelCode = code(channelCode, "channelCode");
        platformCode = code(platformCode, "platformCode");
        currency = code(currency, "currency");
        if (currency.length() != 3) {
            throw new IllegalArgumentException("Limit adjustment currency is invalid");
        }
        originalLimit = amount(originalLimit, false, "originalLimit");
        requestedLimit = amount(requestedLimit, true, "requestedLimit");
        normalizedLimit = amount(normalizedLimit, true, "normalizedLimit");
        effectiveLimit = effectiveLimit == null ? null : amount(effectiveLimit, false, "effectiveLimit");
        if (normalizedLimit.compareTo(requestedLimit) < 0) {
            throw new IllegalArgumentException("Normalized limit must not be below requested limit");
        }
        reason = text(reason, 1000, "reason");
        eligibilityVersion = text(eligibilityVersion, 64, "eligibilityVersion");
        channelConfigVersion = text(channelConfigVersion, 64, "channelConfigVersion");
        amountPolicyVersion = text(amountPolicyVersion, 64, "amountPolicyVersion");
        activeRequestGuard = optional(activeRequestGuard, 32);
        if (LimitEffectiveStatus.EFFECTIVE
            .equals(effectiveStatus) && (effectiveLimit == null || effectiveTime == null)) {
            throw new IllegalArgumentException("Effective limit and time are required");
        }
    }

    public boolean active() {
        return "ACTIVE".equals(activeRequestGuard);
    }

    private static BigDecimal amount(BigDecimal value, boolean positive, String name) {
        if (value == null || value.scale() > 2 || (positive ? value.signum() <= 0 : value.signum() < 0) || value
            .precision() > 20) {
            throw new IllegalArgumentException("Limit adjustment " + name + " is invalid");
        }
        return value.setScale(2);
    }

    private static String code(String value, String name) {
        String normalized = value == null ? null : value.trim().toUpperCase(Locale.ROOT);
        if (normalized == null || !normalized.matches("[A-Z0-9][A-Z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("Limit adjustment " + name + " is invalid");
        }
        return normalized;
    }

    private static String text(String value, int maxLength, String name) {
        String normalized = optional(value, maxLength);
        if (normalized == null) {
            throw new IllegalArgumentException("Limit adjustment " + name + " is invalid");
        }
        return normalized;
    }

    private static String optional(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Limit adjustment text is invalid");
        }
        return normalized;
    }
}
