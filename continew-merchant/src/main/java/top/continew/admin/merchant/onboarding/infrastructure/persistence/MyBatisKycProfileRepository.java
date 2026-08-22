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
import top.continew.admin.merchant.onboarding.application.KycProfileEncryptedDraft;
import top.continew.admin.merchant.onboarding.application.KycProfileRepository;

import java.time.LocalDateTime;

/** MyBatis KYC profile repository restricted to mutable draft rows. */
@Repository
@RequiredArgsConstructor
public class MyBatisKycProfileRepository implements KycProfileRepository {

    private final KycDraftVersionMapper mapper;

    @Override
    public boolean update(Long tenantId,
                          Long merchantId,
                          Long applicationId,
                          Long kycVersionId,
                          KycProfileEncryptedDraft draft,
                          Long expectedVersion,
                          LocalDateTime updateTime) {
        return mapper.lambdaUpdate()
            .eq(KycDraftVersionDO::getTenantId, tenantId)
            .eq(KycDraftVersionDO::getMerchantId, merchantId)
            .eq(KycDraftVersionDO::getOnboardingApplicationId, applicationId)
            .eq(KycDraftVersionDO::getId, kycVersionId)
            .eq(KycDraftVersionDO::getStatus, "DRAFT")
            .eq(KycDraftVersionDO::getRowVersion, expectedVersion)
            .eq(KycDraftVersionDO::getDeleted, 0L)
            .set(KycDraftVersionDO::getLegalName, draft.legalName())
            .set(KycDraftVersionDO::getLegalIdentifierCiphertext, draft.legalIdentifier().ciphertext())
            .set(KycDraftVersionDO::getLegalIdentifierHash, draft.legalIdentifier().normalizedHash())
            .set(KycDraftVersionDO::getLegalIdentifierHashKeyVersion, draft.legalIdentifier().hashKeyVersion())
            .set(KycDraftVersionDO::getLegalIdentifierMasked, draft.legalIdentifier().maskedValue())
            .set(KycDraftVersionDO::getLegalIdentifierKeyVersion, draft.legalIdentifier().keyVersion())
            .set(KycDraftVersionDO::getLicenseIssueDate, draft.licenseIssueDate())
            .set(KycDraftVersionDO::getLicenseExpiryDate, draft.licenseExpiryDate())
            .set(KycDraftVersionDO::getBusinessScope, draft.businessScope())
            .set(KycDraftVersionDO::getAddressPayloadCiphertext, draft.addressPayloadCiphertext())
            .set(KycDraftVersionDO::getPersonPayloadCiphertext, draft.personPayloadCiphertext())
            .set(KycDraftVersionDO::getShareholderPayloadCiphertext, draft.shareholderPayloadCiphertext())
            .set(KycDraftVersionDO::getPayloadKeyVersion, draft.payloadKeyVersion())
            .set(KycDraftVersionDO::getRowVersion, expectedVersion + 1)
            .set(KycDraftVersionDO::getUpdateTime, updateTime)
            .update();
    }
}
