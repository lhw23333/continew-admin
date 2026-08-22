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
import top.continew.admin.merchant.onboarding.application.OnboardingPreviewRepository;
import top.continew.admin.merchant.onboarding.application.OnboardingPreviewSnapshot;

import java.util.Optional;

/** Reads only saved masks, version references, and completeness metadata for ordinary final preview. */
@Repository
@RequiredArgsConstructor
public class MyBatisOnboardingPreviewRepository implements OnboardingPreviewRepository {

    private final KycDraftVersionMapper mapper;

    @Override
    public Optional<OnboardingPreviewSnapshot> findSavedKyc(Long tenantId,
                                                            Long merchantId,
                                                            Long applicationId,
                                                            Long kycVersionId) {
        KycDraftVersionDO row = mapper.lambdaQuery()
            .eq(KycDraftVersionDO::getTenantId, tenantId)
            .eq(KycDraftVersionDO::getMerchantId, merchantId)
            .eq(KycDraftVersionDO::getOnboardingApplicationId, applicationId)
            .eq(KycDraftVersionDO::getId, kycVersionId)
            .eq(KycDraftVersionDO::getStatus, "DRAFT")
            .eq(KycDraftVersionDO::getDeleted, 0L)
            .one();
        return Optional.ofNullable(row).map(this::toSnapshot);
    }

    private OnboardingPreviewSnapshot toSnapshot(KycDraftVersionDO row) {
        return new OnboardingPreviewSnapshot(row.getId(), row.getVersionNo(), row.getRowVersion(), row.getStatus(), row
            .getLegalName(), row.getLegalIdentifierMasked(), row.getLicenseIssueDate(), row.getLicenseExpiryDate(), row
                .getBusinessScope(), present(row.getAddressPayloadCiphertext()), present(row
                    .getPersonPayloadCiphertext()), present(row.getShareholderPayloadCiphertext()), row
                        .getPricingVersionId(), row.getSettlementMode(), row.getSettlementAccountMasked(), row
                            .getSettlementVerificationStatus(), row.getSettlementVerificationReference(), row
                                .getSettlementVerifierVersion(), row.getSettlementVerifiedTime(), present(row
                                    .getSettlementPayloadCiphertext()));
    }

    private boolean present(byte[] value) {
        return value != null && value.length > 0;
    }
}
