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

package top.continew.admin.merchant.onboarding.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import top.continew.admin.merchant.master.domain.MerchantDomainException;
import top.continew.admin.merchant.onboarding.application.OnboardingDraftConflictException;
import top.continew.admin.merchant.onboarding.application.OnboardingSubmissionRepository;
import top.continew.admin.merchant.onboarding.application.OnboardingSubmissionState;
import top.continew.admin.merchant.onboarding.application.OnboardingWorkflowRequest;

import java.time.LocalDateTime;
import java.util.Optional;

/** Serializes final submission per application row and atomically creates the workflow outbox request. */
@Repository
@RequiredArgsConstructor
public class MyBatisOnboardingSubmissionRepository implements OnboardingSubmissionRepository {

    private static final String DRAFT = "DRAFT";
    private static final String SUBMITTED = "SUBMITTED";
    private static final String EVENT_TYPE = "MERCHANT_ONBOARDING_WORKFLOW_START_REQUESTED";

    private final OnboardingApplicationMapper applicationMapper;
    private final KycDraftVersionMapper kycVersionMapper;
    private final OutboxEventMapper outboxEventMapper;

    @Override
    public Optional<OnboardingSubmissionState> lock(Long tenantId, Long merchantId, Long applicationId) {
        OnboardingApplicationDO application = applicationMapper
            .selectOne(new LambdaQueryWrapper<OnboardingApplicationDO>()
                .eq(OnboardingApplicationDO::getTenantId, tenantId)
                .eq(OnboardingApplicationDO::getMerchantId, merchantId)
                .eq(OnboardingApplicationDO::getId, applicationId)
                .eq(OnboardingApplicationDO::getDeleted, 0L)
                .last("FOR UPDATE"));
        if (application == null) {
            return Optional.empty();
        }
        KycDraftVersionDO kyc = kycVersionMapper.selectOne(new LambdaQueryWrapper<KycDraftVersionDO>()
            .eq(KycDraftVersionDO::getTenantId, tenantId)
            .eq(KycDraftVersionDO::getMerchantId, merchantId)
            .eq(KycDraftVersionDO::getOnboardingApplicationId, applicationId)
            .eq(KycDraftVersionDO::getId, application.getKycVersionId())
            .eq(KycDraftVersionDO::getDeleted, 0L)
            .last("FOR UPDATE"));
        if (kyc == null) {
            throw new MerchantDomainException("Onboarding submission KYC version is not available");
        }
        return Optional.of(toState(application, kyc));
    }

    @Override
    public OnboardingWorkflowRequest submit(OnboardingSubmissionState state,
                                            Long actorUserId,
                                            String idempotencyKey,
                                            Long workflowEventId,
                                            String workflowEventKey,
                                            String workflowPayloadJson,
                                            String traceId,
                                            LocalDateTime submittedTime) {
        long submittedBusinessVersion = state.kycRowVersion() + 1;
        boolean kycUpdated = kycVersionMapper.lambdaUpdate()
            .eq(KycDraftVersionDO::getTenantId, state.tenantId())
            .eq(KycDraftVersionDO::getMerchantId, state.merchantId())
            .eq(KycDraftVersionDO::getOnboardingApplicationId, state.applicationId())
            .eq(KycDraftVersionDO::getId, state.kycVersionId())
            .eq(KycDraftVersionDO::getStatus, DRAFT)
            .eq(KycDraftVersionDO::getRowVersion, state.kycRowVersion())
            .eq(KycDraftVersionDO::getDeleted, 0L)
            .set(KycDraftVersionDO::getStatus, SUBMITTED)
            .set(KycDraftVersionDO::getFrozenTime, submittedTime)
            .set(KycDraftVersionDO::getRowVersion, submittedBusinessVersion)
            .set(KycDraftVersionDO::getUpdateUser, actorUserId)
            .set(KycDraftVersionDO::getUpdateTime, submittedTime)
            .update();
        if (!kycUpdated) {
            throw new OnboardingDraftConflictException();
        }
        boolean applicationUpdated = applicationMapper.lambdaUpdate()
            .eq(OnboardingApplicationDO::getTenantId, state.tenantId())
            .eq(OnboardingApplicationDO::getMerchantId, state.merchantId())
            .eq(OnboardingApplicationDO::getId, state.applicationId())
            .eq(OnboardingApplicationDO::getStatus, DRAFT)
            .eq(OnboardingApplicationDO::getRowVersion, state.applicationRowVersion())
            .eq(OnboardingApplicationDO::getDeleted, 0L)
            .set(OnboardingApplicationDO::getStatus, SUBMITTED)
            .set(OnboardingApplicationDO::getIdempotencyKey, idempotencyKey)
            .set(OnboardingApplicationDO::getActiveDraftGuard, null)
            .set(OnboardingApplicationDO::getSubmittedBy, actorUserId)
            .set(OnboardingApplicationDO::getSubmittedTime, submittedTime)
            .set(OnboardingApplicationDO::getRowVersion, state.applicationRowVersion() + 1)
            .set(OnboardingApplicationDO::getUpdateUser, actorUserId)
            .set(OnboardingApplicationDO::getUpdateTime, submittedTime)
            .update();
        if (!applicationUpdated) {
            throw new OnboardingDraftConflictException();
        }
        OutboxEventDO event = new OutboxEventDO();
        event.setId(workflowEventId);
        event.setTenantId(state.tenantId());
        event.setAggregateType("ONBOARDING_APPLICATION");
        event.setAggregateId(state.applicationId());
        event.setAggregateVersion(submittedBusinessVersion);
        event.setEventType(EVENT_TYPE);
        event.setEventKey(workflowEventKey);
        event.setPayloadJson(workflowPayloadJson);
        event.setStatus("PENDING");
        event.setRetryCount(0);
        event.setOccurredTime(submittedTime);
        event.setTraceId(traceId);
        event.setCreateTime(submittedTime);
        try {
            if (outboxEventMapper.insert(event) != 1) {
                throw new MerchantDomainException("Onboarding workflow request persistence failed");
            }
        } catch (DataIntegrityViolationException ex) {
            throw new MerchantDomainException("Onboarding workflow request already exists");
        }
        return new OnboardingWorkflowRequest(event.getId(), event.getEventKey(), event.getStatus());
    }

    @Override
    public Optional<OnboardingWorkflowRequest> findWorkflowRequest(Long tenantId, String workflowEventKey) {
        return Optional.ofNullable(outboxEventMapper.lambdaQuery()
            .eq(OutboxEventDO::getTenantId, tenantId)
            .eq(OutboxEventDO::getEventKey, workflowEventKey)
            .last("FOR UPDATE")
            .one()).map(event -> new OnboardingWorkflowRequest(event.getId(), event.getEventKey(), event.getStatus()));
    }

    private OnboardingSubmissionState toState(OnboardingApplicationDO application, KycDraftVersionDO kyc) {
        return new OnboardingSubmissionState(application.getTenantId(), application.getId(), application
            .getApplicationNo(), application.getMerchantId(), application.getOwningAgentId(), application
                .getChannelCode(), application.getProductCode(), application.getChannelConfigVersion(), application
                    .getRequirementVersion(), application.getStatus(), application.getIdempotencyKey(), application
                        .getSubmittedBy(), application.getSubmittedTime(), application.getRowVersion(), kyc.getId(), kyc
                            .getVersionNo(), kyc.getStatus(), kyc.getRowVersion(), kyc.getFrozenTime());
    }
}
