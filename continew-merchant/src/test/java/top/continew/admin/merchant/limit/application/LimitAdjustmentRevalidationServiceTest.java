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
import top.continew.admin.merchant.limit.domain.LimitAdjustment;
import top.continew.admin.merchant.limit.domain.LimitAdjustmentPolicy;
import top.continew.admin.merchant.limit.domain.LimitAdjustmentPolicyStatus;
import top.continew.admin.merchant.limit.domain.LimitApprovalStatus;
import top.continew.admin.merchant.limit.domain.LimitChannelStatus;
import top.continew.admin.merchant.limit.domain.LimitEffectiveStatus;
import top.continew.admin.merchant.limit.domain.LimitRoundingMode;
import top.continew.admin.merchant.master.domain.Merchant;
import top.continew.admin.merchant.master.domain.MerchantRegistration;
import top.continew.admin.merchant.master.domain.MerchantStatus;
import top.continew.admin.merchant.master.domain.MerchantType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LimitAdjustmentRevalidationServiceTest {

    private static final LocalDateTime CURRENT_TIME = LocalDateTime.of(2026, 8, 28, 9, 0);

    private StubRepository repository;
    private MutableEligibility eligibility;
    private MutablePolicyCatalog policies;
    private LimitAdjustmentRevalidationService service;

    @BeforeEach
    void setUp() {
        repository = new StubRepository();
        eligibility = new MutableEligibility();
        policies = new MutablePolicyCatalog();
        service = new LimitAdjustmentRevalidationService(repository, eligibility, policies);
    }

    @Test
    void acceptsUnchangedBaselineAndConfigurationEvidence() {
        LimitAdjustmentRevalidationService.Snapshot snapshot = service
            .requireCurrent(request(), merchant(), CURRENT_TIME);

        assertEquals(new BigDecimal("500.00"), snapshot.effectiveLimit());
        assertEquals("ELIGIBILITY-V1", snapshot.eligibility().eligibilityVersion());
        assertEquals("POLICY-V1", snapshot.policy().policyVersion());
    }

    @Test
    void rejectsChangedEffectiveLimitBeforeApproval() {
        repository.currentLimit = new BigDecimal("800.00");

        LimitAdjustmentRevalidationException exception = assertThrows(LimitAdjustmentRevalidationException.class, () -> service
            .requireCurrent(request(), merchant(), CURRENT_TIME));

        assertEquals(LimitAdjustmentRevalidationException.Code.EFFECTIVE_LIMIT_CHANGED, exception.code());
    }

    @Test
    void rejectsChangedChannelConfigurationAndPolicyVersions() {
        eligibility.value = new LimitAdjustmentEligibility(7001L, "PRODUCT-A", "ELIGIBILITY-V1", "CONNECTION-V2");
        LimitAdjustmentRevalidationException channelConflict = assertThrows(LimitAdjustmentRevalidationException.class, () -> service
            .requireCurrent(request(), merchant(), CURRENT_TIME));
        assertEquals(LimitAdjustmentRevalidationException.Code.CHANNEL_CONFIGURATION_CHANGED, channelConflict.code());

        eligibility.value = new LimitAdjustmentEligibility(7001L, "PRODUCT-A", "ELIGIBILITY-V1", "CONNECTION-V1");
        policies.value = policy("POLICY-V2", new BigDecimal("1000.00"));
        LimitAdjustmentRevalidationException policyConflict = assertThrows(LimitAdjustmentRevalidationException.class, () -> service
            .requireCurrent(request(), merchant(), CURRENT_TIME));
        assertEquals(LimitAdjustmentRevalidationException.Code.AMOUNT_POLICY_CHANGED, policyConflict.code());
    }

    @Test
    void rejectsChangedNormalizationUnderSamePolicyReference() {
        policies.value = policy("POLICY-V1", new BigDecimal("3000.00"));

        LimitAdjustmentRevalidationException exception = assertThrows(LimitAdjustmentRevalidationException.class, () -> service
            .requireCurrent(request(), merchant(), CURRENT_TIME));

        assertEquals(LimitAdjustmentRevalidationException.Code.NORMALIZED_LIMIT_CHANGED, exception.code());
    }

    private LimitAdjustment request() {
        return new LimitAdjustment(6001L, 1301L, "LA6001", 2301L, 4301L, "CHANNEL-A", "INBOUND", "CNY", new BigDecimal("500.00"), new BigDecimal("1250.00"), new BigDecimal("2000.00"), null, "capacity", "ELIGIBILITY-V1", "CONNECTION-V1", "POLICY-V1", "PROCESS-1", LimitApprovalStatus.PENDING, LimitChannelStatus.NOT_SUBMITTED, LimitEffectiveStatus.NOT_EFFECTIVE, "ACTIVE", 3301L, CURRENT_TIME
            .minusHours(1), null, null, null, null, null, 1L, CURRENT_TIME.minusHours(1), CURRENT_TIME
                .minusMinutes(30));
    }

    private Merchant merchant() {
        Merchant created = Merchant
            .create(new MerchantRegistration(2301L, 1301L, 4301L, "M-2301", MerchantType.ENTERPRISE, "Limit Merchant", "Limit", "a"
                .repeat(64), 3301L, 3302L, "Contact", null, "Technology", "Limit merchant"), CURRENT_TIME.minusDays(1));
        return created.changeStatus(MerchantStatus.ENABLED, null, CURRENT_TIME.minusDays(1).plusMinutes(1));
    }

    private LimitAdjustmentPolicy policy(String version, BigDecimal unit) {
        return new LimitAdjustmentPolicy(8001L, 1301L, "CHANNEL-A", "INBOUND", "CNY", version, new BigDecimal("1000.00"), new BigDecimal("10000.00"), 2, unit, LimitRoundingMode.CEILING, LimitAdjustmentPolicyStatus.ENABLED, CURRENT_TIME
            .minusDays(1), null, CURRENT_TIME.minusDays(1));
    }

    private final class MutableEligibility implements LimitAdjustmentEligibilityPort {
        private LimitAdjustmentEligibility value = new LimitAdjustmentEligibility(7001L, "PRODUCT-A", "ELIGIBILITY-V1", "CONNECTION-V1");

        @Override
        public LimitAdjustmentEligibility requireEligible(Long tenantId,
                                                          Merchant merchant,
                                                          String channelCode,
                                                          LocalDateTime effectiveAt) {
            return value;
        }
    }

    private final class MutablePolicyCatalog implements LimitAdjustmentPolicyCatalog {
        private LimitAdjustmentPolicy value = policy("POLICY-V1", new BigDecimal("1000.00"));

        @Override
        public Optional<LimitAdjustmentPolicy> findEffective(Long tenantId,
                                                             String channelCode,
                                                             String platformCode,
                                                             String currency,
                                                             LocalDateTime effectiveAt) {
            return Optional.of(value);
        }
    }

    private static final class StubRepository implements LimitAdjustmentRepository {
        private BigDecimal currentLimit = new BigDecimal("500.00");

        @Override
        public Optional<BigDecimal> findCurrentEffectiveLimit(Long tenantId,
                                                              Long merchantId,
                                                              String channelCode,
                                                              String platformCode,
                                                              String currency) {
            return Optional.of(currentLimit);
        }

        @Override
        public Optional<LimitAdjustment> findActive(Long tenantId,
                                                    Long merchantId,
                                                    String channelCode,
                                                    String platformCode) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<LimitAdjustment> findById(Long tenantId, Long merchantId, Long requestId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<LimitAdjustment> findByRequestId(Long tenantId, Long requestId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LimitAdjustment insert(LimitAdjustmentDraft draft) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LimitAdjustment bindWorkflow(Long tenantId,
                                            Long requestId,
                                            Long expectedVersion,
                                            String processInstanceId,
                                            Long actorUserId,
                                            LocalDateTime updateTime) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LimitAdjustment applyReviewDecision(Long tenantId,
                                                   Long requestId,
                                                   Long expectedVersion,
                                                   LimitApprovalStatus approvalStatus,
                                                   String opinion,
                                                   Long actorUserId,
                                                   LocalDateTime approvalTime) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LimitAdjustment applyChannelResult(Long tenantId,
                                                  Long requestId,
                                                  Long expectedVersion,
                                                  LimitChannelStatus channelStatus,
                                                  LimitEffectiveStatus effectiveStatus,
                                                  BigDecimal effectiveLimit,
                                                  LocalDateTime effectiveTime,
                                                  String channelResultCode,
                                                  String channelResultMessage,
                                                  Long actorUserId,
                                                  LocalDateTime updateTime) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void appendHistory(LimitAdjustmentHistoryDraft draft) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<LimitAdjustmentHistory> listHistory(Long tenantId, Long requestId) {
            throw new UnsupportedOperationException();
        }
    }
}