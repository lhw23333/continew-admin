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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.continew.admin.merchant.limit.domain.LimitAdjustmentPolicy;
import top.continew.admin.merchant.limit.domain.LimitAdjustmentPolicyStatus;
import top.continew.admin.merchant.limit.domain.LimitRoundingMode;
import top.continew.admin.merchant.master.application.MerchantScopeAuthorizationService;
import top.continew.admin.merchant.master.domain.Merchant;
import top.continew.admin.merchant.master.domain.MerchantDomainException;
import top.continew.admin.merchant.master.domain.MerchantRegistration;
import top.continew.admin.merchant.master.domain.MerchantStatus;
import top.continew.admin.merchant.master.domain.MerchantType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LimitAdjustmentPreviewServiceTest {

    private static final Long TENANT_ID = 1401L;
    private static final Long MERCHANT_ID = 2401L;
    private static final Long ACTOR_ID = 3401L;

    private MutablePolicyCatalog catalog;
    private CapturingAdjustmentService adjustmentService;
    private LimitAdjustmentPreviewService service;

    @BeforeEach
    void setUp() {
        catalog = new MutablePolicyCatalog(policy(new BigDecimal("10000.00")));
        adjustmentService = new CapturingAdjustmentService();
        LimitAdjustmentEligibilityPort eligibility = (tenantId,
                                                      merchant,
                                                      channelCode,
                                                      effectiveAt) -> new LimitAdjustmentEligibility(5401L, "PRODUCT-A", "ELIGIBILITY-V1", "CONNECTION-V1");
        service = new LimitAdjustmentPreviewService(catalog, eligibility, new AllowingAuthorization(merchant()), adjustmentService);
    }

    @Test
    void previewShowsEnteredAndThousandCeilingValues() {
        LimitAdjustmentPreview preview = service.preview(command(new BigDecimal("1250.00")));

        assertEquals(new BigDecimal("1250.00"), preview.requestedLimit());
        assertEquals(new BigDecimal("2000.00"), preview.normalizedLimit());
        assertTrue(preview.changed());
        assertEquals(new BigDecimal("1000.00"), preview.minimumLimit());
        assertEquals(new BigDecimal("10000.00"), preview.maximumLimit());
        assertEquals(2, preview.currencyScale());
        assertEquals(LimitRoundingMode.CEILING, preview.roundingMode());
        assertFalse(service.preview(command(new BigDecimal("2000.00"))).changed());
    }

    @Test
    void precisionRangeAndRoundedMaximumAreEnforced() {
        assertThrows(MerchantDomainException.class, () -> service.preview(command(new BigDecimal("999.00"))));
        assertThrows(MerchantDomainException.class, () -> service.preview(command(new BigDecimal("10000.01"))));
        assertThrows(MerchantDomainException.class, () -> service.preview(command(new BigDecimal("1250.001"))));

        catalog.policy = policy(new BigDecimal("9500.00"));
        assertThrows(MerchantDomainException.class, () -> service.preview(command(new BigDecimal("9500.00"))));
    }

    @Test
    void confirmationRecalculatesAndRejectsStaleOrTamperedPreview() {
        LimitAdjustmentPreview preview = service.preview(command(new BigDecimal("1250.00")));
        LimitAdjustmentConfirmCommand confirmed = confirmation(preview.normalizedLimit(), preview.policyVersion());

        service.confirm(confirmed);
        assertEquals(new BigDecimal("1250.00"), adjustmentService.command.requestedLimit());
        assertEquals(new BigDecimal("2000.00"), adjustmentService.command.normalizedLimit());

        adjustmentService.command = null;
        assertThrows(MerchantDomainException.class, () -> service
            .confirm(confirmation(new BigDecimal("3000.00"), preview.policyVersion())));
        assertThrows(MerchantDomainException.class, () -> service.confirm(confirmation(preview
            .normalizedLimit(), "POLICY-V0")));
        assertEquals(null, adjustmentService.command);
    }

    private LimitAdjustmentPreviewCommand command(BigDecimal requested) {
        return new LimitAdjustmentPreviewCommand(TENANT_ID, ACTOR_ID, MERCHANT_ID, "channel-a", "inbound", "cny", requested);
    }

    private LimitAdjustmentConfirmCommand confirmation(BigDecimal normalized, String policyVersion) {
        return new LimitAdjustmentConfirmCommand(TENANT_ID, ACTOR_ID, MERCHANT_ID, "CHANNEL-A", "INBOUND", "CNY", new BigDecimal("1250.00"), normalized, policyVersion, "Capacity expansion", "127.0.0.1");
    }

    private LimitAdjustmentPolicy policy(BigDecimal maximum) {
        LocalDateTime now = LocalDateTime.of(2026, 8, 27, 10, 0);
        return new LimitAdjustmentPolicy(6401L, TENANT_ID, "CHANNEL-A", "INBOUND", "CNY", "POLICY-V1", new BigDecimal("1000.00"), maximum, 2, new BigDecimal("1000.00"), LimitRoundingMode.CEILING, LimitAdjustmentPolicyStatus.ENABLED, now
            .minusDays(1), null, now.minusDays(2));
    }

    private Merchant merchant() {
        Merchant created = Merchant
            .create(new MerchantRegistration(MERCHANT_ID, TENANT_ID, 4401L, "M-2401", MerchantType.ENTERPRISE, "Preview Merchant", "Preview", "b"
                .repeat(64), ACTOR_ID, 3402L, "Contact", null, "Technology", "Preview merchant"), LocalDateTime
                    .of(2026, 8, 27, 9, 0));
        return created.changeStatus(MerchantStatus.ENABLED, null, LocalDateTime.of(2026, 8, 27, 9, 1));
    }

    private static final class MutablePolicyCatalog implements LimitAdjustmentPolicyCatalog {
        private LimitAdjustmentPolicy policy;

        private MutablePolicyCatalog(LimitAdjustmentPolicy policy) {
            this.policy = policy;
        }

        @Override
        public Optional<LimitAdjustmentPolicy> findEffective(Long tenantId,
                                                             String channelCode,
                                                             String platformCode,
                                                             String currency,
                                                             LocalDateTime effectiveAt) {
            return Optional.of(policy);
        }
    }

    private static final class AllowingAuthorization extends MerchantScopeAuthorizationService {
        private final Merchant merchant;

        private AllowingAuthorization(Merchant merchant) {
            super(null, null);
            this.merchant = merchant;
        }

        @Override
        public Merchant requireAccessible(Long tenantId, Long actorUserId, Long merchantId) {
            return merchant;
        }
    }

    private static final class CapturingAdjustmentService extends LimitAdjustmentService {
        private LimitAdjustmentCreateCommand command;

        private CapturingAdjustmentService() {
            super(null, null, null, null, null);
        }

        @Override
        public LimitAdjustmentCreateResult create(LimitAdjustmentCreateCommand command) {
            this.command = command;
            return new LimitAdjustmentCreateResult(null, true);
        }
    }
}
