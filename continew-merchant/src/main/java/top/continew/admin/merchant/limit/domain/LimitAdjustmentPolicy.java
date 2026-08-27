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

import top.continew.admin.merchant.master.domain.MerchantDomainException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Locale;

/** Append-only amount validation and upward-rounding policy for one limit dimension. */
public record LimitAdjustmentPolicy(Long id, Long tenantId, String channelCode, String platformCode, String currency,
                                    String policyVersion, BigDecimal minimumLimit, BigDecimal maximumLimit,
                                    int currencyScale, BigDecimal roundingUnit, LimitRoundingMode roundingMode,
                                    LimitAdjustmentPolicyStatus status, LocalDateTime effectiveTime,
                                    LocalDateTime expiresTime, LocalDateTime createTime) {

    public LimitAdjustmentPolicy {
        if (id == null || id <= 0 || tenantId == null || tenantId <= 0 || currencyScale < 0 || currencyScale > 2 || roundingMode == null || status == null || effectiveTime == null || createTime == null) {
            throw new IllegalArgumentException("Limit adjustment policy is invalid");
        }
        channelCode = code(channelCode, "channelCode");
        platformCode = code(platformCode, "platformCode");
        currency = code(currency, "currency");
        if (currency.length() != 3) {
            throw new IllegalArgumentException("Limit adjustment policy currency is invalid");
        }
        policyVersion = code(policyVersion, "policyVersion");
        minimumLimit = amount(minimumLimit, "minimumLimit");
        maximumLimit = amount(maximumLimit, "maximumLimit");
        roundingUnit = amount(roundingUnit, "roundingUnit");
        if (maximumLimit.compareTo(minimumLimit) < 0 || (expiresTime != null && !expiresTime.isAfter(effectiveTime))) {
            throw new IllegalArgumentException("Limit adjustment policy range or time is invalid");
        }
    }

    public Normalization normalize(BigDecimal entered) {
        if (!LimitAdjustmentPolicyStatus.ENABLED.equals(status) || entered == null || entered
            .signum() <= 0 || normalizedScale(entered) > currencyScale || entered.precision() > 20 || entered
                .compareTo(minimumLimit) < 0 || entered.compareTo(maximumLimit) > 0) {
            throw new MerchantDomainException("Requested limit does not satisfy the effective policy");
        }
        BigDecimal normalized = entered.divide(roundingUnit, 0, RoundingMode.CEILING)
            .multiply(roundingUnit)
            .setScale(currencyScale);
        if (normalized.compareTo(maximumLimit) > 0) {
            throw new MerchantDomainException("Rounded limit exceeds the effective policy maximum");
        }
        BigDecimal requested = entered.setScale(currencyScale);
        return new Normalization(requested, normalized, requested.compareTo(normalized) != 0);
    }

    private static int normalizedScale(BigDecimal value) {
        return Math.max(0, value.stripTrailingZeros().scale());
    }

    private static BigDecimal amount(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0 || value.scale() > 2 || value.precision() > 20) {
            throw new IllegalArgumentException("Limit adjustment policy " + name + " is invalid");
        }
        return value.setScale(2);
    }

    private static String code(String value, String name) {
        String normalized = value == null ? null : value.trim().toUpperCase(Locale.ROOT);
        if (normalized == null || !normalized.matches("[A-Z0-9][A-Z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("Limit adjustment policy " + name + " is invalid");
        }
        return normalized;
    }

    public record Normalization(BigDecimal requestedLimit, BigDecimal normalizedLimit, boolean changed) {
    }
}
