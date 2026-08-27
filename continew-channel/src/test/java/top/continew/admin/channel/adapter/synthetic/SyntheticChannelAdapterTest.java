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

package top.continew.admin.channel.adapter.synthetic;

import org.junit.jupiter.api.Test;
import top.continew.admin.channel.api.ChannelAdapterException;
import top.continew.admin.channel.dto.ChannelAccountInfoQuery;
import top.continew.admin.channel.dto.ChannelBusinessType;
import top.continew.admin.channel.dto.ChannelCommandContext;
import top.continew.admin.channel.dto.ChannelEventType;
import top.continew.admin.channel.dto.ChannelLimitAdjustmentCommand;
import top.continew.admin.channel.dto.ChannelLimitAdjustmentQuery;
import top.continew.admin.channel.dto.ChannelLimitStatus;
import top.continew.admin.channel.dto.ChannelOnboardingState;
import top.continew.admin.channel.dto.ChannelOnboardingSubmitCommand;
import top.continew.admin.channel.dto.ChannelOperationStatus;
import top.continew.admin.channel.dto.ChannelProductKey;
import top.continew.admin.channel.dto.ChannelSigningAction;
import top.continew.admin.channel.dto.ChannelSigningLinkCommand;
import top.continew.admin.channel.dto.ChannelStageStatus;
import top.continew.admin.channel.dto.ChannelStatusQuery;
import top.continew.admin.channel.dto.ChannelSubmissionResult;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyntheticChannelAdapterTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 24, 0, 0);

    @Test
    void duplicateSubmissionIsIdempotentAndConflictingPayloadIsRejected() {
        SyntheticChannelAdapter adapter = adapter();
        ChannelOnboardingSubmitCommand command = onboardingCommand(101L);
        ChannelSubmissionResult first = adapter.submitOnboarding(command);
        assertEquals(first, adapter.submitOnboarding(command));
        assertEquals(1, adapter.onboardingCount());
        assertEquals(1, adapter.drainEvents().size());

        ChannelAdapterException conflict = assertThrows(ChannelAdapterException.class, () -> adapter
            .submitOnboarding(new ChannelOnboardingSubmitCommand(command.context(), 102L, "REQ-1", List.of(31L))));
        assertEquals(ChannelAdapterException.Code.IDEMPOTENCY_CONFLICT, conflict.code());
    }

    @Test
    void onboardingStateCanBeAdvancedAndQueriedWithASeparateTrace() {
        SyntheticChannelAdapter adapter = adapter();
        ChannelOnboardingSubmitCommand command = onboardingCommand(101L);
        adapter.submitOnboarding(command);
        adapter.drainEvents();
        ChannelOnboardingState succeeded = new ChannelOnboardingState(ChannelStageStatus.SUCCEEDED, ChannelStageStatus.SUCCEEDED, ChannelStageStatus.SUCCEEDED, ChannelStageStatus.SUCCEEDED, ChannelStageStatus.SUCCEEDED);
        adapter.advanceOnboarding(command.context()
            .businessSerial(), succeeded, ChannelOperationStatus.SUCCEEDED, "SYN_SUCCESS");
        ChannelCommandContext queryContext = context(ChannelBusinessType.ONBOARDING, "TRACE-QUERY");
        assertEquals(succeeded, adapter.queryOnboardingStatus(new ChannelStatusQuery(queryContext)).state());
        assertEquals(ChannelEventType.STATUS_CHANGED, adapter.drainEvents().get(0).eventType());
    }

    @Test
    void signingAndAccountOperationsReturnOnlyControlledValues() {
        SyntheticChannelAdapter adapter = adapter();
        ChannelOnboardingSubmitCommand command = onboardingCommand(101L);
        adapter.submitOnboarding(command);
        var link = adapter.createSigningLink(new ChannelSigningLinkCommand(command
            .context(), 201L, ChannelSigningAction.SIGN_AGREEMENT, NOW.plusMinutes(10)));
        assertTrue(link.signingUrl().startsWith("https://synthetic.invalid/"));
        assertFalse(link.toString().contains("reference="));
        var account = adapter.queryAccountInfo(new ChannelAccountInfoQuery(command.context()));
        assertTrue(account.accountNumberMasked().startsWith("****"));
    }

    @Test
    void limitAdjustmentRemainsProcessingUntilExplicitChannelConfirmation() {
        SyntheticChannelAdapter adapter = adapter();
        ChannelLimitAdjustmentCommand command = new ChannelLimitAdjustmentCommand(context(ChannelBusinessType.LIMIT_ADJUSTMENT, "TRACE-LIMIT"), 501L, "INBOUND", "CNY", BigDecimal.ZERO, new BigDecimal("1000"), new BigDecimal("1000"), "BUSINESS_GROWTH");
        assertEquals(ChannelLimitStatus.PROCESSING, adapter.adjustLimit(command).limitStatus());
        assertEquals(ChannelLimitStatus.PROCESSING, adapter.queryLimitAdjustment(new ChannelLimitAdjustmentQuery(command
            .context(), 501L)).limitStatus());
        assertEquals(ChannelLimitStatus.EFFECTIVE, adapter.markLimitEffective(command.context()
            .businessSerial(), new BigDecimal("1000")).limitStatus());
    }

    @Test
    void concurrentDuplicateSubmissionCreatesOneRecordAndOneEvent() throws Exception {
        SyntheticChannelAdapter adapter = adapter();
        ChannelOnboardingSubmitCommand command = onboardingCommand(101L);
        var executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<ChannelSubmissionResult>> calls = java.util.stream.IntStream.range(0, 32)
                .mapToObj(index -> (Callable<ChannelSubmissionResult>)() -> adapter.submitOnboarding(command))
                .toList();
            List<ChannelSubmissionResult> results = executor.invokeAll(calls).stream().map(future -> {
                try {
                    return future.get();
                } catch (Exception ex) {
                    throw new AssertionError(ex);
                }
            }).toList();
            assertTrue(results.stream().allMatch(results.get(0)::equals));
        } finally {
            executor.shutdownNow();
        }
        assertEquals(1, adapter.onboardingCount());
        assertEquals(1, adapter.drainEvents().size());
    }

    private SyntheticChannelAdapter adapter() {
        return new SyntheticChannelAdapter(Clock.fixed(Instant.parse("2026-08-24T00:00:00Z"), ZoneOffset.UTC));
    }

    private ChannelOnboardingSubmitCommand onboardingCommand(Long kycVersionId) {
        return new ChannelOnboardingSubmitCommand(context(ChannelBusinessType.ONBOARDING, "TRACE-1"), kycVersionId, "REQ-1", List
            .of(31L));
    }

    private ChannelCommandContext context(ChannelBusinessType businessType, String traceId) {
        return new ChannelCommandContext(1L, new ChannelProductKey("SYNTHETIC", "ONBOARDING"), "CFG-1", businessType, 2L, 1L, "SERIAL-1", traceId);
    }
}
