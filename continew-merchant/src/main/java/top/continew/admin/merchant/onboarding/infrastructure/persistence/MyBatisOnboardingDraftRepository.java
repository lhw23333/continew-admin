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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import top.continew.admin.merchant.master.domain.MerchantDomainException;
import top.continew.admin.channel.dto.ChannelRequirementSummary;
import top.continew.admin.merchant.onboarding.application.OnboardingDraft;
import top.continew.admin.merchant.onboarding.application.OnboardingDraftConflictException;
import top.continew.admin.merchant.onboarding.application.OnboardingDraftDraft;
import top.continew.admin.merchant.onboarding.application.OnboardingDraftRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** MyBatis onboarding draft repository with tenant, merchant, and optimistic-version predicates. */
@Repository
@RequiredArgsConstructor
public class MyBatisOnboardingDraftRepository implements OnboardingDraftRepository {

    private static final TypeReference<List<Integer>> COMPLETED_STEPS = new TypeReference<>() {};

    private final OnboardingApplicationMapper applicationMapper;
    private final KycDraftVersionMapper kycVersionMapper;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<OnboardingDraft> findActive(Long tenantId,
                                                Long merchantId,
                                                String channelCode,
                                                String productCode) {
        OnboardingApplicationDO application = applicationMapper.lambdaQuery()
            .eq(OnboardingApplicationDO::getTenantId, tenantId)
            .eq(OnboardingApplicationDO::getMerchantId, merchantId)
            .eq(OnboardingApplicationDO::getChannelCode, channelCode)
            .eq(OnboardingApplicationDO::getProductCode, productCode)
            .eq(OnboardingApplicationDO::getStatus, "DRAFT")
            .eq(OnboardingApplicationDO::getActiveDraftGuard, "ACTIVE")
            .eq(OnboardingApplicationDO::getDeleted, 0L)
            .orderByDesc(OnboardingApplicationDO::getCreateTime)
            .orderByDesc(OnboardingApplicationDO::getId)
            .last("LIMIT 1")
            .one();
        return Optional.ofNullable(application).map(this::toDraft);
    }

    @Override
    public Optional<OnboardingDraft> findByApplicationId(Long tenantId, Long merchantId, Long applicationId) {
        OnboardingApplicationDO application = applicationMapper.lambdaQuery()
            .eq(OnboardingApplicationDO::getTenantId, tenantId)
            .eq(OnboardingApplicationDO::getMerchantId, merchantId)
            .eq(OnboardingApplicationDO::getId, applicationId)
            .eq(OnboardingApplicationDO::getStatus, "DRAFT")
            .eq(OnboardingApplicationDO::getDeleted, 0L)
            .one();
        return Optional.ofNullable(application).map(this::toDraft);
    }

    @Override
    public Optional<OnboardingDraft> findByKycVersionId(Long tenantId, Long kycVersionId) {
        OnboardingApplicationDO application = applicationMapper.lambdaQuery()
            .eq(OnboardingApplicationDO::getTenantId, tenantId)
            .eq(OnboardingApplicationDO::getKycVersionId, kycVersionId)
            .eq(OnboardingApplicationDO::getStatus, "DRAFT")
            .eq(OnboardingApplicationDO::getDeleted, 0L)
            .one();
        return Optional.ofNullable(application).map(this::toDraft);
    }

    @Override
    public int nextKycVersionNo(Long tenantId, Long merchantId) {
        KycDraftVersionDO latest = kycVersionMapper.lambdaQuery()
            .select(KycDraftVersionDO::getVersionNo)
            .eq(KycDraftVersionDO::getTenantId, tenantId)
            .eq(KycDraftVersionDO::getMerchantId, merchantId)
            .eq(KycDraftVersionDO::getDeleted, 0L)
            .orderByDesc(KycDraftVersionDO::getVersionNo)
            .orderByDesc(KycDraftVersionDO::getId)
            .last("LIMIT 1")
            .one();
        return latest == null ? 1 : latest.getVersionNo() + 1;
    }

    @Override
    public void insert(OnboardingDraftDraft draft) {
        OnboardingApplicationDO application = new OnboardingApplicationDO();
        application.setId(draft.applicationId());
        application.setTenantId(draft.tenantId());
        application.setApplicationNo(draft.applicationNo());
        application.setMerchantId(draft.merchantId());
        application.setOwningAgentId(draft.owningAgentId());
        application.setChannelCode(draft.channelCode());
        application.setProductCode(draft.productCode());
        application.setRequirementVersion(draft.requirementVersion());
        application.setRequirementSummaryJson(writeRequirementSummary(draft.requirementSummary()));
        application.setChannelConfigVersion(draft.channelConfigVersion());
        application.setKycVersionId(draft.kycVersionId());
        application.setStatus("DRAFT");
        application.setActiveDraftGuard("ACTIVE");
        application.setRowVersion(0L);
        application.setCreateUser(draft.createUser());
        application.setCreateTime(draft.createTime());
        application.setDeleted(0L);
        try {
            if (applicationMapper.insert(application) != 1) {
                throw new MerchantDomainException("Onboarding application draft persistence failed");
            }
        } catch (DataIntegrityViolationException ex) {
            throw new OnboardingDraftConflictException();
        }

        KycDraftVersionDO kyc = new KycDraftVersionDO();
        kyc.setId(draft.kycVersionId());
        kyc.setTenantId(draft.tenantId());
        kyc.setMerchantId(draft.merchantId());
        kyc.setOnboardingApplicationId(draft.applicationId());
        kyc.setVersionNo(draft.kycVersionNo());
        kyc.setRequirementVersion(draft.requirementVersion());
        kyc.setStatus("DRAFT");
        kyc.setSavedStep(1);
        kyc.setStepCompletionJson("[]");
        kyc.setLegalName(draft.legalName());
        kyc.setPricingVersionId(draft.pricingVersionId());
        kyc.setRowVersion(0L);
        kyc.setCreateUser(draft.createUser());
        kyc.setCreateTime(draft.createTime());
        kyc.setDeleted(0L);
        if (kycVersionMapper.insert(kyc) != 1) {
            throw new MerchantDomainException("KYC draft persistence failed");
        }
    }

    @Override
    public boolean updateProgress(Long tenantId,
                                  Long merchantId,
                                  Long applicationId,
                                  Long kycVersionId,
                                  Integer savedStep,
                                  List<Integer> completedSteps,
                                  Long expectedVersion,
                                  LocalDateTime updateTime) {
        return kycVersionMapper.lambdaUpdate()
            .eq(KycDraftVersionDO::getTenantId, tenantId)
            .eq(KycDraftVersionDO::getMerchantId, merchantId)
            .eq(KycDraftVersionDO::getOnboardingApplicationId, applicationId)
            .eq(KycDraftVersionDO::getId, kycVersionId)
            .eq(KycDraftVersionDO::getStatus, "DRAFT")
            .eq(KycDraftVersionDO::getRowVersion, expectedVersion)
            .eq(KycDraftVersionDO::getDeleted, 0L)
            .set(KycDraftVersionDO::getSavedStep, savedStep)
            .set(KycDraftVersionDO::getStepCompletionJson, writeCompletedSteps(completedSteps))
            .set(KycDraftVersionDO::getRowVersion, expectedVersion + 1)
            .set(KycDraftVersionDO::getUpdateTime, updateTime)
            .update();
    }

    private OnboardingDraft toDraft(OnboardingApplicationDO application) {
        KycDraftVersionDO kyc = kycVersionMapper.lambdaQuery()
            .eq(KycDraftVersionDO::getTenantId, application.getTenantId())
            .eq(KycDraftVersionDO::getMerchantId, application.getMerchantId())
            .eq(KycDraftVersionDO::getOnboardingApplicationId, application.getId())
            .eq(KycDraftVersionDO::getId, application.getKycVersionId())
            .eq(KycDraftVersionDO::getStatus, "DRAFT")
            .eq(KycDraftVersionDO::getDeleted, 0L)
            .one();
        if (kyc == null) {
            throw new MerchantDomainException("Onboarding draft KYC version is not available");
        }
        return new OnboardingDraft(application.getId(), application.getApplicationNo(), application
            .getMerchantId(), application.getOwningAgentId(), application.getChannelCode(), application
                .getProductCode(), application.getChannelConfigVersion(), application
                    .getRequirementVersion(), readRequirementSummary(application.getRequirementSummaryJson()), kyc
                        .getId(), kyc.getVersionNo(), kyc.getPricingVersionId(), kyc
                            .getSavedStep(), readCompletedSteps(kyc.getStepCompletionJson()), kyc
                                .getRowVersion(), application.getCreateTime(), kyc.getUpdateTime());
    }

    private String writeRequirementSummary(ChannelRequirementSummary requirements) {
        try {
            return objectMapper.writeValueAsString(requirements);
        } catch (JsonProcessingException ex) {
            throw new MerchantDomainException("Onboarding requirement summary is invalid");
        }
    }

    private ChannelRequirementSummary readRequirementSummary(String json) {
        try {
            return objectMapper.readValue(json, ChannelRequirementSummary.class);
        } catch (JsonProcessingException ex) {
            throw new MerchantDomainException("Stored onboarding requirement summary is invalid");
        }
    }

    private String writeCompletedSteps(List<Integer> completedSteps) {
        try {
            return objectMapper.writeValueAsString(completedSteps);
        } catch (JsonProcessingException ex) {
            throw new MerchantDomainException("Onboarding draft step state is invalid");
        }
    }

    private List<Integer> readCompletedSteps(String json) {
        try {
            return objectMapper.readValue(json, COMPLETED_STEPS);
        } catch (JsonProcessingException ex) {
            throw new MerchantDomainException("Stored onboarding draft step state is invalid");
        }
    }
}
