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

import org.springframework.stereotype.Service;
import top.continew.admin.channel.api.ChannelAdapter;
import top.continew.admin.channel.api.ChannelAdapterRegistry;
import top.continew.admin.channel.dto.ChannelBusinessType;
import top.continew.admin.channel.dto.ChannelCommandContext;
import top.continew.admin.channel.dto.ChannelLimitAdjustmentCommand;
import top.continew.admin.channel.dto.ChannelLimitAdjustmentQuery;
import top.continew.admin.channel.dto.ChannelLimitAdjustmentResult;
import top.continew.admin.channel.dto.ChannelProductKey;
import top.continew.admin.merchant.limit.domain.LimitAdjustment;
import top.continew.admin.merchant.limit.domain.LimitApprovalStatus;
import top.continew.admin.merchant.master.application.MerchantScopeAuthorizationService;
import top.continew.admin.merchant.master.domain.Merchant;
import top.continew.admin.merchant.master.domain.MerchantDomainException;
import top.continew.admin.workflow.api.WorkflowActor;
import top.continew.admin.workflow.api.WorkflowAuthorizationPort;
import top.continew.admin.workflow.api.WorkflowMappingService;
import top.continew.admin.workflow.api.WorkflowService;
import top.continew.admin.workflow.definition.MerchantLimitAdjustmentWorkflowDefinition;
import top.continew.admin.workflow.dto.WorkflowInstanceMapping;
import top.continew.admin.workflow.dto.WorkflowTask;

import java.time.Clock;
import java.time.LocalDateTime;

/** Executes one claimed channel submit/query task before atomically recording its normalized result. */
@Service
public class LimitAdjustmentChannelExecutionService {

    private static final String CHANNEL_SUBMIT_TASK = "channelSubmitTask";
    private static final String CHANNEL_QUERY_TASK = "channelQueryTask";

    private final WorkflowService workflowService;
    private final WorkflowMappingService mappingService;
    private final WorkflowAuthorizationPort authorizationPort;
    private final MerchantScopeAuthorizationService merchantScopeAuthorizationService;
    private final LimitAdjustmentRepository repository;
    private final LimitAdjustmentRevalidationService revalidationService;
    private final ChannelAdapterRegistry adapterRegistry;
    private final LimitAdjustmentProcessService processService;
    private final Clock clock = Clock.systemDefaultZone();

    public LimitAdjustmentChannelExecutionService(WorkflowService workflowService,
                                                  WorkflowMappingService mappingService,
                                                  WorkflowAuthorizationPort authorizationPort,
                                                  MerchantScopeAuthorizationService merchantScopeAuthorizationService,
                                                  LimitAdjustmentRepository repository,
                                                  LimitAdjustmentRevalidationService revalidationService,
                                                  ChannelAdapterRegistry adapterRegistry,
                                                  LimitAdjustmentProcessService processService) {
        this.workflowService = workflowService;
        this.mappingService = mappingService;
        this.authorizationPort = authorizationPort;
        this.merchantScopeAuthorizationService = merchantScopeAuthorizationService;
        this.repository = repository;
        this.revalidationService = revalidationService;
        this.adapterRegistry = adapterRegistry;
        this.processService = processService;
    }

    public LimitAdjustmentProcessResult execute(LimitAdjustmentChannelExecutionCommand command) {
        if (command == null || command.businessVersion() == null || command.businessVersion() <= 0) {
            throw new MerchantDomainException("Limit channel execution request is invalid");
        }
        WorkflowActor actor = authorizationPort.requireActor(command.tenantId(), command.actorUserId());
        if (!actor.roleCodes().contains("CHANNEL_OPERATIONS")) {
            throw new MerchantDomainException("Channel operations role is required");
        }
        WorkflowTask task = workflowService.task(command.tenantId(), command.actorUserId(), command.taskId());
        if (!actor.flowableUserId().equals(task.assignee()) || !CHANNEL_SUBMIT_TASK.equals(task
            .taskDefinitionKey()) && !CHANNEL_QUERY_TASK.equals(task
                .taskDefinitionKey()) || !MerchantLimitAdjustmentWorkflowDefinition.PROCESS_KEY.equals(task
                    .processDefinitionKey())) {
            throw new MerchantDomainException("Limit channel task is not executable by the actor");
        }
        WorkflowInstanceMapping mapping = mappingService.findByProcessInstanceId(command.tenantId(), task
            .processInstanceId())
            .orElseThrow(() -> new MerchantDomainException("Limit workflow mapping is unavailable"));
        if (!"MERCHANT_LIMIT_ADJUSTMENT".equals(mapping.businessType()) || !mapping.businessVersion()
            .equals(command.businessVersion())) {
            throw new MerchantDomainException("Limit workflow business version is invalid");
        }
        LimitAdjustment request = repository.findByRequestId(command.tenantId(), mapping.businessId())
            .orElseThrow(() -> new MerchantDomainException("Limit adjustment request is unavailable"));
        if (!mapping.processInstanceId().equals(request.processInstanceId()) || !LimitApprovalStatus.APPROVED
            .equals(request.approvalStatus())) {
            throw new MerchantDomainException("Limit adjustment is not ready for channel execution");
        }
        Merchant merchant = merchantScopeAuthorizationService.requireAccessible(command.tenantId(), actor
            .userId(), request.merchantId());
        LimitAdjustmentRevalidationService.Snapshot snapshot = revalidationService
            .requireCurrent(request, merchant, LocalDateTime.now(clock));
        ChannelCommandContext context = new ChannelCommandContext(command.tenantId(), new ChannelProductKey(request
            .channelCode(), snapshot.eligibility().productCode()), request
                .channelConfigVersion(), ChannelBusinessType.LIMIT_ADJUSTMENT, request.id(), mapping
                    .businessVersion(), request.requestNo(), traceId(command, request, task));
        ChannelAdapter adapter = adapterRegistry.require(request.channelCode());
        ChannelLimitAdjustmentResult result = CHANNEL_SUBMIT_TASK.equals(task.taskDefinitionKey())
            ? adapter.adjustLimit(new ChannelLimitAdjustmentCommand(context, request.id(), request
                .platformCode(), request.currency(), request.originalLimit(), request.requestedLimit(), request
                    .normalizedLimit(), "LIMIT_ADJUSTMENT"))
            : adapter.queryLimitAdjustment(new ChannelLimitAdjustmentQuery(context, request.id()));
        return processService.recordChannelResult(new LimitAdjustmentChannelResultCommand(command.tenantId(), actor
            .userId(), task.taskId(), mapping.businessVersion(), result, command.ipAddress()));
    }

    private String traceId(LimitAdjustmentChannelExecutionCommand command, LimitAdjustment request, WorkflowTask task) {
        if (command.traceId() != null && !command.traceId().isBlank()) {
            return command.traceId().trim();
        }
        return "LIMIT:%s:%s".formatted(request.id(), task.taskId());
    }
}