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

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import top.continew.admin.merchant.master.domain.MerchantDomainException;
import top.continew.admin.merchant.onboarding.application.OnboardingSupplementRepository;
import top.continew.admin.merchant.onboarding.application.SupplementKycSnapshot;

import java.time.LocalDateTime;
import java.util.Optional;

/** MyBatis linked supplement version persistence. */
@Repository
public class MyBatisOnboardingSupplementRepository implements OnboardingSupplementRepository {

    private final KycDraftVersionMapper kycMapper;
    private final OnboardingApplicationMapper applicationMapper;

    public MyBatisOnboardingSupplementRepository(KycDraftVersionMapper kycMapper,
                                                 OnboardingApplicationMapper applicationMapper) {
        this.kycMapper = kycMapper;
        this.applicationMapper = applicationMapper;
    }

    @Override
    public Optional<SupplementKycSnapshot> find(Long tenantId, Long merchantId, Long applicationId, Long kycVersionId) {
        return Optional.ofNullable(kycMapper.lambdaQuery()
            .eq(KycDraftVersionDO::getTenantId, tenantId)
            .eq(KycDraftVersionDO::getMerchantId, merchantId)
            .eq(KycDraftVersionDO::getOnboardingApplicationId, applicationId)
            .eq(KycDraftVersionDO::getId, kycVersionId)
            .eq(KycDraftVersionDO::getDeleted, 0L)
            .one()).map(this::snapshot);
    }

    @Override
    public Optional<Long> findByTask(Long tenantId, Long applicationId, String taskId) {
        KycDraftVersionDO row = kycMapper.lambdaQuery()
            .select(KycDraftVersionDO::getId)
            .eq(KycDraftVersionDO::getTenantId, tenantId)
            .eq(KycDraftVersionDO::getOnboardingApplicationId, applicationId)
            .eq(KycDraftVersionDO::getSupplementTaskId, taskId)
            .eq(KycDraftVersionDO::getDeleted, 0L)
            .one();
        return Optional.ofNullable(row).map(KycDraftVersionDO::getId);
    }

    @Override
    public void copyVersion(Long newId,
                            Integer newVersionNo,
                            String taskId,
                            Long actorUserId,
                            LocalDateTime createTime,
                            SupplementKycSnapshot source) {
        KycDraftVersionDO old = kycMapper.selectById(source.id());
        if (old == null || !"SUBMITTED".equals(old.getStatus())) {
            throw new MerchantDomainException("Submitted KYC source is unavailable");
        }
        KycDraftVersionDO row = new KycDraftVersionDO();
        row.setId(newId);
        row.setTenantId(old.getTenantId());
        row.setMerchantId(old.getMerchantId());
        row.setOnboardingApplicationId(old.getOnboardingApplicationId());
        row.setVersionNo(newVersionNo);
        row.setPreviousVersionId(old.getId());
        row.setSupplementTaskId(taskId);
        row.setSourceKycVersionId(old.getSourceKycVersionId());
        row.setReuseProvenanceJson(old.getReuseProvenanceJson());
        row.setRequirementVersion(old.getRequirementVersion());
        row.setStatus("DRAFT");
        row.setSavedStep(old.getSavedStep());
        row.setStepCompletionJson(old.getStepCompletionJson());
        copyBusiness(old, row);
        row.setFrozenTime(null);
        row.setRowVersion(0L);
        row.setCreateUser(actorUserId);
        row.setCreateTime(createTime);
        row.setDeleted(0L);
        try {
            if (kycMapper.insert(row) != 1) {
                throw new MerchantDomainException("Supplement KYC persistence failed");
            }
        } catch (DataIntegrityViolationException ex) {
            throw new MerchantDomainException("Supplement task already has a KYC version");
        }
    }

    @Override
    public boolean replaceApplicationKyc(Long tenantId,
                                         Long applicationId,
                                         Long sourceKycVersionId,
                                         Long newKycVersionId,
                                         Long expectedApplicationVersion,
                                         Long actorUserId,
                                         LocalDateTime updateTime) {
        return applicationMapper.lambdaUpdate()
            .eq(OnboardingApplicationDO::getTenantId, tenantId)
            .eq(OnboardingApplicationDO::getId, applicationId)
            .eq(OnboardingApplicationDO::getStatus, "SUPPLEMENT_REQUIRED")
            .eq(OnboardingApplicationDO::getKycVersionId, sourceKycVersionId)
            .eq(OnboardingApplicationDO::getRowVersion, expectedApplicationVersion)
            .eq(OnboardingApplicationDO::getDeleted, 0L)
            .set(OnboardingApplicationDO::getKycVersionId, newKycVersionId)
            .set(OnboardingApplicationDO::getRowVersion, expectedApplicationVersion + 1)
            .set(OnboardingApplicationDO::getUpdateUser, actorUserId)
            .set(OnboardingApplicationDO::getUpdateTime, updateTime)
            .update();
    }

    @Override
    public boolean freeze(Long tenantId,
                          Long applicationId,
                          Long kycVersionId,
                          Long expectedRowVersion,
                          Long actorUserId,
                          LocalDateTime frozenTime) {
        return kycMapper.lambdaUpdate()
            .eq(KycDraftVersionDO::getTenantId, tenantId)
            .eq(KycDraftVersionDO::getOnboardingApplicationId, applicationId)
            .eq(KycDraftVersionDO::getId, kycVersionId)
            .eq(KycDraftVersionDO::getStatus, "DRAFT")
            .eq(KycDraftVersionDO::getRowVersion, expectedRowVersion)
            .eq(KycDraftVersionDO::getDeleted, 0L)
            .set(KycDraftVersionDO::getStatus, "SUBMITTED")
            .set(KycDraftVersionDO::getFrozenTime, frozenTime)
            .set(KycDraftVersionDO::getRowVersion, expectedRowVersion + 1)
            .set(KycDraftVersionDO::getUpdateUser, actorUserId)
            .set(KycDraftVersionDO::getUpdateTime, frozenTime)
            .update();
    }

    private SupplementKycSnapshot snapshot(KycDraftVersionDO row) {
        return new SupplementKycSnapshot(row.getId(), row.getVersionNo(), row.getPreviousVersionId(), row
            .getSupplementTaskId(), row.getStatus(), row.getRowVersion(), row.getLegalName(), row
                .getLegalIdentifierMasked(), row.getLicenseIssueDate(), row.getLicenseExpiryDate(), row
                    .getBusinessScope(), row.getAddressPayloadCiphertext(), row.getPersonPayloadCiphertext(), row
                        .getShareholderPayloadCiphertext(), row.getPricingVersionId(), row
                            .getSettlementAccountMasked(), row.getSettlementMode() == null
                                ? null
                                : row.getSettlementMode().name(), row.getSettlementVerificationStatus() == null
                                    ? null
                                    : row.getSettlementVerificationStatus().name());
    }

    private void copyBusiness(KycDraftVersionDO source, KycDraftVersionDO target) {
        target.setLegalName(source.getLegalName());
        target.setLegalIdentifierCiphertext(source.getLegalIdentifierCiphertext());
        target.setLegalIdentifierHash(source.getLegalIdentifierHash());
        target.setLegalIdentifierHashKeyVersion(source.getLegalIdentifierHashKeyVersion());
        target.setLegalIdentifierMasked(source.getLegalIdentifierMasked());
        target.setLegalIdentifierKeyVersion(source.getLegalIdentifierKeyVersion());
        target.setLicenseIssueDate(source.getLicenseIssueDate());
        target.setLicenseExpiryDate(source.getLicenseExpiryDate());
        target.setBusinessScope(source.getBusinessScope());
        target.setAddressPayloadCiphertext(source.getAddressPayloadCiphertext());
        target.setPersonPayloadCiphertext(source.getPersonPayloadCiphertext());
        target.setShareholderPayloadCiphertext(source.getShareholderPayloadCiphertext());
        target.setPayloadKeyVersion(source.getPayloadKeyVersion());
        target.setPricingVersionId(source.getPricingVersionId());
        target.setSettlementAccountCiphertext(source.getSettlementAccountCiphertext());
        target.setSettlementAccountHash(source.getSettlementAccountHash());
        target.setSettlementHashKeyVersion(source.getSettlementHashKeyVersion());
        target.setSettlementAccountMasked(source.getSettlementAccountMasked());
        target.setSettlementKeyVersion(source.getSettlementKeyVersion());
        target.setSettlementMode(source.getSettlementMode());
        target.setSettlementVerificationStatus(source.getSettlementVerificationStatus());
        target.setSettlementVerificationReference(source.getSettlementVerificationReference());
        target.setSettlementVerifierVersion(source.getSettlementVerifierVersion());
        target.setSettlementVerifiedTime(source.getSettlementVerifiedTime());
        target.setSettlementPayloadCiphertext(source.getSettlementPayloadCiphertext());
        target.setSettlementPayloadKeyVersion(source.getSettlementPayloadKeyVersion());
    }
}
