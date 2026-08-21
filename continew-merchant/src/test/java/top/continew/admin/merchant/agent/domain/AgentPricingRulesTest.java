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

package top.continew.admin.merchant.agent.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentPricingRulesTest {

    private final AgentPricingRules parent = rules("0.0100", "0.50", "0.60");

    @Test
    void subordinateWithinAllParentBoundsIsAccepted() {
        assertDoesNotThrow(() -> rules("0.0120", "0.75", "0.40").requireWithin(parent));
    }

    @Test
    void subordinateCannotUndercutParentCostsOrExceedProfitShare() {
        assertThrows(AgentPricingBoundaryException.class, () -> rules("0.0099", "0.75", "0.40").requireWithin(parent));
        assertThrows(AgentPricingBoundaryException.class, () -> rules("0.0120", "0.49", "0.40").requireWithin(parent));
        assertThrows(AgentPricingBoundaryException.class, () -> rules("0.0120", "0.75", "0.61").requireWithin(parent));
    }

    @Test
    void invalidRangeAndPrecisionAreRejected() {
        assertThrows(AgentDomainException.class, () -> rules("1.00000001", "0.50", "0.60"));
        assertThrows(AgentDomainException.class, () -> rules("0.0100", "0.501", "0.60"));
        assertThrows(AgentDomainException.class, () -> rules("0.0100", "0.50", "-0.01"));
    }

    private AgentPricingRules rules(String percentage, String fixed, String share) {
        return new AgentPricingRules(new BigDecimal(percentage), new BigDecimal(fixed), new BigDecimal(share));
    }
}
