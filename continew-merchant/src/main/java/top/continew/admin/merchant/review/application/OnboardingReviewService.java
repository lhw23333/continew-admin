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

package top.continew.admin.merchant.review.application;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.merchant.master.domain.MerchantDomainException;
import top.continew.admin.merchant.onboarding.application.OnboardingSupplementRepository;
import top.continew.admin.merchant.onboarding.application.SupplementKycSnapshot;
import top.continew.admin.merchant.security.audit.application.SecurityAuditWriter;
import top.continew.admin.merchant.security.audit.domain.SecurityAuditRecord;
import top.continew.admin.merchant.security.audit.domain.SecurityAuditResult;
import top.continew.admin.workflow.api.WorkflowActor;
import top.continew.admin.workflow.api.WorkflowAuthorizationPort;
import top.continew.admin.workflow.api.WorkflowMappingService;
import top.continew.admin.workflow.api.WorkflowService;
import top.continew.admin.workflow.command.CompleteTaskCommand;
import top.continew.admin.workflow.command.TransferTaskCommand;
import top.continew.admin.workflow.dto.WorkflowInstanceMapping;
import top.continew.admin.workflow.dto.WorkflowTask;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Applies low-cost separation-of-duties policy and atomically records human onboarding review actions. */
@Service
public class OnboardingReviewService {

    private static final Set<String> REVIEW_ROLES = Set.of("MERCHANT_REVIEWER", "RISK_REVIEWER");
    private static final String RISK_REVIEWER = "RISK_REVIEWER";
    private static final Pattern ISSUE_CODE = Pattern.compile("[A-Z][A-Z0-9_]{1,63}");
    private static final Pattern MOBILE = Pattern.compile("(?<![0-9])1[3-9][0-9]{9}(?![0-9])");
    private static final Pattern LONG_NUMBER = Pattern.compile("(?<![0-9])[0-9]{12,32}(?![0-9])");
    private static final Pattern PERMANENT_URL = Pattern.compile("(?i)https?://");

    private final WorkflowService workflowService;
    private final WorkflowMappingService mappingService;
    private final WorkflowAuthorizationPort authorizationPort;
    private final OnboardingReviewRepository reviewRepository;
    private final OnboardingSupplementRepository supplementRepository;
    private final IdentifierGenerator identifierGenerator;
    private final ObjectMapper objectMapper;
    private final SecurityAuditWriter securityAuditWriter;
    private final Clock clock = Clock.systemDefaultZone();

    public OnboardingReviewService(WorkflowService workflowService,
                                   WorkflowMappingService mappingService,
                                   WorkflowAuthorizationPort authorizationPort,
                                   OnboardingReviewRepository reviewRepository,
                                   OnboardingSupplementRepository supplementRepository,
                                   IdentifierGenerator identifierGenerator,
                                   ObjectMapper objectMapper,
                                   SecurityAuditWriter securityAuditWriter) {
        this.workflowService = workflowService;
        this.mappingService = mappingService;
        this.authorizationPort = authorizationPort;
        this.reviewRepository = reviewRepository;
        this.supplementRepository = supplementRepository;
        this.identifierGenerator = identifierGenerator;
        this.objectMapper = objectMapper;
        this.securityAuditWriter = securityAuditWriter;
    }

    @Transactional
    public OnboardingReviewResult review(OnboardingReviewCommand command) {
        requireCommand(command);
        WorkflowActor actor = authorizationPort.requireActor(command.tenantId(), command.actorUserId());
        WorkflowTask task = workflowService.task(command.tenantId(), command.actorUserId(), command.taskId());
        requireAssigned(task, actor);
        WorkflowInstanceMapping mapping = requireOnboardingMapping(command.tenantId(), task.processInstanceId(), command
            .businessVersion());
        OnboardingReviewContext context = reviewRepository.findContext(command.tenantId(), mapping.businessId())
            .orElseThrow(() -> new MerchantDomainException("Onboarding review context is unavailable"));
        authorizationPort.requireBusinessAccess(actor, mapping.businessType(), mapping.businessId());
        ReviewDecision decision = decision(command, actor, context);
        LocalDateTime now = LocalDateTime.now(clock);
        if (OnboardingReviewAction.RESUBMIT.equals(command.action())) {
            SupplementKycSnapshot supplement = supplementRepository.find(command.tenantId(), context
                .merchantId(), context.applicationId(), context.kycVersionId())
                .orElseThrow(() -> new MerchantDomainException("Supplement KYC version is unavailable"));
            if (!supplementRepository.freeze(command.tenantId(), context.applicationId(), context
                .kycVersionId(), supplement.rowVersion(), actor.userId(), now)) {
                throw new MerchantDomainException("Supplement KYC version changed concurrently");
            }
        }
        Long reviewRecordId = identifierGenerator.nextId(new Object()).longValue();
        reviewRepository.insert(new ReviewRecordDraft(reviewRecordId, command.tenantId(), mapping
            .businessType(), mapping.businessId(), mapping.businessVersion(), mapping.processInstanceId(), task
                .taskId(), actor.userId(), command.action().name(), decision.opinion(), json(decision
                    .issueCodes()), json(Map.of("statusBefore", context.applicationStatus(), "statusAfter", decision
                        .targetStatus())), now));
        if (!reviewRepository.updateApplicationStatus(command.tenantId(), context.applicationId(), context
            .applicationStatus(), decision.targetStatus(), context.rowVersion(), actor.userId(), now)) {
            throw new MerchantDomainException("Onboarding review state changed concurrently");
        }
        workflowService.complete(new CompleteTaskCommand(command.tenantId(), task.taskId(), actor.userId(), Map
            .of("reviewAction", command.action().name(), "requiresSupplement", OnboardingReviewAction.REQUEST_SUPPLEMENT
                .equals(command.action()), "kycVersion", command.businessVersion())));
        audit(command.tenantId(), actor.userId(), context, mapping.businessVersion(), "WORKFLOW_REVIEW_" + command
            .action()
            .name(), "reviewRecordId=" + reviewRecordId, command.ipAddress(), now);
        return new OnboardingReviewResult(reviewRecordId, context.applicationId(), mapping.businessVersion(), task
            .taskId(), mapping.processInstanceId(), command.action().name(), decision.targetStatus(), actor
                .userId(), null, decision.issueCodes(), now);
    }

    @Transactional
    public OnboardingReviewResult transfer(OnboardingTransferCommand command) {
        if (command == null || command.targetUserId() == null || command.targetUserId() <= 0 || command
            .businessVersion() == null || command.businessVersion() <= 0) {
            throw new MerchantDomainException("Workflow transfer request is invalid");
        }
        WorkflowActor actor = authorizationPort.requireActor(command.tenantId(), command.actorUserId());
        if (!actor.roleCodes().contains(RISK_REVIEWER)) {
            throw new MerchantDomainException("Workflow transfer permission is required");
        }
        WorkflowActor target = authorizationPort.requireActor(command.tenantId(), command.targetUserId());
        if (target.userId().equals(actor.userId()) || target.roleCodes().stream().noneMatch(REVIEW_ROLES::contains)) {
            throw new MerchantDomainException("Workflow transfer target is invalid");
        }
        WorkflowTask task = workflowService.task(command.tenantId(), command.actorUserId(), command.taskId());
        requireAssigned(task, actor);
        WorkflowInstanceMapping mapping = requireOnboardingMapping(command.tenantId(), task.processInstanceId(), command
            .businessVersion());
        authorizationPort.requireBusinessAccess(target, mapping.businessType(), mapping.businessId());
        OnboardingReviewContext context = reviewRepository.findContext(command.tenantId(), mapping.businessId())
            .orElseThrow(() -> new MerchantDomainException("Onboarding review context is unavailable"));
        String reason = opinion(command.reason(), true);
        LocalDateTime now = LocalDateTime.now(clock);
        Long reviewRecordId = identifierGenerator.nextId(new Object()).longValue();
        reviewRepository.insert(new ReviewRecordDraft(reviewRecordId, command.tenantId(), mapping
            .businessType(), mapping.businessId(), mapping.businessVersion(), mapping.processInstanceId(), task
                .taskId(), actor.userId(), "TRANSFER", reason, "[]", json(Map.of("targetUserId", target
                    .userId())), now));
        workflowService.transfer(new TransferTaskCommand(command.tenantId(), task.taskId(), actor.userId(), target
            .userId()));
        audit(command.tenantId(), actor.userId(), context, mapping
            .businessVersion(), "WORKFLOW_REVIEW_TRANSFER", "reviewRecordId=%s;targetUserId=%s"
                .formatted(reviewRecordId, target.userId()), command.ipAddress(), now);
        return new OnboardingReviewResult(reviewRecordId, context.applicationId(), mapping.businessVersion(), task
            .taskId(), mapping.processInstanceId(), "TRANSFER", context.applicationStatus(), actor.userId(), target
                .userId(), List.of(), now);
    }

    private ReviewDecision decision(OnboardingReviewCommand command,
                                    WorkflowActor actor,
                                    OnboardingReviewContext context) {
        boolean resubmit = OnboardingReviewAction.RESUBMIT.equals(command.action());
        if (resubmit) {
            if (!actor.userId().equals(context.submittedBy()) && !actor.userId()
                .equals(context.merchantOperatorUserId())) {
                throw new MerchantDomainException("Only the applicant or merchant operator may resubmit");
            }
            requireStatus(context.applicationStatus(), Set.of("SUPPLEMENT_REQUIRED"));
            return new ReviewDecision("UNDER_REVIEW", opinion(command.opinion(), false), List.of());
        }
        if (actor.roleCodes().stream().noneMatch(REVIEW_ROLES::contains)) {
            throw new MerchantDomainException("Workflow review role is required");
        }
        if (actor.userId().equals(context.submittedBy())) {
            throw new MerchantDomainException("The applicant cannot review the same business version");
        }
        requireStatus(context.applicationStatus(), Set.of("SUBMITTED", "UNDER_REVIEW"));
        return switch (command.action()) {
            case APPROVE -> new ReviewDecision("APPROVED", opinion(command.opinion(), false), List.of());
            case REJECT -> new ReviewDecision("REJECTED", opinion(command.opinion(), true), List.of());
            case REQUEST_SUPPLEMENT -> new ReviewDecision("SUPPLEMENT_REQUIRED", opinion(command
                .opinion(), true), issueCodes(command.issueCodes()));
            case RESUBMIT -> throw new MerchantDomainException("Invalid resubmit policy state");
        };
    }

    private WorkflowInstanceMapping requireOnboardingMapping(Long tenantId,
                                                             String processInstanceId,
                                                             Long businessVersion) {
        WorkflowInstanceMapping mapping = mappingService.findByProcessInstanceId(tenantId, processInstanceId)
            .orElseThrow(() -> new MerchantDomainException("Workflow mapping is unavailable"));
        if (!"MERCHANT_ONBOARDING".equals(mapping.businessType()) || !mapping.businessVersion()
            .equals(businessVersion)) {
            throw new MerchantDomainException("Workflow business version is invalid");
        }
        return mapping;
    }

    private void requireAssigned(WorkflowTask task, WorkflowActor actor) {
        if (!actor.flowableUserId().equals(task.assignee())) {
            throw new MerchantDomainException("Workflow task must be claimed by the actor");
        }
    }

    private void requireCommand(OnboardingReviewCommand command) {
        if (command == null || command.action() == null || command.businessVersion() == null || command
            .businessVersion() <= 0) {
            throw new MerchantDomainException("Workflow review request is invalid");
        }
    }

    private void requireStatus(String current, Set<String> allowed) {
        if (!allowed.contains(current)) {
            throw new MerchantDomainException("Workflow review action is not allowed in the current state");
        }
    }

    private List<String> issueCodes(List<String> values) {
        if (values == null || values.isEmpty() || values.size() > 20) {
            throw new MerchantDomainException("Supplement issue codes are required");
        }
        List<String> normalized = values.stream()
            .map(value -> value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT))
            .distinct()
            .toList();
        if (normalized.size() != values.size() || normalized.stream()
            .anyMatch(value -> !ISSUE_CODE.matcher(value).matches())) {
            throw new MerchantDomainException("Supplement issue codes are invalid");
        }
        return normalized;
    }

    private String opinion(String value, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) {
                throw new MerchantDomainException("Review opinion is required");
            }
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 2000 || normalized.chars().anyMatch(Character::isISOControl) || MOBILE
            .matcher(normalized)
            .find() || LONG_NUMBER.matcher(normalized).find() || PERMANENT_URL.matcher(normalized).find()) {
            throw new MerchantDomainException("Review opinion contains invalid or sensitive content");
        }
        return normalized;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new MerchantDomainException("Review evidence serialization failed");
        }
    }

    private void audit(Long tenantId,
                       Long actorUserId,
                       OnboardingReviewContext context,
                       Long businessVersion,
                       String action,
                       String reason,
                       String ipAddress,
                       LocalDateTime now) {
        securityAuditWriter.append(new SecurityAuditRecord(tenantId, actorUserId, context
            .owningAgentId(), action, "ONBOARDING_APPLICATION", context
                .applicationId(), businessVersion, "REVIEW_ACTION", reason, ipAddress, SecurityAuditResult.SUCCESS, null, now));
    }

    private record ReviewDecision(String targetStatus, String opinion, List<String> issueCodes) {
    }
}
