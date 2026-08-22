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
import top.continew.admin.merchant.onboarding.application.OnboardingPricingRepository;

import java.time.LocalDateTime;

/** MyBatis exact pricing-version selection persistence for mutable KYC drafts. */
@Repository
@RequiredArgsConstructor
public class MyBatisOnboardingPricingRepository implements OnboardingPricingRepository {

    private final KycDraftVersionMapper mapper;

    @Override
    public boolean update(Long tenantId,
                          Long merchantId,
                          Long applicationId,
                          Long kycVersionId,
                          Long pricingVersionId,
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
            .set(KycDraftVersionDO::getPricingVersionId, pricingVersionId)
            .set(KycDraftVersionDO::getRowVersion, expectedVersion + 1)
            .set(KycDraftVersionDO::getUpdateTime, updateTime)
            .update();
    }
}
