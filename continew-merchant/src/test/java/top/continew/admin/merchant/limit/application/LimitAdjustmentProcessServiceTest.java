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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.continew.admin.channel.dto.ChannelLimitAdjustmentResult;
import top.continew.admin.channel.dto.ChannelLimitStatus;
import top.continew.admin.channel.dto.ChannelOperationStatus;
import top.continew.admin.channel.dto.ChannelProductKey;
import top.continew.admin.channel.dto.ChannelResultMeta;
import top.continew.admin.merchant.limit.domain.LimitAdjustment;
import top.continew.admin.merchant.limit.domain.LimitAdjustmentPolicy;
import top.continew.admin.merchant.limit.domain.LimitAdjustmentPolicyStatus;
import top.continew.admin.merchant.limit.domain.LimitApprovalStatus;
import top.continew.admin.merchant.limit.domain.LimitChannelStatus;
import top.continew.admin.merchant.limit.domain.LimitEffectiveStatus;
import top.continew.admin.merchant.limit.domain.LimitRoundingMode;
import top.continew.admin.merchant.master.application.MerchantScopeAuthorizationService;
import top.continew.admin.merchant.master.domain.Merchant;
import top.continew.admin.merchant.master.domain.MerchantRegistration;
import top.continew.admin.merchant.master.domain.MerchantType;
import top.continew.admin.merchant.security.audit.application.SecurityAuditRepository;
import top.continew.admin.merchant.security.audit.application.SecurityAuditWriter;
import top.continew.admin.merchant.security.audit.domain.SecurityAuditRecord;
import top.continew.admin.workflow.api.WorkflowActor;
import top.continew.admin.workflow.api.WorkflowAuthorizationPort;
import top.continew.admin.workflow.api.WorkflowMappingService;
import top.continew.admin.workflow.api.WorkflowService;
import top.continew.admin.workflow.command.ClaimTaskCommand;
import top.continew.admin.workflow.command.CompleteTaskCommand;
import top.continew.admin.workflow.command.StartWorkflowCommand;
import top.continew.admin.workflow.command.TransferTaskCommand;
import top.continew.admin.workflow.command.UnclaimTaskCommand;
import top.continew.admin.workflow.definition.MerchantLimitAdjustmentWorkflowDefinition;
import top.continew.admin.workflow.dto.WorkflowInstanceMapping;
import top.continew.admin.workflow.dto.WorkflowPage;
import top.continew.admin.workflow.dto.WorkflowProcessHistory;
import top.continew.admin.workflow.dto.WorkflowRef;
import top.continew.admin.workflow.dto.WorkflowTask;
import top.continew.admin.workflow.query.WorkflowDoneQuery;
import top.continew.admin.workflow.query.WorkflowTaskQuery;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LimitAdjustmentProcessServiceTest {

    private static final Long TENANT_ID = 1301L;
    private static final Long REQUEST_ID = 2301L;
    private static final Long MERCHANT_ID = 3301L;
    private static final Long REVIEWER_ID = 4301L;
    private static final Long CHANNEL_USER_ID = 4302L;
    private static final LocalDateTime CURRENT_TIME = LocalDateTime.of(2026, 8, 28, 10, 0);

    private InMemoryRepository repository;
    private FakeWorkflowService workflowService;
    private LimitAdjustmentProcessService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryRepository();
        workflowService = new FakeWorkflowService();
        AtomicLong ids = new AtomicLong(90_000L);
        IdentifierGenerator identifierGenerator = value -> ids.incrementAndGet();
        LimitAdjustmentRevalidationService revalidation = new LimitAdjustmentRevalidationService(null, null, null) {
            @Override
            public Snapshot requireCurrent(LimitAdjustment request, Merchant merchant, LocalDateTime effectiveAt) {
                return new Snapshot(new LimitAdjustmentEligibility(5001L, "PRODUCT-A", "ELIGIBILITY-V1", "CONNECTION-V1"), policy(), request
                    .originalLimit());
            }
        };
        service = new LimitAdjustmentProcessService(workflowService, new FakeMappingService(), new FakeAuthorization(), new AllowingMerchantAuthorization(), repository, identifierGenerator, revalidation, new SecurityAuditWriter(new InMemoryAuditRepository()));
    }

    @Test
    void humanApprovalAloneDoesNotMakeLimitEffectiveBeforeChannelConfirmation() {
        repository.request = request(LimitApprovalStatus.PENDING, LimitChannelStatus.NOT_SUBMITTED, LimitEffectiveStatus.NOT_EFFECTIVE, 1L, "ACTIVE", null, null);
        workflowService.task = task("limitReviewTask", REVIEWER_ID);

        LimitAdjustmentProcessResult result = service
            .review(new LimitAdjustmentReviewCommand(TENANT_ID, REVIEWER_ID, "TASK-1", 1L, LimitAdjustmentReviewAction.APPROVE, "Approved for channel submission", "127.0.0.1"));

        assertEquals(LimitApprovalStatus.APPROVED, result.request().approvalStatus());
        assertEquals(LimitChannelStatus.NOT_SUBMITTED, result.request().channelStatus());
        assertEquals(LimitEffectiveStatus.NOT_EFFECTIVE, result.request().effectiveStatus());
        assertNull(result.request().effectiveLimit());
        assertTrue(result.request().active());
        assertEquals(Map.of("reviewAction", "APPROVE"), workflowService.completed.variables());
        assertEquals("APPROVE", repository.history.get(0).action());
    }

    @Test
    void channelEffectiveConfirmationUpdatesEffectiveStateAndClosesActiveGuard() {
        repository.request = request(LimitApprovalStatus.APPROVED, LimitChannelStatus.NOT_SUBMITTED, LimitEffectiveStatus.NOT_EFFECTIVE, 2L, "ACTIVE", null, CURRENT_TIME
            .minusMinutes(10));
        workflowService.task = task("channelSubmitTask", CHANNEL_USER_ID);
        ChannelResultMeta meta = new ChannelResultMeta(new ChannelProductKey("CHANNEL-A", "PRODUCT-A"), "CONNECTION-V1", "LA2301", "CHANNEL-REQ-1", "LIMIT_EFFECTIVE", "MAP-V1", ChannelOperationStatus.SUCCEEDED, "Limit effective", CURRENT_TIME);
        ChannelLimitAdjustmentResult channelResult = new ChannelLimitAdjustmentResult(meta, REQUEST_ID, "INBOUND", "CNY", new BigDecimal("2000.00"), new BigDecimal("2000.00"), ChannelLimitStatus.EFFECTIVE, CURRENT_TIME);

        LimitAdjustmentProcessResult result = service
            .recordChannelResult(new LimitAdjustmentChannelResultCommand(TENANT_ID, CHANNEL_USER_ID, "TASK-1", 1L, channelResult, "127.0.0.1"));

        assertEquals(LimitChannelStatus.SUCCEEDED, result.request().channelStatus());
        assertEquals(LimitEffectiveStatus.EFFECTIVE, result.request().effectiveStatus());
        assertEquals(new BigDecimal("2000.00"), result.request().effectiveLimit());
        assertFalse(result.request().active());
        assertEquals(Map.of("channelStatus", "EFFECTIVE"), workflowService.completed.variables());
        assertEquals("CHANNEL_EFFECTIVE", repository.history.get(0).action());
    }

    private WorkflowTask task(String taskDefinitionKey, Long assignee) {
        return new WorkflowTask("TASK-1", taskDefinitionKey, taskDefinitionKey, "PROCESS-1", "DEFINITION-1", MerchantLimitAdjustmentWorkflowDefinition.PROCESS_KEY, 1, "%s:MERCHANT_LIMIT_ADJUSTMENT:%s:1"
            .formatted(TENANT_ID, REQUEST_ID), String.valueOf(TENANT_ID), String
                .valueOf(assignee), WorkflowTask.State.CLAIMED, CURRENT_TIME.minusMinutes(5), CURRENT_TIME
                    .minusMinutes(4), null, null);
    }

    private LimitAdjustment request(LimitApprovalStatus approvalStatus,
                                    LimitChannelStatus channelStatus,
                                    LimitEffectiveStatus effectiveStatus,
                                    Long rowVersion,
                                    String activeGuard,
                                    BigDecimal effectiveLimit,
                                    LocalDateTime approvalTime) {
        LocalDateTime effectiveTime = LimitEffectiveStatus.EFFECTIVE.equals(effectiveStatus) ? CURRENT_TIME : null;
        return new LimitAdjustment(REQUEST_ID, TENANT_ID, "LA2301", MERCHANT_ID, 5301L, "CHANNEL-A", "INBOUND", "CNY", new BigDecimal("500.00"), new BigDecimal("1250.00"), new BigDecimal("2000.00"), effectiveLimit, "capacity", "ELIGIBILITY-V1", "CONNECTION-V1", "POLICY-V1", "PROCESS-1", approvalStatus, channelStatus, effectiveStatus, activeGuard, 6301L, CURRENT_TIME
            .minusHours(1), approvalTime, effectiveTime, null, null, null, rowVersion, CURRENT_TIME
                .minusHours(1), CURRENT_TIME.minusMinutes(30));
    }

    private LimitAdjustmentPolicy policy() {
        return new LimitAdjustmentPolicy(7001L, TENANT_ID, "CHANNEL-A", "INBOUND", "CNY", "POLICY-V1", new BigDecimal("1000.00"), new BigDecimal("10000.00"), 2, new BigDecimal("1000.00"), LimitRoundingMode.CEILING, LimitAdjustmentPolicyStatus.ENABLED, CURRENT_TIME
            .minusDays(1), null, CURRENT_TIME.minusDays(1));
    }

    private Merchant merchant() {
        return Merchant
            .create(new MerchantRegistration(MERCHANT_ID, TENANT_ID, 5301L, "M-3301", MerchantType.ENTERPRISE, "Limit Merchant", "Limit", "a"
                .repeat(64), 6301L, 6302L, "Contact", null, "Technology", "Limit merchant"), CURRENT_TIME.minusDays(1));
    }

    private final class FakeAuthorization implements WorkflowAuthorizationPort {
        @Override
        public WorkflowActor requireActor(Long tenantId, Long userId) {
            Set<String> roles = REVIEWER_ID.equals(userId) ? Set.of("MERCHANT_REVIEWER") : Set.of("CHANNEL_OPERATIONS");
            return new WorkflowActor(tenantId, userId, roles);
        }

        @Override
        public boolean canAccessBusiness(WorkflowActor actor, String businessType, Long businessId) {
            return true;
        }
    }

    private final class AllowingMerchantAuthorization extends MerchantScopeAuthorizationService {
        private AllowingMerchantAuthorization() {
            super(null, null);
        }

        @Override
        public Merchant requireAccessible(Long tenantId, Long actorUserId, Long merchantId) {
            return merchant();
        }
    }

    private static final class FakeMappingService implements WorkflowMappingService {
        private final WorkflowInstanceMapping mapping = new WorkflowInstanceMapping(1L, TENANT_ID, "MERCHANT_LIMIT_ADJUSTMENT", REQUEST_ID, 1L, "DEFINITION-1", MerchantLimitAdjustmentWorkflowDefinition.PROCESS_KEY, 1, "PROCESS-1", "%s:MERCHANT_LIMIT_ADJUSTMENT:%s:1"
            .formatted(TENANT_ID, REQUEST_ID), "RUNNING", CURRENT_TIME.minusMinutes(10), null, 0L);

        @Override
        public Optional<WorkflowInstanceMapping> findByBusinessKey(Long tenantId, String businessKey) {
            return Optional.of(mapping);
        }

        @Override
        public Optional<WorkflowInstanceMapping> findByProcessInstanceId(Long tenantId, String processInstanceId) {
            return Optional.of(mapping);
        }
    }

    private static final class FakeWorkflowService implements WorkflowService {
        private WorkflowTask task;
        private CompleteTaskCommand completed;

        @Override
        public WorkflowTask task(Long tenantId, Long userId, String taskId) {
            return task;
        }

        @Override
        public void complete(CompleteTaskCommand command) {
            completed = command;
        }

        @Override
        public WorkflowRef start(StartWorkflowCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void claim(ClaimTaskCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void unclaim(UnclaimTaskCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void transfer(TransferTaskCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public WorkflowTask taskView(Long tenantId, Long userId, String taskId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public WorkflowPage<WorkflowTask> pageTodo(WorkflowTaskQuery query) {
            throw new UnsupportedOperationException();
        }

        @Override
        public WorkflowPage<WorkflowTask> pageDone(WorkflowDoneQuery query) {
            throw new UnsupportedOperationException();
        }

        @Override
        public WorkflowProcessHistory history(Long tenantId, Long userId, String processInstanceId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class InMemoryRepository implements LimitAdjustmentRepository {
        private LimitAdjustment request;
        private final List<LimitAdjustmentHistory> history = new ArrayList<>();

        @Override
        public Optional<LimitAdjustment> findByRequestId(Long tenantId, Long requestId) {
            return Optional.of(request);
        }

        @Override
        public LimitAdjustment applyReviewDecision(Long tenantId,
                                                   Long requestId,
                                                   Long expectedVersion,
                                                   LimitApprovalStatus approvalStatus,
                                                   String opinion,
                                                   Long actorUserId,
                                                   LocalDateTime approvalTime) {
            request = copy(request, approvalStatus, request.channelStatus(), request.effectiveStatus(), request
                .effectiveLimit(), approvalTime, request.effectiveTime(), opinion, request.channelResultCode(), request
                    .channelResultMessage(), LimitApprovalStatus.REJECTED.equals(approvalStatus)
                        ? null
                        : request.activeRequestGuard(), expectedVersion + 1);
            return request;
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
            boolean terminal = LimitEffectiveStatus.EFFECTIVE.equals(effectiveStatus) || LimitChannelStatus.FAILED
                .equals(channelStatus) || LimitChannelStatus.REJECTED.equals(channelStatus);
            request = copy(request, request.approvalStatus(), channelStatus, effectiveStatus, effectiveLimit, request
                .approvalTime(), effectiveTime, request.opinion(), channelResultCode, channelResultMessage, terminal
                    ? null
                    : request.activeRequestGuard(), expectedVersion + 1);
            return request;
        }

        @Override
        public void appendHistory(LimitAdjustmentHistoryDraft draft) {
            LimitAdjustment value = draft.request();
            history.add(new LimitAdjustmentHistory(draft.id(), value.tenantId(), value.id(), value.rowVersion(), draft
                .action(), value.approvalStatus(), value.channelStatus(), value.effectiveStatus(), value
                    .originalLimit(), value.requestedLimit(), value.normalizedLimit(), value.effectiveLimit(), value
                        .amountPolicyVersion(), draft.actorUserId(), value.opinion(), value.channelResultCode(), value
                            .channelResultMessage(), draft.occurredTime()));
        }

        private LimitAdjustment copy(LimitAdjustment value,
                                     LimitApprovalStatus approvalStatus,
                                     LimitChannelStatus channelStatus,
                                     LimitEffectiveStatus effectiveStatus,
                                     BigDecimal effectiveLimit,
                                     LocalDateTime approvalTime,
                                     LocalDateTime effectiveTime,
                                     String opinion,
                                     String resultCode,
                                     String resultMessage,
                                     String activeGuard,
                                     Long rowVersion) {
            return new LimitAdjustment(value.id(), value.tenantId(), value.requestNo(), value.merchantId(), value
                .owningAgentId(), value.channelCode(), value.platformCode(), value.currency(), value
                    .originalLimit(), value.requestedLimit(), value.normalizedLimit(), effectiveLimit, value
                        .reason(), value.eligibilityVersion(), value.channelConfigVersion(), value
                            .amountPolicyVersion(), value
                                .processInstanceId(), approvalStatus, channelStatus, effectiveStatus, activeGuard, value
                                    .applicantId(), value
                                        .applicationTime(), approvalTime, effectiveTime, opinion, resultCode, resultMessage, rowVersion, value
                                            .createTime(), CURRENT_TIME);
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
        public Optional<BigDecimal> findCurrentEffectiveLimit(Long tenantId,
                                                              Long merchantId,
                                                              String channelCode,
                                                              String platformCode,
                                                              String currency) {
            return Optional.of(request.originalLimit());
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
        public List<LimitAdjustmentHistory> listHistory(Long tenantId, Long requestId) {
            return List.copyOf(history);
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