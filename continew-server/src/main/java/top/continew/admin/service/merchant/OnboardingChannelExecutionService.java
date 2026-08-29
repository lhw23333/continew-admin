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

package top.continew.admin.service.merchant;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.channel.api.ChannelAdapter;
import top.continew.admin.channel.api.ChannelAdapterRegistry;
import top.continew.admin.channel.api.ChannelApplicationStatePort;
import top.continew.admin.channel.dto.ChannelApplicationState;
import top.continew.admin.channel.dto.ChannelBusinessType;
import top.continew.admin.channel.dto.ChannelCommandContext;
import top.continew.admin.channel.dto.ChannelMappedStatus;
import top.continew.admin.channel.dto.ChannelOnboardingState;
import top.continew.admin.channel.dto.ChannelOnboardingSubmitCommand;
import top.continew.admin.channel.dto.ChannelOperationStatus;
import top.continew.admin.channel.dto.ChannelProductKey;
import top.continew.admin.channel.dto.ChannelResultMeta;
import top.continew.admin.channel.dto.ChannelStageStatus;
import top.continew.admin.channel.dto.ChannelStateMergeResult;
import top.continew.admin.channel.dto.ChannelStatusQuery;
import top.continew.admin.channel.service.ChannelStateMerger;
import top.continew.admin.merchant.master.application.MerchantScopeAuthorizationService;
import top.continew.admin.merchant.master.domain.MerchantDomainException;
import top.continew.admin.workflow.api.WorkflowActor;
import top.continew.admin.workflow.api.WorkflowAuthorizationPort;

import java.time.LocalDateTime;
import java.util.List;

/** Executes idempotent onboarding submit/query commands and atomically persists normalized channel state. */
@Service
@RequiredArgsConstructor
public class OnboardingChannelExecutionService {

    private static final String CHANNEL_OPERATIONS = "CHANNEL_OPERATIONS";

    private final WorkflowAuthorizationPort authorizationPort;
    private final MerchantScopeAuthorizationService merchantScopeAuthorizationService;
    private final ChannelAdapterRegistry adapterRegistry;
    private final ChannelApplicationStatePort statePort;
    private final ChannelStateMerger stateMerger;
    private final JdbcTemplate jdbcTemplate;

    @Transactional(rollbackFor = Exception.class)
    public OnboardingChannelExecutionResult submit(OnboardingChannelExecutionCommand command) {
        Execution execution = execution(command, true);
        List<Long> evidenceIds = jdbcTemplate.queryForList("""
            SELECT id FROM biz_kyc_attachment
            WHERE tenant_id = ? AND kyc_version_id = ? AND scan_status = 'CLEAN'
              AND validation_status = 'VALID' AND deleted = 0
            ORDER BY sort, id
            """, Long.class, command.tenantId(), execution.application().kycVersionId());
        ChannelOnboardingSubmitCommand channelCommand = new ChannelOnboardingSubmitCommand(execution
            .context(), execution.application().kycVersionId(), execution.application().requirementVersion(), evidenceIds);
        var result = execution.adapter().submitOnboarding(channelCommand);
        return apply(execution.current(), result.meta(), result.state());
    }

    @Transactional(rollbackFor = Exception.class)
    public OnboardingChannelExecutionResult query(OnboardingChannelExecutionCommand command) {
        Execution execution = execution(command, false);
        var result = execution.adapter().queryOnboardingStatus(new ChannelStatusQuery(execution.context()));
        return apply(execution.current(), result.meta(), result.state());
    }

    private Execution execution(OnboardingChannelExecutionCommand command, boolean submit) {
        if (command == null || command.tenantId() == null || command.tenantId() <= 0 || command
            .merchantId() == null || command.merchantId() <= 0 || command.applicationId() == null || command
                .applicationId() <= 0) {
            throw new MerchantDomainException("Onboarding channel request is invalid");
        }
        WorkflowActor actor = authorizationPort.requireActor(command.tenantId(), command.actorUserId());
        if (!actor.roleCodes().contains(CHANNEL_OPERATIONS)) {
            throw new MerchantDomainException("Channel operations role is required");
        }
        merchantScopeAuthorizationService.requireAccessible(command.tenantId(), command.actorUserId(), command
            .merchantId());
        Application application = application(command, submit);
        ChannelProductKey product = new ChannelProductKey(application.channelCode(), application.productCode());
        ChannelApplicationState current = statePort.lock(command.tenantId(), command.applicationId(), product, application
            .configVersion())
            .orElseThrow(() -> new MerchantDomainException("Onboarding application is not ready for channel execution"));
        String businessSerial = current.businessSerial() == null ? application.applicationNo() : current.businessSerial();
        ChannelCommandContext context = new ChannelCommandContext(command.tenantId(), product, application
            .configVersion(), ChannelBusinessType.ONBOARDING, application.id(), current.businessVersion(), businessSerial, traceId(command, current));
        return new Execution(application, current, context, adapterRegistry.require(application.channelCode()));
    }

    private Application application(OnboardingChannelExecutionCommand command, boolean submit) {
        List<Application> applications = jdbcTemplate.query("""
            SELECT id, application_no, merchant_id, channel_code, product_code, channel_config_version,
                   requirement_version, kyc_version_id, status
            FROM biz_onboarding_application
            WHERE tenant_id = ? AND merchant_id = ? AND id = ? AND deleted = 0
            """, (resultSet, rowNum) -> new Application(resultSet.getLong("id"), resultSet
            .getString("application_no"), resultSet.getLong("merchant_id"), resultSet
                .getString("channel_code"), resultSet.getString("product_code"), resultSet
                    .getString("channel_config_version"), resultSet.getString("requirement_version"), resultSet
                        .getLong("kyc_version_id"), resultSet.getString("status")), command.tenantId(), command
                            .merchantId(), command.applicationId());
        if (applications.size() != 1) {
            throw new MerchantDomainException("Onboarding application is unavailable");
        }
        Application application = applications.get(0);
        if ((submit && !"APPROVED".equals(application.status())) || (!submit && !"CHANNEL_PROCESSING".equals(application
            .status()))) {
            throw new MerchantDomainException(submit
                ? "Only an approved onboarding application can be submitted to a channel"
                : "Only a channel-processing onboarding application can be queried");
        }
        return application;
    }

    private OnboardingChannelExecutionResult apply(ChannelApplicationState current,
                                                    ChannelResultMeta meta,
                                                    ChannelOnboardingState state) {
        int rank = rank(meta.operationStatus());
        boolean terminal = isTerminal(state.finalStatus());
        ChannelMappedStatus mapped = new ChannelMappedStatus(meta.operationStatus(), state, null, rank, terminal);
        ChannelStateMergeResult merged = stateMerger.merge(current, mapped);
        if (!statePort.apply(current, merged, meta.businessSerial(), meta.rawStatusCode(), meta.resultTime())) {
            throw new MerchantDomainException("Onboarding channel state changed concurrently");
        }
        return new OnboardingChannelExecutionResult(current.applicationId(), meta.businessSerial(), meta
            .channelRequestId(), meta.operationStatus(), merged.state(), merged.finalTerminal(), meta.safeMessage(), meta
                .resultTime());
    }

    private int rank(ChannelOperationStatus status) {
        return switch (status) {
            case ACCEPTED -> 10;
            case PROCESSING -> 20;
            case UNCERTAIN -> 30;
            case SUCCEEDED, FAILED, REJECTED -> 100;
        };
    }

    private boolean isTerminal(ChannelStageStatus status) {
        return ChannelStageStatus.SUCCEEDED.equals(status) || ChannelStageStatus.FAILED
            .equals(status) || ChannelStageStatus.REJECTED.equals(status);
    }

    private String traceId(OnboardingChannelExecutionCommand command, ChannelApplicationState current) {
        return command.traceId() == null || command.traceId().isBlank()
            ? "ONBOARDING:%s:%s".formatted(current.applicationId(), current.businessVersion())
            : command.traceId().trim();
    }

    public record OnboardingChannelExecutionCommand(Long tenantId, Long actorUserId, Long merchantId,
                                                    Long applicationId, String traceId) {
    }

    public record OnboardingChannelExecutionResult(Long applicationId, String businessSerial,
                                                   String channelRequestId, ChannelOperationStatus operationStatus,
                                                   ChannelOnboardingState state, boolean finalTerminal,
                                                   String safeMessage, LocalDateTime resultTime) {
    }

    private record Application(Long id, String applicationNo, Long merchantId, String channelCode,
                               String productCode, String configVersion, String requirementVersion,
                               Long kycVersionId, String status) {
    }

    private record Execution(Application application, ChannelApplicationState current,
                             ChannelCommandContext context, ChannelAdapter adapter) {
    }
}
