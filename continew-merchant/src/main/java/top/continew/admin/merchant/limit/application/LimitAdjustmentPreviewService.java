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
import top.continew.admin.merchant.limit.domain.LimitAdjustmentPolicy;
import top.continew.admin.merchant.master.application.MerchantScopeAuthorizationService;
import top.continew.admin.merchant.master.domain.Merchant;
import top.continew.admin.merchant.master.domain.MerchantDomainException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;

/** Computes previews and revalidates explicit confirmations against the latest effective policy. */
@Service
@RequiredArgsConstructor
public class LimitAdjustmentPreviewService {

    private final LimitAdjustmentPolicyCatalog policyCatalog;
    private final LimitAdjustmentEligibilityPort eligibilityPort;
    private final MerchantScopeAuthorizationService merchantScopeAuthorizationService;
    private final LimitAdjustmentService adjustmentService;
    private final Clock clock = Clock.systemDefaultZone();

    public LimitAdjustmentPreview preview(LimitAdjustmentPreviewCommand command) {
        if (command == null || command.tenantId() == null || command.actorUserId() == null || command
            .merchantId() == null) {
            throw new IllegalArgumentException("Limit adjustment preview identity is invalid");
        }
        String channelCode = code(command.channelCode(), "channelCode");
        String platformCode = code(command.platformCode(), "platformCode");
        String currency = code(command.currency(), "currency");
        if (currency.length() != 3) {
            throw new IllegalArgumentException("Limit adjustment preview currency is invalid");
        }
        Merchant merchant = merchantScopeAuthorizationService.requireAccessible(command.tenantId(), command
            .actorUserId(), command.merchantId());
        LocalDateTime now = LocalDateTime.now(clock);
        eligibilityPort.requireEligible(command.tenantId(), merchant, channelCode, now);
        LimitAdjustmentPolicy policy = policyCatalog.findEffective(command
            .tenantId(), channelCode, platformCode, currency, now)
            .orElseThrow(() -> new MerchantDomainException("No effective limit adjustment policy"));
        LimitAdjustmentPolicy.Normalization normalization = policy.normalize(command.requestedLimit());
        return new LimitAdjustmentPreview(command.merchantId(), channelCode, platformCode, currency, normalization
            .requestedLimit(), normalization.normalizedLimit(), normalization.changed(), policy.minimumLimit(), policy
                .maximumLimit(), policy.currencyScale(), policy.roundingUnit(), policy.roundingMode(), policy
                    .policyVersion());
    }

    public LimitAdjustmentCreateResult confirm(LimitAdjustmentConfirmCommand command) {
        if (command == null || command.confirmedNormalizedLimit() == null || command.confirmedPolicyVersion() == null) {
            throw new MerchantDomainException("Limit adjustment confirmation is invalid");
        }
        LimitAdjustmentPreview preview = preview(new LimitAdjustmentPreviewCommand(command.tenantId(), command
            .actorUserId(), command.merchantId(), command.channelCode(), command.platformCode(), command
                .currency(), command.requestedLimit()));
        if (!preview.policyVersion().equals(command.confirmedPolicyVersion().trim()) || preview.normalizedLimit()
            .compareTo(command.confirmedNormalizedLimit()) != 0) {
            throw new MerchantDomainException("Limit adjustment preview is stale or tampered");
        }
        return adjustmentService.create(new LimitAdjustmentCreateCommand(command.tenantId(), command
            .actorUserId(), command.merchantId(), preview.channelCode(), preview.platformCode(), preview
                .currency(), preview.requestedLimit(), preview.normalizedLimit(), preview.policyVersion(), command
                    .reason(), command.ipAddress()));
    }

    private String code(String value, String name) {
        String normalized = value == null ? null : value.trim().toUpperCase(Locale.ROOT);
        if (normalized == null || !normalized.matches("[A-Z0-9][A-Z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("Limit adjustment preview " + name + " is invalid");
        }
        return normalized;
    }
}
