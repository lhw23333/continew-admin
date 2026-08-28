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

package top.continew.admin.merchant.limit.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.continew.admin.merchant.limit.domain.LimitAdjustment;
import top.continew.admin.merchant.limit.domain.LimitAdjustmentPolicy;
import top.continew.admin.merchant.master.domain.Merchant;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Rechecks server-owned baseline and configuration evidence before irreversible limit actions. */
@Service
@RequiredArgsConstructor
public class LimitAdjustmentRevalidationService {

    private final LimitAdjustmentRepository repository;
    private final LimitAdjustmentEligibilityPort eligibilityPort;
    private final LimitAdjustmentPolicyCatalog policyCatalog;

    public Snapshot requireCurrent(LimitAdjustment request, Merchant merchant, LocalDateTime effectiveAt) {
        BigDecimal currentLimit = repository.findCurrentEffectiveLimit(request.tenantId(), request.merchantId(), request
            .channelCode(), request.platformCode(), request.currency()).orElse(BigDecimal.ZERO).setScale(2);
        if (currentLimit.compareTo(request.originalLimit()) != 0) {
            throw conflict(LimitAdjustmentRevalidationException.Code.EFFECTIVE_LIMIT_CHANGED);
        }
        LimitAdjustmentEligibility eligibility = eligibilityPort.requireEligible(request.tenantId(), merchant, request
            .channelCode(), effectiveAt);
        if (!request.eligibilityVersion().equals(eligibility.eligibilityVersion())) {
            throw conflict(LimitAdjustmentRevalidationException.Code.ELIGIBILITY_CHANGED);
        }
        if (!request.channelConfigVersion().equals(eligibility.channelConfigVersion())) {
            throw conflict(LimitAdjustmentRevalidationException.Code.CHANNEL_CONFIGURATION_CHANGED);
        }
        LimitAdjustmentPolicy policy = policyCatalog.findEffective(request.tenantId(), request.channelCode(), request
            .platformCode(), request.currency(), effectiveAt)
            .orElseThrow(() -> conflict(LimitAdjustmentRevalidationException.Code.AMOUNT_POLICY_CHANGED));
        if (!request.amountPolicyVersion().equals(policy.policyVersion())) {
            throw conflict(LimitAdjustmentRevalidationException.Code.AMOUNT_POLICY_CHANGED);
        }
        LimitAdjustmentPolicy.Normalization normalization = policy.normalize(request.requestedLimit());
        if (normalization.normalizedLimit().compareTo(request.normalizedLimit()) != 0) {
            throw conflict(LimitAdjustmentRevalidationException.Code.NORMALIZED_LIMIT_CHANGED);
        }
        return new Snapshot(eligibility, policy, currentLimit);
    }

    private LimitAdjustmentRevalidationException conflict(LimitAdjustmentRevalidationException.Code code) {
        return new LimitAdjustmentRevalidationException(code);
    }

    public record Snapshot(LimitAdjustmentEligibility eligibility, LimitAdjustmentPolicy policy,
                           BigDecimal effectiveLimit) {
    }
}