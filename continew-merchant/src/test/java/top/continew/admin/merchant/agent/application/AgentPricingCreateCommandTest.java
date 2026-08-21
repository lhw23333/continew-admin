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

import org.junit.jupiter.api.Test;
import top.continew.admin.merchant.agent.domain.AgentDomainException;
import top.continew.admin.merchant.agent.domain.AgentPricingRules;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentPricingCreateCommandTest {

    @Test
    void dimensionCodesAreNormalizedAndLongNumbersAreRedactedFromAuditReason() {
        AgentPricingCreateCommand command = new AgentPricingCreateCommand(1L, 2L, 3L, " channel-a ", " product-a ", "cny", rules(), LocalDateTime
            .of(2026, 8, 22, 9, 0), null, "contact 13800138000\n approved", "127.0.0.1");

        assertEquals("CHANNEL-A", command.channelCode());
        assertEquals("PRODUCT-A", command.productCode());
        assertEquals("CNY", command.currency());
        assertEquals("contact [REDACTED] approved", command.reason());
    }

    @Test
    void expiryMustBeAfterEffectiveTime() {
        LocalDateTime effectiveTime = LocalDateTime.of(2026, 8, 22, 9, 0);
        assertThrows(AgentDomainException.class, () -> new AgentPricingCreateCommand(1L, 2L, 3L, "CHANNEL-A", "PRODUCT-A", "CNY", rules(), effectiveTime, effectiveTime
            .minusSeconds(1), "invalid expiry", "127.0.0.1"));
    }

    private AgentPricingRules rules() {
        return new AgentPricingRules(new BigDecimal("0.0100"), new BigDecimal("0.50"), new BigDecimal("0.60"));
    }
}
