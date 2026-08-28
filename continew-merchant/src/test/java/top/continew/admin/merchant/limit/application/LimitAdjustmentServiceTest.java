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

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.continew.admin.merchant.limit.domain.LimitAdjustment;
import top.continew.admin.merchant.limit.domain.LimitApprovalStatus;
import top.continew.admin.merchant.limit.domain.LimitChannelStatus;
import top.continew.admin.merchant.limit.domain.LimitEffectiveStatus;
import top.continew.admin.merchant.master.application.MerchantScopeAuthorizationService;
import top.continew.admin.merchant.master.domain.Merchant;
import top.continew.admin.merchant.master.domain.MerchantAccessDeniedException;
import top.continew.admin.merchant.master.domain.MerchantDomainException;
import top.continew.admin.merchant.master.domain.MerchantRegistration;
import top.continew.admin.merchant.master.domain.MerchantType;
import top.continew.admin.merchant.security.audit.application.SecurityAuditRepository;
import top.continew.admin.merchant.security.audit.application.SecurityAuditWriter;
import top.continew.admin.merchant.security.audit.domain.SecurityAuditRecord;
import top.continew.starter.extension.tenant.context.TenantContext;
import top.continew.starter.extension.tenant.context.TenantContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LimitAdjustmentServiceTest {

    private static final Long TENANT_ID = 1301L;
    private static final Long MERCHANT_ID = 2301L;
    private static final Long ACTOR_ID = 3301L;

    private InMemoryRepository repository;
    private MutableEligibility eligibility;
    private InMemoryAuditRepository auditRepository;
    private List<LimitAdjustmentWorkflowRequestDraft> workflowRequests;
    private LimitAdjustmentService service;

    @BeforeEach
    void setUp() {
        TenantContext context = new TenantContext();
        context.setTenantId(TENANT_ID);
        TenantContextHolder.setContext(context);
        repository = new InMemoryRepository();
        eligibility = new MutableEligibility();
        auditRepository = new InMemoryAuditRepository();
        workflowRequests = new ArrayList<>();
        AtomicLong ids = new AtomicLong(10_000);
        IdentifierGenerator identifierGenerator = entity -> ids.incrementAndGet();
        service = new LimitAdjustmentService(repository, eligibility, new AllowingAuthorization(merchant()), identifierGenerator, new SecurityAuditWriter(auditRepository), workflowRequests::add, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void createsVersionedRequestFromServerBaselineAndAppendsHistoryAudit() {
        repository.currentEffective = new BigDecimal("500.00");

        LimitAdjustmentCreateResult result = service.create(command());

        assertTrue(result.created());
        assertEquals(new BigDecimal("500.00"), result.request().originalLimit());
        assertEquals(new BigDecimal("1250.00"), result.request().requestedLimit());
        assertEquals(new BigDecimal("2000.00"), result.request().normalizedLimit());
        assertEquals(LimitApprovalStatus.PENDING, result.request().approvalStatus());
        assertEquals(LimitChannelStatus.NOT_SUBMITTED, result.request().channelStatus());
        assertEquals(LimitEffectiveStatus.NOT_EFFECTIVE, result.request().effectiveStatus());
        assertTrue(result.request().active());
        assertEquals("ELIGIBILITY-V1", result.request().eligibilityVersion());
        assertEquals("CONNECTION-V2", result.request().channelConfigVersion());
        assertEquals("CREATE", repository.history.get(0).action());
        assertEquals(0L, repository.history.get(0).requestVersion());
        assertEquals("LIMIT_ADJUSTMENT_CREATE", auditRepository.records.get(0).action());
        assertFalse(auditRepository.records.get(0).reason().contains("capacity expansion"));
        assertEquals(1, workflowRequests.size());
        assertEquals(result.request().id(), workflowRequests.get(0).requestId());
        assertFalse(workflowRequests.get(0).payloadJson().contains("capacity expansion"));
    }

    @Test
    void duplicateActiveDimensionReturnsExistingWithoutSecondSideEffects() {
        LimitAdjustmentCreateResult first = service.create(command());
        LimitAdjustmentCreateResult duplicate = service
            .create(new LimitAdjustmentCreateCommand(TENANT_ID, ACTOR_ID, MERCHANT_ID, "CHANNEL-A", "INBOUND", "CNY", new BigDecimal("3000.00"), new BigDecimal("3000.00"), "POLICY-V1", "different request", "127.0.0.1"));

        assertFalse(duplicate.created());
        assertEquals(first.request().id(), duplicate.request().id());
        assertEquals(1, repository.history.size());
        assertEquals(1, auditRepository.records.size());
        assertEquals(1, eligibility.calls);
    }

    @Test
    void ineligibleMerchantDoesNotCreateHistoryOrAudit() {
        eligibility.eligible = false;

        assertThrows(MerchantDomainException.class, () -> service.create(command()));
        assertTrue(repository.requests.isEmpty());
        assertTrue(repository.history.isEmpty());
        assertTrue(auditRepository.records.isEmpty());
    }

    @Test
    void historyRequiresRequestToBelongToTheAuthorizedMerchant() {
        LimitAdjustment request = service.create(command()).request();

        assertEquals(1, service.history(TENANT_ID, ACTOR_ID, MERCHANT_ID, request.id()).size());
        assertThrows(MerchantAccessDeniedException.class, () -> service
            .history(TENANT_ID, ACTOR_ID, MERCHANT_ID + 1, request.id()));
    }

    private LimitAdjustmentCreateCommand command() {
        return new LimitAdjustmentCreateCommand(TENANT_ID, ACTOR_ID, MERCHANT_ID, "channel-a", "inbound", "cny", new BigDecimal("1250.00"), new BigDecimal("2000.00"), "POLICY-V1", "capacity expansion", "127.0.0.1");
    }

    private Merchant merchant() {
        Merchant created = Merchant
            .create(new MerchantRegistration(MERCHANT_ID, TENANT_ID, 4301L, "M-2301", MerchantType.ENTERPRISE, "Limit Merchant", "Limit", "a"
                .repeat(64), ACTOR_ID, 3302L, "Contact", null, "Technology", "Limit merchant"), LocalDateTime
                    .of(2026, 8, 27, 10, 0));
        return created
            .changeStatus(top.continew.admin.merchant.master.domain.MerchantStatus.ENABLED, null, LocalDateTime
                .of(2026, 8, 27, 10, 1));
    }

    private static final class MutableEligibility implements LimitAdjustmentEligibilityPort {
        private boolean eligible = true;
        private int calls;

        @Override
        public LimitAdjustmentEligibility requireEligible(Long tenantId,
                                                          Merchant merchant,
                                                          String channelCode,
                                                          LocalDateTime effectiveAt) {
            calls++;
            if (!eligible) {
                throw new MerchantDomainException("Merchant channel is not eligible for limit adjustment");
            }
            return new LimitAdjustmentEligibility(5301L, "PRODUCT-A", "ELIGIBILITY-V1", "CONNECTION-V2");
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
            if (!TENANT_ID.equals(tenantId) || !MERCHANT_ID.equals(merchantId)) {
                throw new MerchantAccessDeniedException();
            }
            return merchant;
        }
    }

    private static final class InMemoryRepository implements LimitAdjustmentRepository {
        private final List<LimitAdjustment> requests = new ArrayList<>();
        private final List<LimitAdjustmentHistory> history = new ArrayList<>();
        private BigDecimal currentEffective;

        @Override
        public Optional<LimitAdjustment> findActive(Long tenantId,
                                                    Long merchantId,
                                                    String channelCode,
                                                    String platformCode) {
            return requests.stream()
                .filter(item -> item.tenantId().equals(tenantId) && item.merchantId().equals(merchantId) && item
                    .channelCode()
                    .equals(channelCode) && item.platformCode().equals(platformCode) && item.active())
                .findFirst();
        }

        @Override
        public Optional<LimitAdjustment> findById(Long tenantId, Long merchantId, Long requestId) {
            return requests.stream()
                .filter(item -> item.tenantId().equals(tenantId) && item.merchantId().equals(merchantId) && item.id()
                    .equals(requestId))
                .findFirst();
        }

        @Override
        public Optional<LimitAdjustment> findByRequestId(Long tenantId, Long requestId) {
            return requests.stream()
                .filter(item -> item.tenantId().equals(tenantId) && item.id().equals(requestId))
                .findFirst();
        }

        @Override
        public Optional<BigDecimal> findCurrentEffectiveLimit(Long tenantId,
                                                              Long merchantId,
                                                              String channelCode,
                                                              String platformCode,
                                                              String currency) {
            return Optional.ofNullable(currentEffective);
        }

        @Override
        public LimitAdjustment insert(LimitAdjustmentDraft draft) {
            LimitAdjustment request = new LimitAdjustment(draft.id(), draft.tenantId(), draft.requestNo(), draft
                .merchantId(), draft.owningAgentId(), draft.channelCode(), draft.platformCode(), draft.currency(), draft
                    .originalLimit(), draft.requestedLimit(), draft.normalizedLimit(), null, draft.reason(), draft
                        .eligibilityVersion(), draft.channelConfigVersion(), draft
                            .amountPolicyVersion(), null, LimitApprovalStatus.PENDING, LimitChannelStatus.NOT_SUBMITTED, LimitEffectiveStatus.NOT_EFFECTIVE, "ACTIVE", draft
                                .applicantId(), draft.applicationTime(), null, null, null, null, null, 0L, draft
                                    .applicationTime(), null);
            requests.add(request);
            return request;
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
                                                   top.continew.admin.merchant.limit.domain.LimitApprovalStatus approvalStatus,
                                                   String opinion,
                                                   Long actorUserId,
                                                   LocalDateTime approvalTime) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LimitAdjustment applyChannelResult(Long tenantId,
                                                  Long requestId,
                                                  Long expectedVersion,
                                                  top.continew.admin.merchant.limit.domain.LimitChannelStatus channelStatus,
                                                  top.continew.admin.merchant.limit.domain.LimitEffectiveStatus effectiveStatus,
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
            LimitAdjustment request = draft.request();
            history.add(new LimitAdjustmentHistory(draft.id(), request.tenantId(), request.id(), request
                .rowVersion(), draft.action(), request.approvalStatus(), request.channelStatus(), request
                    .effectiveStatus(), request.originalLimit(), request.requestedLimit(), request
                        .normalizedLimit(), request.effectiveLimit(), request.amountPolicyVersion(), draft
                            .actorUserId(), request.opinion(), request.channelResultCode(), request
                                .channelResultMessage(), draft.occurredTime()));
        }

        @Override
        public List<LimitAdjustmentHistory> listHistory(Long tenantId, Long requestId) {
            return history.stream()
                .filter(item -> item.tenantId().equals(tenantId) && item.requestId().equals(requestId))
                .toList();
        }
    }

    private static final class InMemoryAuditRepository implements SecurityAuditRepository {
        private final List<SecurityAuditRecord> records = new ArrayList<>();

        @Override
        public Long append(SecurityAuditRecord record) {
            records.add(record);
            return (long)records.size();
        }
    }
}
