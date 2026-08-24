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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.merchant.master.application.MerchantRepository;
import top.continew.admin.merchant.master.application.MerchantScopeAuthorizationService;
import top.continew.admin.merchant.master.domain.Merchant;
import top.continew.admin.merchant.master.domain.MerchantDomainException;
import top.continew.admin.merchant.onboarding.application.OnboardingSupplementDiff;
import top.continew.admin.merchant.onboarding.application.OnboardingSupplementRepository;
import top.continew.admin.merchant.onboarding.application.OnboardingSupplementService;
import top.continew.admin.merchant.onboarding.application.SupplementKycSnapshot;
import top.continew.admin.workflow.api.WorkflowMappingService;
import top.continew.admin.workflow.api.WorkflowService;
import top.continew.admin.workflow.dto.WorkflowInstanceMapping;
import top.continew.admin.workflow.dto.WorkflowPage;
import top.continew.admin.workflow.dto.WorkflowProcessHistory;
import top.continew.admin.workflow.dto.WorkflowTask;
import top.continew.admin.workflow.query.WorkflowDoneQuery;
import top.continew.admin.workflow.query.WorkflowTaskQuery;

import java.util.List;

/** Scope-aware task-center projection over Flowable routing and merchant-owned masked data. */
@Service
@RequiredArgsConstructor
public class WorkflowTaskCenterService {

    private static final String BUSINESS_TYPE = "MERCHANT_ONBOARDING";
    private static final TypeReference<List<String>> ISSUE_CODES = new TypeReference<>() {};

    private final WorkflowService workflowService;
    private final WorkflowMappingService mappingService;
    private final MerchantScopeAuthorizationService merchantScopeAuthorizationService;
    private final MerchantRepository merchantRepository;
    private final OnboardingReviewRepository reviewRepository;
    private final OnboardingSupplementRepository supplementRepository;
    private final OnboardingSupplementService supplementService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public WorkflowPage<WorkflowTaskView> pageTodo(WorkflowTaskQuery query) {
        return decorate(workflowService.pageTodo(query), query.tenantId(), query.userId());
    }

    @Transactional(readOnly = true)
    public WorkflowPage<WorkflowTaskView> pageDone(WorkflowDoneQuery query) {
        return decorate(workflowService.pageDone(query), query.tenantId(), query.userId());
    }

    @Transactional(readOnly = true)
    public WorkflowTaskDetail detail(Long tenantId, Long actorUserId, String taskId) {
        WorkflowTask task = workflowService.taskView(tenantId, actorUserId, taskId);
        WorkflowTaskBusinessSummary business = summary(tenantId, actorUserId, task.processInstanceId());
        OnboardingSupplementDiff diff = business.previousKycVersionId() == null
            ? null
            : supplementService.diff(tenantId, actorUserId, business.merchantId(), business.applicationId(), business
                .kycVersionId());
        List<WorkflowReviewHistoryItem> reviews = reviewRepository.listEvidence(tenantId, task.processInstanceId())
            .stream()
            .map(this::review)
            .toList();
        return new WorkflowTaskDetail(task, business, diff, reviews);
    }

    @Transactional(readOnly = true)
    public WorkflowProcessHistory history(Long tenantId, Long actorUserId, String processInstanceId) {
        return workflowService.history(tenantId, actorUserId, processInstanceId);
    }

    private WorkflowPage<WorkflowTaskView> decorate(WorkflowPage<WorkflowTask> page, Long tenantId, Long actorUserId) {
        return new WorkflowPage<>(page.items()
            .stream()
            .map(task -> new WorkflowTaskView(task, summary(tenantId, actorUserId, task.processInstanceId())))
            .toList(), page.total(), page.page(), page.size());
    }

    private WorkflowTaskBusinessSummary summary(Long tenantId, Long actorUserId, String processInstanceId) {
        WorkflowInstanceMapping mapping = mappingService.findByProcessInstanceId(tenantId, processInstanceId)
            .orElseThrow(() -> new MerchantDomainException("Workflow business mapping is unavailable"));
        if (!BUSINESS_TYPE.equals(mapping.businessType())) {
            throw new MerchantDomainException("Workflow business type is unsupported");
        }
        OnboardingReviewContext context = reviewRepository.findContext(tenantId, mapping.businessId())
            .orElseThrow(() -> new MerchantDomainException("Onboarding review context is unavailable"));
        Merchant merchant = merchantScopeAuthorizationService.requireAccessible(tenantId, actorUserId, context
            .merchantId());
        SupplementKycSnapshot kyc = supplementRepository.find(tenantId, context.merchantId(), context
            .applicationId(), context.kycVersionId())
            .orElseThrow(() -> new MerchantDomainException("KYC version is unavailable"));
        return new WorkflowTaskBusinessSummary(context.applicationId(), context.applicationNo(), mapping
            .businessVersion(), merchant.id(), merchant.merchantNo(), merchant.shortName(), kyc.legalName(), kyc
                .legalIdentifierMasked(), merchant.contactMobile() == null
                    ? null
                    : merchant.contactMobile().maskedValue(), context.owningAgentId(), context.channelCode(), context
                        .productCode(), context.applicationStatus(), kyc.id(), kyc.versionNo(), kyc
                            .previousVersionId());
    }

    private WorkflowReviewHistoryItem review(ReviewRecordEvidence evidence) {
        return new WorkflowReviewHistoryItem(evidence.id(), evidence.reviewType(), evidence.reviewerId(), evidence
            .action(), evidence.opinion(), issueCodes(evidence.issueCodesJson()), evidence.modelVersion(), evidence
                .evidenceSummary(), evidence.decisionTime());
    }

    private List<String> issueCodes(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, ISSUE_CODES);
        } catch (JsonProcessingException ex) {
            throw new MerchantDomainException("Review evidence is unavailable");
        }
    }
}
