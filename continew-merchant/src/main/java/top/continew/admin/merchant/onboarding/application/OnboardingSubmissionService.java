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

package top.continew.admin.merchant.onboarding.application;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.merchant.master.application.MerchantScopeAuthorizationService;
import top.continew.admin.merchant.master.domain.Merchant;
import top.continew.admin.merchant.master.domain.MerchantAccessDeniedException;
import top.continew.admin.merchant.master.domain.MerchantDomainException;
import top.continew.admin.merchant.security.audit.application.SecurityAuditWriter;
import top.continew.admin.merchant.security.audit.domain.SecurityAuditRecord;
import top.continew.admin.merchant.security.audit.domain.SecurityAuditResult;
import top.continew.starter.extension.tenant.context.TenantContextHolder;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

/** Revalidates and atomically freezes a draft while creating one workflow-start outbox request. */
@Service
@RequiredArgsConstructor
public class OnboardingSubmissionService {

    private static final String SUBMITTED = "SUBMITTED";
    private static final String DRAFT = "DRAFT";
    private static final String PROCESS_DEFINITION_KEY = "merchant-onboarding-review-v1";
    private static final String EVENT_TYPE = "MERCHANT_ONBOARDING_WORKFLOW_START_REQUESTED";
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9._:-]{8,128}");
    private static final Pattern TRACE_ID = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

    private final MerchantScopeAuthorizationService merchantScopeAuthorizationService;
    private final OnboardingSubmissionRepository submissionRepository;
    private final OnboardingFinalPreviewService previewService;
    private final IdentifierGenerator identifierGenerator;
    private final ObjectMapper objectMapper;
    private final SecurityAuditWriter securityAuditWriter;
    private final Clock clock = Clock.systemDefaultZone();

    @Transactional
    public OnboardingSubmissionResult submit(OnboardingSubmissionCommand command) {
        requireTenant(command.tenantId());
        String idempotencyKey = normalizeIdempotencyKey(command.idempotencyKey());
        String traceId = normalizeTraceId(command.traceId());
        if (command.expectedVersion() == null || command.expectedVersion() < 0) {
            throw new MerchantDomainException("Onboarding submission version is invalid");
        }
        Merchant merchant = merchantScopeAuthorizationService.requireAccessible(command.tenantId(), command
            .actorUserId(), command.merchantId());
        OnboardingSubmissionState state = submissionRepository.lock(command.tenantId(), command.merchantId(), command
            .applicationId()).orElseThrow(MerchantAccessDeniedException::new);
        String workflowEventKey = workflowEventKey(state);
        if (!DRAFT.equals(state.applicationStatus())) {
            return existing(state, idempotencyKey, workflowEventKey);
        }
        if (!DRAFT.equals(state.kycStatus()) || !state.kycRowVersion().equals(command.expectedVersion())) {
            throw new OnboardingDraftConflictException();
        }
        OnboardingFinalPreview preview = previewService.preview(command.tenantId(), command.actorUserId(), command
            .merchantId(), command.applicationId());
        if (!preview.readyForSubmission()) {
            throw new OnboardingSubmissionBlockedException(preview.blockers()
                .stream()
                .map(OnboardingFinalPreview.PreviewBlocker::code)
                .toList());
        }
        LocalDateTime now = LocalDateTime.now(clock);
        Long submittedVersion = state.kycRowVersion() + 1;
        Long workflowEventId = identifierGenerator.nextId(new Object()).longValue();
        String payload = workflowPayload(state, command.actorUserId(), submittedVersion);
        OnboardingWorkflowRequest workflowRequest = submissionRepository.submit(state, command
            .actorUserId(), idempotencyKey, workflowEventId, workflowEventKey, payload, traceId, now);
        securityAuditWriter.append(new SecurityAuditRecord(command.tenantId(), command.actorUserId(), merchant
            .owningAgentId(), "ONBOARDING_SUBMIT", "ONBOARDING_APPLICATION", state
                .applicationId(), submittedVersion, "WORKFLOW_REQUEST", "eventId=%s;status=%s".formatted(workflowRequest
                    .eventId(), workflowRequest.status()), command
                        .ipAddress(), SecurityAuditResult.SUCCESS, null, now));
        return result(state, SUBMITTED, submittedVersion, idempotencyKey, now, workflowRequest);
    }

    private OnboardingSubmissionResult existing(OnboardingSubmissionState state,
                                                String idempotencyKey,
                                                String workflowEventKey) {
        if (!SUBMITTED.equals(state.applicationStatus()) || !SUBMITTED.equals(state.kycStatus()) || !idempotencyKey
            .equals(state.idempotencyKey())) {
            throw new MerchantDomainException("Onboarding application has already been submitted");
        }
        OnboardingWorkflowRequest workflowRequest = submissionRepository.findWorkflowRequest(state
            .tenantId(), workflowEventKey).orElse(null);
        if (workflowRequest == null) {
            throw new MerchantDomainException("Submitted onboarding workflow request is unavailable");
        }
        return result(state, state.applicationStatus(), state.kycRowVersion(), state.idempotencyKey(), state
            .submittedTime(), workflowRequest);
    }

    private OnboardingSubmissionResult result(OnboardingSubmissionState state,
                                              String status,
                                              Long businessVersion,
                                              String idempotencyKey,
                                              LocalDateTime submittedTime,
                                              OnboardingWorkflowRequest workflowRequest) {
        return new OnboardingSubmissionResult(state.applicationId(), state.applicationNo(), status, state
            .kycVersionId(), state.kycVersionNo(), businessVersion, idempotencyKey, submittedTime, workflowRequest);
    }

    private String workflowPayload(OnboardingSubmissionState state, Long applicantId, Long businessVersion) {
        WorkflowStartPayload payload = new WorkflowStartPayload(state.applicationId(), state.merchantId(), state
            .owningAgentId(), state.kycVersionId(), state.kycVersionNo(), businessVersion, state.channelCode(), state
                .productCode(), applicantId, PROCESS_DEFINITION_KEY, "%s:MERCHANT_ONBOARDING:%s:%s".formatted(state
                    .tenantId(), state.applicationId(), businessVersion));
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new MerchantDomainException("Onboarding workflow request serialization failed");
        }
    }

    private String workflowEventKey(OnboardingSubmissionState state) {
        return "ONBOARDING_WORKFLOW_START:%s:%s:%s".formatted(state.tenantId(), state.applicationId(), state
            .kycVersionId());
    }

    private String normalizeIdempotencyKey(String value) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || !IDEMPOTENCY_KEY.matcher(normalized).matches()) {
            throw new MerchantDomainException("Onboarding idempotency key is invalid");
        }
        return normalized;
    }

    private String normalizeTraceId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (!TRACE_ID.matcher(normalized).matches()) {
            throw new MerchantDomainException("Onboarding trace ID is invalid");
        }
        return normalized;
    }

    private void requireTenant(Long tenantId) {
        if (tenantId == null || !tenantId.equals(TenantContextHolder.getTenantId())) {
            throw new MerchantAccessDeniedException();
        }
    }

    private record WorkflowStartPayload(Long applicationId, Long merchantId, Long owningAgentId, Long kycVersionId,
                                        Integer kycVersion, Long businessVersion, String channelCode,
                                        String productCode, Long applicantId, String processDefinitionKey,
                                        String businessKey) {
    }
}
