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

package top.continew.admin.merchant.review.infrastructure.persistence;

import org.springframework.stereotype.Repository;
import top.continew.admin.merchant.master.application.MerchantRepository;
import top.continew.admin.merchant.master.domain.Merchant;
import top.continew.admin.merchant.master.domain.MerchantDomainException;
import top.continew.admin.merchant.onboarding.infrastructure.persistence.OnboardingApplicationDO;
import top.continew.admin.merchant.onboarding.infrastructure.persistence.OnboardingApplicationMapper;
import top.continew.admin.merchant.review.application.OnboardingReviewContext;
import top.continew.admin.merchant.review.application.OnboardingReviewRepository;
import top.continew.admin.merchant.review.application.ReviewRecordDraft;

import java.time.LocalDateTime;
import java.util.Optional;

/** MyBatis persistence for application review state and immutable human review evidence. */
@Repository
public class MyBatisOnboardingReviewRepository implements OnboardingReviewRepository {

    private final OnboardingApplicationMapper applicationMapper;
    private final MerchantRepository merchantRepository;
    private final ReviewRecordMapper reviewRecordMapper;

    public MyBatisOnboardingReviewRepository(OnboardingApplicationMapper applicationMapper,
                                             MerchantRepository merchantRepository,
                                             ReviewRecordMapper reviewRecordMapper) {
        this.applicationMapper = applicationMapper;
        this.merchantRepository = merchantRepository;
        this.reviewRecordMapper = reviewRecordMapper;
    }

    @Override
    public Optional<OnboardingReviewContext> findContext(Long tenantId, Long applicationId) {
        OnboardingApplicationDO application = applicationMapper.lambdaQuery()
            .eq(OnboardingApplicationDO::getTenantId, tenantId)
            .eq(OnboardingApplicationDO::getId, applicationId)
            .eq(OnboardingApplicationDO::getDeleted, 0L)
            .one();
        if (application == null) {
            return Optional.empty();
        }
        Merchant merchant = merchantRepository.findById(tenantId, application.getMerchantId()).orElse(null);
        if (merchant == null) {
            return Optional.empty();
        }
        return Optional.of(new OnboardingReviewContext(application.getId(), application.getMerchantId(), application
            .getOwningAgentId(), application.getSubmittedBy(), application.getStatus(), application
                .getKycVersionId(), application.getRowVersion(), merchant.operatorUserId()));
    }

    @Override
    public boolean updateApplicationStatus(Long tenantId,
                                           Long applicationId,
                                           String expectedStatus,
                                           String targetStatus,
                                           Long expectedVersion,
                                           Long actorUserId,
                                           LocalDateTime updateTime) {
        return applicationMapper.lambdaUpdate()
            .eq(OnboardingApplicationDO::getTenantId, tenantId)
            .eq(OnboardingApplicationDO::getId, applicationId)
            .eq(OnboardingApplicationDO::getStatus, expectedStatus)
            .eq(OnboardingApplicationDO::getRowVersion, expectedVersion)
            .eq(OnboardingApplicationDO::getDeleted, 0L)
            .set(OnboardingApplicationDO::getStatus, targetStatus)
            .set(OnboardingApplicationDO::getRowVersion, expectedVersion + 1)
            .set(OnboardingApplicationDO::getUpdateUser, actorUserId)
            .set(OnboardingApplicationDO::getUpdateTime, updateTime)
            .update();
    }

    @Override
    public void insert(ReviewRecordDraft draft) {
        ReviewRecordDO row = new ReviewRecordDO();
        row.setId(draft.id());
        row.setTenantId(draft.tenantId());
        row.setBusinessType(draft.businessType());
        row.setBusinessId(draft.businessId());
        row.setBusinessVersion(draft.businessVersion());
        row.setProcessInstanceId(draft.processInstanceId());
        row.setTaskId(draft.taskId());
        row.setReviewType("HUMAN");
        row.setReviewerId(String.valueOf(draft.reviewerUserId()));
        row.setAction(draft.action());
        row.setOpinion(draft.opinion());
        row.setIssueCodesJson(draft.issueCodesJson());
        row.setDecisionPayloadJson(draft.decisionPayloadJson());
        row.setDecisionTime(draft.decisionTime());
        row.setCreateUser(draft.reviewerUserId());
        row.setCreateTime(draft.decisionTime());
        if (reviewRecordMapper.insert(row) != 1) {
            throw new MerchantDomainException("Review record persistence failed");
        }
    }
}
