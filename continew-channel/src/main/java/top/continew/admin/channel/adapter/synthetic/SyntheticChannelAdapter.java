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

import top.continew.admin.channel.api.ChannelAdapter;
import top.continew.admin.channel.api.ChannelAdapterException;
import top.continew.admin.channel.dto.ChannelAccountInfoQuery;
import top.continew.admin.channel.dto.ChannelAccountInfoResult;
import top.continew.admin.channel.dto.ChannelAccountStatus;
import top.continew.admin.channel.dto.ChannelCommandContext;
import top.continew.admin.channel.dto.ChannelEvent;
import top.continew.admin.channel.dto.ChannelEventType;
import top.continew.admin.channel.dto.ChannelLimitAdjustmentCommand;
import top.continew.admin.channel.dto.ChannelLimitAdjustmentQuery;
import top.continew.admin.channel.dto.ChannelLimitAdjustmentResult;
import top.continew.admin.channel.dto.ChannelLimitStatus;
import top.continew.admin.channel.dto.ChannelOnboardingState;
import top.continew.admin.channel.dto.ChannelOnboardingSubmitCommand;
import top.continew.admin.channel.dto.ChannelOperationStatus;
import top.continew.admin.channel.dto.ChannelRef;
import top.continew.admin.channel.dto.ChannelResultMeta;
import top.continew.admin.channel.dto.ChannelSigningLinkCommand;
import top.continew.admin.channel.dto.ChannelSigningLinkResult;
import top.continew.admin.channel.dto.ChannelStageStatus;
import top.continew.admin.channel.dto.ChannelStatusQuery;
import top.continew.admin.channel.dto.ChannelStatusResult;
import top.continew.admin.channel.dto.ChannelSubmissionResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** In-memory deterministic reference adapter. It never performs network or filesystem I/O. */
public final class SyntheticChannelAdapter implements ChannelAdapter {

    public static final String CHANNEL_CODE = "SYNTHETIC";
    public static final String MAPPING_VERSION = "SYNTHETIC-MAP-V1";
    private static final ChannelRef CHANNEL = new ChannelRef(CHANNEL_CODE);

    private final Clock clock;
    private final boolean completeOnQuery;
    private final ConcurrentMap<String, OnboardingRecord> onboarding = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, OnboardingQueryRecord> onboardingQueries = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, LimitRecord> limits = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<ChannelEvent> events = new ConcurrentLinkedQueue<>();
    private final AtomicLong eventSequence = new AtomicLong();

    public SyntheticChannelAdapter(Clock clock) {
        this(clock, false);
    }

    public SyntheticChannelAdapter(Clock clock, boolean completeOnQuery) {
        if (clock == null)
            throw new IllegalArgumentException("clock is invalid");
        this.clock = clock;
        this.completeOnQuery = completeOnQuery;
    }

    @Override
    public ChannelRef channel() {
        return CHANNEL;
    }

    @Override
    public ChannelSubmissionResult submitOnboarding(ChannelOnboardingSubmitCommand command) {
        requireChannel(command.context());
        AtomicReference<Boolean> created = new AtomicReference<>(false);
        OnboardingRecord record = onboarding.compute(command.context().businessSerial(), (serial, existing) -> {
            if (existing != null) {
                if (!existing.command().equals(command))
                    throw conflict();
                return existing;
            }
            created.set(true);
            LocalDateTime now = now();
            ChannelOnboardingState state = new ChannelOnboardingState(ChannelStageStatus.PROCESSING, ChannelStageStatus.NOT_STARTED, ChannelStageStatus.NOT_STARTED, ChannelStageStatus.NOT_STARTED, ChannelStageStatus.PROCESSING);
            ChannelSubmissionResult result = new ChannelSubmissionResult(meta(command
                .context(), requestId(serial), "SYN_ACCEPTED", ChannelOperationStatus.ACCEPTED, "Synthetic onboarding accepted", now), state);
            return new OnboardingRecord(command, result);
        });
        if (Boolean.TRUE.equals(created.get())) {
            emit(record.command().context(), requestId(command.context()
                .businessSerial()), ChannelEventType.SUBMISSION_ACCEPTED, record.result().meta(), record.result()
                    .state());
        }
        return record.result();
    }

    @Override
    public ChannelStatusResult queryOnboardingStatus(ChannelStatusQuery query) {
        requireChannel(query.context());
        OnboardingRecord record = onboarding.get(query.context().businessSerial());
        if (record != null && !sameBusiness(record.command().context(), query.context()))
            throw notFound();
        if (completeOnQuery) {
            return completeOnboardingQuery(query, record);
        }
        if (record == null)
            throw notFound();
        return new ChannelStatusResult(record.result().meta(), record.result().state());
    }

    private ChannelStatusResult completeOnboardingQuery(ChannelStatusQuery query, OnboardingRecord submission) {
        AtomicReference<Boolean> created = new AtomicReference<>(false);
        OnboardingQueryRecord queryRecord = onboardingQueries.compute(query.context()
            .businessSerial(), (serial, existing) -> {
                if (existing != null) {
                    if (!sameBusiness(existing.context(), query.context()))
                        throw notFound();
                    return existing;
                }
                created.set(true);
                LocalDateTime now = now();
                ChannelOnboardingState state = new ChannelOnboardingState(ChannelStageStatus.SUCCEEDED, ChannelStageStatus.SUCCEEDED, ChannelStageStatus.SUCCEEDED, ChannelStageStatus.SUCCEEDED, ChannelStageStatus.SUCCEEDED);
                ChannelResultMeta meta = meta(query.context(), requestId(serial), "SYN_SUCCEEDED", ChannelOperationStatus.SUCCEEDED, "Synthetic onboarding completed", now);
                return new OnboardingQueryRecord(query.context(), new ChannelStatusResult(meta, state));
            });
        if (submission != null && Boolean.TRUE.equals(created.get())) {
            onboarding.computeIfPresent(query.context()
                .businessSerial(), (serial, existing) -> new OnboardingRecord(existing.command(), new ChannelSubmissionResult(queryRecord
                    .result()
                    .meta(), queryRecord.result().state())));
        }
        if (Boolean.TRUE.equals(created.get())) {
            emit(query.context(), queryRecord.result()
                .meta()
                .channelRequestId(), ChannelEventType.STATUS_CHANGED, queryRecord.result().meta(), queryRecord.result()
                    .state());
        }
        return queryRecord.result();
    }

    @Override
    public ChannelSigningLinkResult createSigningLink(ChannelSigningLinkCommand command) {
        requireOnboarding(command.context());
        if (!command.expiresAt().isAfter(now()))
            throw invalidTransition();
        String serial = command.context().businessSerial();
        String url = "https://synthetic.invalid/action/%s/%s?reference=%s".formatted(serial, command.action()
            .name(), digest(serial + ":" + command.action()));
        return new ChannelSigningLinkResult(meta(command
            .context(), requestId(serial), "SYN_LINK_READY", ChannelOperationStatus.SUCCEEDED, "Synthetic signing link ready", now()), command
                .action(), url, command.expiresAt());
    }

    @Override
    public ChannelAccountInfoResult queryAccountInfo(ChannelAccountInfoQuery query) {
        requireOnboarding(query.context());
        String suffix = "%04d".formatted(Math.floorMod(query.context().businessId(), 10000));
        return new ChannelAccountInfoResult(meta(query.context(), requestId(query.context()
            .businessSerial()), "SYN_ACCOUNT_ACTIVE", ChannelOperationStatus.SUCCEEDED, "Synthetic account active", now()), "SYN-ACCOUNT-" + query
                .context()
                .businessId(), "****" + suffix, "SYNBANK", ChannelAccountStatus.ACTIVE);
    }

    @Override
    public ChannelLimitAdjustmentResult adjustLimit(ChannelLimitAdjustmentCommand command) {
        requireChannel(command.context());
        AtomicReference<Boolean> created = new AtomicReference<>(false);
        LimitRecord record = limits.compute(command.context().businessSerial(), (serial, existing) -> {
            if (existing != null) {
                if (!existing.command().equals(command))
                    throw conflict();
                return existing;
            }
            created.set(true);
            ChannelLimitAdjustmentResult result = new ChannelLimitAdjustmentResult(meta(command
                .context(), requestId(serial), "SYN_LIMIT_ACCEPTED", ChannelOperationStatus.ACCEPTED, "Synthetic limit accepted", now()), command
                    .requestId(), command.platformCode(), command.currency(), command
                        .normalizedLimit(), null, ChannelLimitStatus.PROCESSING, null);
            return new LimitRecord(command, result);
        });
        if (Boolean.TRUE.equals(created.get())) {
            emit(record.command().context(), requestId(command.context()
                .businessSerial()), ChannelEventType.LIMIT_CHANGED, record.result().meta(), null);
        }
        return record.result();
    }

    @Override
    public ChannelLimitAdjustmentResult queryLimitAdjustment(ChannelLimitAdjustmentQuery query) {
        requireChannel(query.context());
        LimitRecord record = limits.get(query.context().businessSerial());
        if (record == null || !sameBusiness(record.command().context(), query.context()) || !record.command()
            .requestId()
            .equals(query.requestId()))
            throw notFound();
        return record.result();
    }

    public ChannelStatusResult advanceOnboarding(String businessSerial,
                                                 ChannelOnboardingState state,
                                                 ChannelOperationStatus status,
                                                 String rawStatusCode) {
        if (state == null || status == null)
            throw invalidTransition();
        OnboardingRecord record = onboarding.computeIfPresent(businessSerial, (serial, existing) -> {
            ChannelResultMeta meta = meta(existing.command()
                .context(), requestId(serial), rawStatusCode, status, "Synthetic onboarding advanced", now());
            return new OnboardingRecord(existing.command(), new ChannelSubmissionResult(meta, state));
        });
        if (record == null)
            throw notFound();
        emit(record.command().context(), requestId(businessSerial), ChannelEventType.STATUS_CHANGED, record.result()
            .meta(), state);
        return new ChannelStatusResult(record.result().meta(), state);
    }

    public ChannelLimitAdjustmentResult markLimitEffective(String businessSerial, BigDecimal effectiveLimit) {
        AtomicReference<ChannelLimitAdjustmentResult> updated = new AtomicReference<>();
        LimitRecord record = limits.computeIfPresent(businessSerial, (serial, existing) -> {
            ChannelLimitAdjustmentResult result = new ChannelLimitAdjustmentResult(meta(existing.command()
                .context(), requestId(serial), "SYN_LIMIT_EFFECTIVE", ChannelOperationStatus.SUCCEEDED, "Synthetic limit effective", now()), existing
                    .command()
                    .requestId(), existing.command().platformCode(), existing.command().currency(), existing.command()
                        .normalizedLimit(), effectiveLimit, ChannelLimitStatus.EFFECTIVE, now());
            updated.set(result);
            return new LimitRecord(existing.command(), result);
        });
        if (record == null)
            throw notFound();
        emit(record.command().context(), requestId(businessSerial), ChannelEventType.LIMIT_CHANGED, updated.get()
            .meta(), null);
        return updated.get();
    }

    public List<ChannelEvent> drainEvents() {
        List<ChannelEvent> drained = new ArrayList<>();
        ChannelEvent event;
        while ((event = events.poll()) != null)
            drained.add(event);
        return List.copyOf(drained);
    }

    public int onboardingCount() {
        return onboarding.size();
    }

    private void requireOnboarding(ChannelCommandContext context) {
        requireChannel(context);
        OnboardingRecord record = onboarding.get(context.businessSerial());
        if (record == null || !sameBusiness(record.command().context(), context))
            throw notFound();
    }

    private void requireChannel(ChannelCommandContext context) {
        if (context == null || !CHANNEL_CODE.equals(context.product().channelCode())) {
            throw new ChannelAdapterException(ChannelAdapterException.Code.UNSUPPORTED_CHANNEL);
        }
    }

    private boolean sameBusiness(ChannelCommandContext left, ChannelCommandContext right) {
        return left.tenantId().equals(right.tenantId()) && left.product().equals(right.product()) && left
            .configVersion()
            .equals(right.configVersion()) && left.businessType() == right.businessType() && left.businessId()
                .equals(right.businessId()) && left.businessVersion().equals(right.businessVersion()) && left
                    .businessSerial()
                    .equals(right.businessSerial());
    }

    private ChannelResultMeta meta(ChannelCommandContext context,
                                   String channelRequestId,
                                   String rawCode,
                                   ChannelOperationStatus status,
                                   String message,
                                   LocalDateTime time) {
        return new ChannelResultMeta(context.product(), context.configVersion(), context
            .businessSerial(), channelRequestId, rawCode, MAPPING_VERSION, status, message, time);
    }

    private void emit(ChannelCommandContext context,
                      String channelRequestId,
                      ChannelEventType eventType,
                      ChannelResultMeta meta,
                      ChannelOnboardingState state) {
        LocalDateTime time = now();
        events.add(new ChannelEvent(context.tenantId(), "SYN-EVENT-" + eventSequence
            .incrementAndGet(), eventType, context.product(), context.businessType(), context.businessId(), context
                .businessVersion(), context.businessSerial(), channelRequestId, meta.rawStatusCode(), meta
                    .statusMappingVersion(), meta.operationStatus(), state, time, time));
    }

    private String requestId(String serial) {
        return "SYN-REQ-" + digest(serial);
    }

    private String digest(String value) {
        try {
            return java.util.HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)), 0, 8);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private ChannelAdapterException conflict() {
        return new ChannelAdapterException(ChannelAdapterException.Code.IDEMPOTENCY_CONFLICT);
    }

    private ChannelAdapterException notFound() {
        return new ChannelAdapterException(ChannelAdapterException.Code.NOT_FOUND);
    }

    private ChannelAdapterException invalidTransition() {
        return new ChannelAdapterException(ChannelAdapterException.Code.INVALID_TRANSITION);
    }

    private record OnboardingRecord(ChannelOnboardingSubmitCommand command, ChannelSubmissionResult result) {}

    private record OnboardingQueryRecord(ChannelCommandContext context, ChannelStatusResult result) {}

    private record LimitRecord(ChannelLimitAdjustmentCommand command, ChannelLimitAdjustmentResult result) {}
}
