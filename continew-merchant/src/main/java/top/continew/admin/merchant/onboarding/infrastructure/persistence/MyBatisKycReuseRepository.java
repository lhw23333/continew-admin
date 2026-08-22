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

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import top.continew.admin.merchant.onboarding.application.KycReusableSnapshot;
import top.continew.admin.merchant.onboarding.application.KycReuseField;
import top.continew.admin.merchant.onboarding.application.KycReuseRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** MyBatis same-merchant KYC reuse repository; ciphertext never leaves the application service boundary. */
@Repository
@RequiredArgsConstructor
public class MyBatisKycReuseRepository implements KycReuseRepository {

    private final KycDraftVersionMapper kycVersionMapper;
    private final OnboardingApplicationMapper applicationMapper;

    @Override
    public List<KycReusableSnapshot> listSources(Long tenantId, Long merchantId, Long targetKycVersionId) {
        return kycVersionMapper.lambdaQuery()
            .eq(KycDraftVersionDO::getTenantId, tenantId)
            .eq(KycDraftVersionDO::getMerchantId, merchantId)
            .ne(KycDraftVersionDO::getId, targetKycVersionId)
            .ne(KycDraftVersionDO::getStatus, "DRAFT")
            .isNotNull(KycDraftVersionDO::getFrozenTime)
            .eq(KycDraftVersionDO::getDeleted, 0L)
            .orderByDesc(KycDraftVersionDO::getFrozenTime)
            .orderByDesc(KycDraftVersionDO::getVersionNo)
            .orderByDesc(KycDraftVersionDO::getId)
            .list()
            .stream()
            .map(this::toSnapshot)
            .flatMap(Optional::stream)
            .toList();
    }

    @Override
    public Optional<KycReusableSnapshot> findSource(Long tenantId, Long merchantId, Long sourceKycVersionId) {
        KycDraftVersionDO source = kycVersionMapper.lambdaQuery()
            .eq(KycDraftVersionDO::getTenantId, tenantId)
            .eq(KycDraftVersionDO::getMerchantId, merchantId)
            .eq(KycDraftVersionDO::getId, sourceKycVersionId)
            .ne(KycDraftVersionDO::getStatus, "DRAFT")
            .isNotNull(KycDraftVersionDO::getFrozenTime)
            .eq(KycDraftVersionDO::getDeleted, 0L)
            .one();
        return source == null ? Optional.empty() : toSnapshot(source);
    }

    @Override
    public boolean apply(Long tenantId,
                         Long merchantId,
                         Long targetApplicationId,
                         Long targetKycVersionId,
                         KycReusableSnapshot source,
                         Set<KycReuseField> fields,
                         String provenanceJson,
                         Long expectedVersion,
                         LocalDateTime updateTime) {
        var update = kycVersionMapper.lambdaUpdate()
            .eq(KycDraftVersionDO::getTenantId, tenantId)
            .eq(KycDraftVersionDO::getMerchantId, merchantId)
            .eq(KycDraftVersionDO::getOnboardingApplicationId, targetApplicationId)
            .eq(KycDraftVersionDO::getId, targetKycVersionId)
            .eq(KycDraftVersionDO::getStatus, "DRAFT")
            .eq(KycDraftVersionDO::getRowVersion, expectedVersion)
            .eq(KycDraftVersionDO::getDeleted, 0L)
            .set(KycDraftVersionDO::getSourceKycVersionId, source.id())
            .set(KycDraftVersionDO::getReuseProvenanceJson, provenanceJson)
            .set(KycDraftVersionDO::getRowVersion, expectedVersion + 1)
            .set(KycDraftVersionDO::getUpdateTime, updateTime);
        if (fields.contains(KycReuseField.LEGAL_NAME)) {
            update.set(KycDraftVersionDO::getLegalName, source.legalName());
        }
        if (fields.contains(KycReuseField.LEGAL_IDENTIFIER)) {
            update.set(KycDraftVersionDO::getLegalIdentifierCiphertext, source.legalIdentifierCiphertext())
                .set(KycDraftVersionDO::getLegalIdentifierHash, source.legalIdentifierHash())
                .set(KycDraftVersionDO::getLegalIdentifierHashKeyVersion, source.legalIdentifierHashKeyVersion())
                .set(KycDraftVersionDO::getLegalIdentifierMasked, source.legalIdentifierMasked())
                .set(KycDraftVersionDO::getLegalIdentifierKeyVersion, source.legalIdentifierKeyVersion());
        }
        if (fields.contains(KycReuseField.LICENSE_DATES)) {
            update.set(KycDraftVersionDO::getLicenseIssueDate, source.licenseIssueDate())
                .set(KycDraftVersionDO::getLicenseExpiryDate, source.licenseExpiryDate());
        }
        if (fields.contains(KycReuseField.BUSINESS_SCOPE)) {
            update.set(KycDraftVersionDO::getBusinessScope, source.businessScope());
        }
        return update.update();
    }

    private Optional<KycReusableSnapshot> toSnapshot(KycDraftVersionDO source) {
        if (source.getOnboardingApplicationId() == null) {
            return Optional.empty();
        }
        OnboardingApplicationDO application = applicationMapper.lambdaQuery()
            .select(OnboardingApplicationDO::getChannelCode)
            .eq(OnboardingApplicationDO::getTenantId, source.getTenantId())
            .eq(OnboardingApplicationDO::getMerchantId, source.getMerchantId())
            .eq(OnboardingApplicationDO::getId, source.getOnboardingApplicationId())
            .eq(OnboardingApplicationDO::getDeleted, 0L)
            .one();
        if (application == null) {
            return Optional.empty();
        }
        return Optional.of(new KycReusableSnapshot(source.getId(), source.getMerchantId(), source
            .getOnboardingApplicationId(), source.getVersionNo(), application.getChannelCode(), source
                .getRequirementVersion(), source.getStatus(), source.getLegalName(), source
                    .getLegalIdentifierCiphertext(), source.getLegalIdentifierHash(), source
                        .getLegalIdentifierHashKeyVersion(), source.getLegalIdentifierMasked(), source
                            .getLegalIdentifierKeyVersion(), source.getLicenseIssueDate(), source
                                .getLicenseExpiryDate(), source.getBusinessScope(), source.getFrozenTime(), source
                                    .getUpdateTime(), source.getCreateTime()));
    }
}
