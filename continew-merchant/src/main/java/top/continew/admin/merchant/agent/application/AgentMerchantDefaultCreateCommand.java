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
import top.continew.admin.merchant.agent.domain.AgentMerchantDefaultProduct;

import java.time.LocalDateTime;
import java.util.List;

/** Scoped command for publishing an immutable merchant-default version. */
public record AgentMerchantDefaultCreateCommand(Long tenantId, Long actorUserId, Long agentId,
                                                List<AgentMerchantDefaultProduct> products, LocalDateTime effectiveTime,
                                                LocalDateTime expiresTime, String reason, String ipAddress) {

    public AgentMerchantDefaultCreateCommand {
        requirePositive(tenantId, "tenantId");
        requirePositive(actorUserId, "actorUserId");
        requirePositive(agentId, "agentId");
        if (products == null) {
            throw new AgentDomainException("Merchant-default products are required");
        }
        if (products.stream().anyMatch(product -> product == null)) {
            throw new AgentDomainException("Merchant-default product must not be null");
        }
        products = products.stream()
            .map(product -> new AgentMerchantDefaultProduct(AgentPricingCreateCommand.normalizeCode(product
                .channelCode(), "channelCode"), AgentPricingCreateCommand.normalizeCode(product
                    .productCode(), "productCode"), product.pricingVersionId()))
            .toList();
        if (effectiveTime == null) {
            throw new AgentDomainException("Merchant-default effective time is required");
        }
        if (expiresTime != null && !expiresTime.isAfter(effectiveTime)) {
            throw new AgentDomainException("Merchant-default expiry must be after the effective time");
        }
        reason = sanitizeReason(reason);
    }

    private static String sanitizeReason(String value) {
        if (value == null || value.isBlank()) {
            throw new AgentDomainException("Merchant-default change reason is required");
        }
        String normalized = value.replaceAll("[\\p{Cntrl}]", " ")
            .replaceAll("(?<!\\d)\\d{7,}(?!\\d)", "[REDACTED]")
            .replaceAll("\\s+", " ")
            .trim();
        if (normalized.length() > 255) {
            throw new AgentDomainException("Merchant-default change reason is too long");
        }
        return normalized;
    }

    private static void requirePositive(Long value, String name) {
        if (value == null || value <= 0) {
            throw new AgentDomainException(name + " must be positive");
        }
    }
}
