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

package top.continew.admin.merchant.agent.application;

import top.continew.admin.merchant.agent.domain.AgentDomainException;
import top.continew.admin.merchant.agent.domain.AgentPricingRules;

import java.time.LocalDateTime;
import java.util.Locale;

/** Scoped command for publishing an immutable agent pricing version. */
public record AgentPricingCreateCommand(Long tenantId, Long actorUserId, Long agentId, String channelCode,
                                        String productCode, String currency, AgentPricingRules rules,
                                        LocalDateTime effectiveTime, LocalDateTime expiresTime, String reason,
                                        String ipAddress) {

    public AgentPricingCreateCommand {
        requirePositive(tenantId, "tenantId");
        requirePositive(actorUserId, "actorUserId");
        requirePositive(agentId, "agentId");
        channelCode = normalizeCode(channelCode, "channelCode");
        productCode = normalizeCode(productCode, "productCode");
        currency = normalizeCurrency(currency);
        if (rules == null || effectiveTime == null) {
            throw new AgentDomainException("Pricing rules and effective time are required");
        }
        if (expiresTime != null && !expiresTime.isAfter(effectiveTime)) {
            throw new AgentDomainException("Pricing expiry must be after the effective time");
        }
        reason = normalizeReason(reason);
    }

    public static String normalizeCode(String value, String name) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9][A-Z0-9_-]{0,63}")) {
            throw new AgentDomainException("Pricing " + name + " is invalid");
        }
        return normalized;
    }

    public static String normalizeCurrency(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{3}")) {
            throw new AgentDomainException("Pricing currency is invalid");
        }
        return normalized;
    }

    private static String normalizeReason(String value) {
        if (value == null || value.isBlank()) {
            throw new AgentDomainException("Pricing change reason is required");
        }
        String normalized = value.replaceAll("[\\p{Cntrl}]", " ")
            .replaceAll("(?<!\\d)\\d{7,}(?!\\d)", "[REDACTED]")
            .replaceAll("\\s+", " ")
            .trim();
        if (normalized.length() > 255) {
            throw new AgentDomainException("Pricing change reason is too long");
        }
        return normalized;
    }

    private static void requirePositive(Long value, String name) {
        if (value == null || value <= 0) {
            throw new AgentDomainException(name + " must be positive");
        }
    }
}
