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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.channel.dto.ChannelLimitAdjustmentResult;
import top.continew.admin.merchant.limit.domain.LimitAdjustment;
import top.continew.admin.merchant.limit.domain.LimitApprovalStatus;
import top.continew.admin.merchant.limit.domain.LimitChannelStatus;
import top.continew.admin.merchant.limit.domain.LimitEffectiveStatus;
import top.continew.admin.merchant.master.application.MerchantScopeAuthorizationService;
import top.continew.admin.merchant.master.domain.Merchant;
import top.continew.admin.merchant.master.domain.MerchantDomainException;
import top.continew.admin.merchant.security.audit.application.SecurityAuditWriter;
import top.continew.admin.merchant.security.audit.domain.SecurityAuditRecord;
import top.continew.admin.merchant.security.audit.domain.SecurityAuditResult;
import top.continew.admin.workflow.api.WorkflowActor;
import top.continew.admin.workflow.api.WorkflowAuthorizationPort;
import top.continew.admin.workflow.api.WorkflowMappingService;
import top.continew.admin.workflow.api.WorkflowService;
import top.continew.admin.workflow.command.CompleteTaskCommand;
import top.continew.admin.workflow.definition.MerchantLimitAdjustmentWorkflowDefinition;
import top.continew.admin.workflow.dto.WorkflowInstanceMapping;
import top.continew.admin.workflow.dto.WorkflowTask;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Applies limit review and channel outcomes while keeping domain state authoritative. */
@Service
public class LimitAdjustmentProcessService {

    private static final String BUSINESS_TYPE = "MERCHANT_LIMIT_ADJUSTMENT";
    private static final String REVIEW_TASK = "limitReviewTask";
    private static final String CHANNEL_SUBMIT_TASK = "channelSubmitTask";
    private static final String CHANNEL_QUERY_TASK = "channelQueryTask";
    private static final Set<String> REVIEW_ROLES = Set.of("MERCHANT_REVIEWER", "RISK_REVIEWER");
    private static final Pattern MOBILE = Pattern.compile("(?<![0-9])1[3-9][0-9]{9}(?![0-9])");
    private static final Pattern LONG_NUMBER = Pattern.compile("(?<![0-9])[0-9]{12,32}(?![0-9])");
    private static final Pattern PERMANENT_URL = Pattern.compile("(?i)https?://");

    private final WorkflowService workflowService;
    private final WorkflowMappingService mappingService;
    private final WorkflowAuthorizationPort authorizationPort;
    private final MerchantScopeAuthorizationService merchantScopeAuthorizationService;
    private final LimitAdjustmentRepository repository;
    private final IdentifierGenerator identifierGenerator;
    private final LimitAdjustmentRevalidationService revalidationService;
    private final SecurityAuditWriter securityAuditWriter;
    private final Clock clock = Clock.systemDefaultZone();

    public LimitAdjustmentProcessService(WorkflowService workflowService,
                                         WorkflowMappingService mappingService,
                                         WorkflowAuthorizationPort authorizationPort,
                                         MerchantScopeAuthorizationService merchantScopeAuthorizationService,
                                         LimitAdjustmentRepository repository,
                                         IdentifierGenerator identifierGenerator,
                                         LimitAdjustmentRevalidationService revalidationService,
                                         SecurityAuditWriter securityAuditWriter) {
        this.workflowService = workflowService;
        this.mappingService = mappingService;
        this.authorizationPort = authorizationPort;
        this.merchantScopeAuthorizationService = merchantScopeAuthorizationService;
        this.repository = repository;
        this.identifierGenerator = identifierGenerator;
        this.revalidationService = revalidationService;
        this.securityAuditWriter = securityAuditWriter;
    }

    @Transactional
    public LimitAdjustmentProcessResult review(LimitAdjustmentReviewCommand command) {
        requireReviewCommand(command);
        WorkflowActor actor = authorizationPort.requireActor(command.tenantId(), command.actorUserId());
        WorkflowTask task = workflowService.task(command.tenantId(), command.actorUserId(), command.taskId());
        requireAssigned(task, actor);
        if (!REVIEW_TASK.equals(task.taskDefinitionKey())) {
            throw invalidState();
        }
        WorkflowInstanceMapping mapping = requireMapping(command.tenantId(), task, command.businessVersion());
        LimitAdjustment request = requireRequest(mapping);
        Merchant merchant = merchantScopeAuthorizationService.requireAccessible(command.tenantId(), actor
            .userId(), request.merchantId());
        if (actor.roleCodes().stream().noneMatch(REVIEW_ROLES::contains)) {
            throw new MerchantDomainException("Limit adjustment reviewer role is required");
        }
        if (actor.userId().equals(request.applicantId())) {
            throw new MerchantDomainException("The applicant cannot review the same limit request");
        }
        if (!LimitApprovalStatus.PENDING.equals(request.approvalStatus()) || !LimitChannelStatus.NOT_SUBMITTED
            .equals(request.channelStatus()) || !LimitEffectiveStatus.NOT_EFFECTIVE.equals(request.effectiveStatus())) {
            throw invalidState();
        }
        String opinion = opinion(command.opinion(), LimitAdjustmentReviewAction.REJECT.equals(command.action()));
        LimitApprovalStatus target = LimitAdjustmentReviewAction.APPROVE.equals(command.action())
            ? LimitApprovalStatus.APPROVED
            : LimitApprovalStatus.REJECTED;
        LocalDateTime now = LocalDateTime.now(clock);
        if (LimitApprovalStatus.APPROVED.equals(target)) {
            revalidationService.requireCurrent(request, merchant, now);
        }
        LimitAdjustment updated = repository.applyReviewDecision(command.tenantId(), request.id(), request
            .rowVersion(), target, opinion, actor.userId(), now);
        String action = command.action().name();
        repository.appendHistory(new LimitAdjustmentHistoryDraft(identifierGenerator.nextId(new Object())
            .longValue(), updated, action, actor.userId(), now));
        workflowService.complete(new CompleteTaskCommand(command.tenantId(), task.taskId(), actor.userId(), Map
            .of("reviewAction", action)));
        audit(updated, actor.userId(), "LIMIT_ADJUSTMENT_" + action, "taskId=" + task.taskId(), command
            .ipAddress(), now);
        return new LimitAdjustmentProcessResult(updated, task.taskId(), mapping.processInstanceId(), action, now);
    }

    @Transactional
    public LimitAdjustmentProcessResult recordChannelResult(LimitAdjustmentChannelResultCommand command) {
        if (command == null || command.result() == null || command.businessVersion() == null || command
            .businessVersion() <= 0) {
            throw new MerchantDomainException("Limit channel result is invalid");
        }
        WorkflowActor actor = authorizationPort.requireActor(command.tenantId(), command.actorUserId());
        WorkflowTask task = workflowService.task(command.tenantId(), command.actorUserId(), command.taskId());
        requireAssigned(task, actor);
        if (!CHANNEL_SUBMIT_TASK.equals(task.taskDefinitionKey()) && !CHANNEL_QUERY_TASK.equals(task
            .taskDefinitionKey())) {
            throw invalidState();
        }
        WorkflowInstanceMapping mapping = requireMapping(command.tenantId(), task, command.businessVersion());
        LimitAdjustment request = requireRequest(mapping);
        merchantScopeAuthorizationService.requireAccessible(command.tenantId(), actor.userId(), request.merchantId());
        if (!actor.roleCodes().contains("CHANNEL_OPERATIONS") || !LimitApprovalStatus.APPROVED.equals(request
            .approvalStatus()) || LimitEffectiveStatus.EFFECTIVE.equals(request.effectiveStatus())) {
            throw invalidState();
        }
        validateChannelResult(request, command.result());
        LocalDateTime now = LocalDateTime.now(clock);
        ChannelTransition transition = transition(command.result());
        LimitAdjustment updated = repository.applyChannelResult(command.tenantId(), request.id(), request
            .rowVersion(), transition.channelStatus(), transition.effectiveStatus(), command.result()
                .effectiveLimit(), command.result().effectiveTime(), command.result().meta().rawStatusCode(), command
                    .result()
                    .meta()
                    .safeMessage(), actor.userId(), now);
        String action = transition.action(CHANNEL_SUBMIT_TASK.equals(task.taskDefinitionKey()));
        repository.appendHistory(new LimitAdjustmentHistoryDraft(identifierGenerator.nextId(new Object())
            .longValue(), updated, action, actor.userId(), now));
        workflowService.complete(new CompleteTaskCommand(command.tenantId(), task.taskId(), actor.userId(), Map
            .of("channelStatus", command.result().limitStatus().name())));
        audit(updated, actor.userId(), "LIMIT_ADJUSTMENT_" + action, "taskId=%s;rawCode=%s".formatted(task
            .taskId(), safe(command.result().meta().rawStatusCode())), command.ipAddress(), now);
        return new LimitAdjustmentProcessResult(updated, task.taskId(), mapping.processInstanceId(), action, now);
    }

    private WorkflowInstanceMapping requireMapping(Long tenantId, WorkflowTask task, Long businessVersion) {
        if (!MerchantLimitAdjustmentWorkflowDefinition.PROCESS_KEY.equals(task.processDefinitionKey())) {
            throw invalidState();
        }
        WorkflowInstanceMapping mapping = mappingService.findByProcessInstanceId(tenantId, task.processInstanceId())
            .orElseThrow(() -> new MerchantDomainException("Limit workflow mapping is unavailable"));
        if (!BUSINESS_TYPE.equals(mapping.businessType()) || !mapping.businessVersion()
            .equals(businessVersion) || !MerchantLimitAdjustmentWorkflowDefinition.PROCESS_KEY.equals(mapping
                .processDefinitionKey())) {
            throw new MerchantDomainException("Limit workflow business version is invalid");
        }
        return mapping;
    }

    private LimitAdjustment requireRequest(WorkflowInstanceMapping mapping) {
        LimitAdjustment request = repository.findByRequestId(mapping.tenantId(), mapping.businessId())
            .orElseThrow(() -> new MerchantDomainException("Limit adjustment request is unavailable"));
        if (!mapping.processInstanceId().equals(request.processInstanceId())) {
            throw new MerchantDomainException("Limit adjustment workflow binding is inconsistent");
        }
        return request;
    }

    private void validateChannelResult(LimitAdjustment request, ChannelLimitAdjustmentResult result) {
        if (!request.id().equals(result.requestId()) || !request.platformCode()
            .equals(result.platformCode()) || !request.currency().equals(result.currency()) || request.normalizedLimit()
                .compareTo(result.requestedLimit()) != 0) {
            throw new MerchantDomainException("Limit channel result does not match the request");
        }
    }

    private ChannelTransition transition(ChannelLimitAdjustmentResult result) {
        return switch (result.limitStatus()) {
            case ACCEPTED -> new ChannelTransition(LimitChannelStatus.SUBMITTED, LimitEffectiveStatus.NOT_EFFECTIVE);
            case PROCESSING -> new ChannelTransition(LimitChannelStatus.PROCESSING, LimitEffectiveStatus.NOT_EFFECTIVE);
            case UNCERTAIN -> new ChannelTransition(LimitChannelStatus.UNCERTAIN, LimitEffectiveStatus.NOT_EFFECTIVE);
            case REJECTED -> new ChannelTransition(LimitChannelStatus.REJECTED, LimitEffectiveStatus.NOT_EFFECTIVE);
            case FAILED -> new ChannelTransition(LimitChannelStatus.FAILED, LimitEffectiveStatus.NOT_EFFECTIVE);
            case EFFECTIVE -> new ChannelTransition(LimitChannelStatus.SUCCEEDED, LimitEffectiveStatus.EFFECTIVE);
        };
    }

    private void requireReviewCommand(LimitAdjustmentReviewCommand command) {
        if (command == null || command.action() == null || command.businessVersion() == null || command
            .businessVersion() <= 0) {
            throw new MerchantDomainException("Limit review request is invalid");
        }
    }

    private void requireAssigned(WorkflowTask task, WorkflowActor actor) {
        if (!actor.flowableUserId().equals(task.assignee())) {
            throw new MerchantDomainException("Limit workflow task must be claimed by the actor");
        }
    }

    private String opinion(String value, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) {
                throw new MerchantDomainException("Limit review opinion is required");
            }
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 2000 || normalized.chars().anyMatch(Character::isISOControl) || MOBILE
            .matcher(normalized)
            .find() || LONG_NUMBER.matcher(normalized).find() || PERMANENT_URL.matcher(normalized).find()) {
            throw new MerchantDomainException("Limit review opinion contains invalid or sensitive content");
        }
        return normalized;
    }

    private void audit(LimitAdjustment request,
                       Long actorUserId,
                       String action,
                       String reason,
                       String ipAddress,
                       LocalDateTime now) {
        securityAuditWriter.append(new SecurityAuditRecord(request.tenantId(), actorUserId, request
            .owningAgentId(), action, "LIMIT_ADJUSTMENT", request.id(), request
                .rowVersion(), null, reason, ipAddress, SecurityAuditResult.SUCCESS, null, now));
    }

    private String safe(String value) {
        return value == null ? "NONE" : value;
    }

    private MerchantDomainException invalidState() {
        return new MerchantDomainException("Limit workflow action is not allowed in the current state");
    }

    private record ChannelTransition(LimitChannelStatus channelStatus, LimitEffectiveStatus effectiveStatus) {
        private String action(boolean submissionTask) {
            if (LimitEffectiveStatus.EFFECTIVE.equals(effectiveStatus)) {
                return "CHANNEL_EFFECTIVE";
            }
            if (LimitChannelStatus.FAILED.equals(channelStatus) || LimitChannelStatus.REJECTED.equals(channelStatus)) {
                return "CHANNEL_FAILED";
            }
            return submissionTask ? "CHANNEL_SUBMIT" : "CHANNEL_QUERY";
        }
    }
}