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

import java.math.BigDecimal;

/** Typed channel-product pricing values persisted as a versioned JSON document. */
public record AgentPricingRules(BigDecimal percentageCost, BigDecimal fixedFee, BigDecimal profitShareRatio) {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal MAX_FIXED_FEE = new BigDecimal("1000000000");

    public AgentPricingRules {
        percentageCost = requireRange(percentageCost, ZERO, ONE, 8, "percentageCost");
        fixedFee = requireRange(fixedFee, ZERO, MAX_FIXED_FEE, 2, "fixedFee");
        profitShareRatio = requireRange(profitShareRatio, ZERO, ONE, 8, "profitShareRatio");
    }

    /**
     * Cost values cannot undercut the parent's effective cost, while a child cannot receive a larger profit share
     * than its parent allocation.
     */
    public void requireWithin(AgentPricingRules parent) {
        if (parent == null) {
            throw new AgentPricingBoundaryException("Parent pricing version is required");
        }
        if (percentageCost.compareTo(parent.percentageCost) < 0) {
            throw new AgentPricingBoundaryException("Percentage cost is below the parent boundary");
        }
        if (fixedFee.compareTo(parent.fixedFee) < 0) {
            throw new AgentPricingBoundaryException("Fixed fee is below the parent boundary");
        }
        if (profitShareRatio.compareTo(parent.profitShareRatio) > 0) {
            throw new AgentPricingBoundaryException("Profit-share ratio exceeds the parent boundary");
        }
    }

    public String auditSummary() {
        return "percentage=%s,fixed=%s,share=%s".formatted(percentageCost.toPlainString(), fixedFee
            .toPlainString(), profitShareRatio.toPlainString());
    }

    private static BigDecimal requireRange(BigDecimal value,
                                           BigDecimal minimum,
                                           BigDecimal maximum,
                                           int maximumScale,
                                           String name) {
        if (value == null || value.compareTo(minimum) < 0 || value
            .compareTo(maximum) > 0 || normalizedScale(value) > maximumScale) {
            throw new AgentDomainException("Agent pricing " + name + " is invalid");
        }
        return value;
    }

    private static int normalizedScale(BigDecimal value) {
        return Math.max(0, value.stripTrailingZeros().scale());
    }
}
