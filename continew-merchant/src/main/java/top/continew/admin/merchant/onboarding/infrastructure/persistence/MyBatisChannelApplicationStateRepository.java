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
import org.springframework.stereotype.Repository;
import top.continew.admin.channel.api.ChannelApplicationStatePort;
import top.continew.admin.channel.dto.ChannelApplicationState;
import top.continew.admin.channel.dto.ChannelOnboardingState;
import top.continew.admin.channel.dto.ChannelProductKey;
import top.continew.admin.channel.dto.ChannelStageStatus;
import top.continew.admin.channel.dto.ChannelStateMergeResult;
import top.continew.admin.channel.dto.ChannelStateRanks;

import java.time.LocalDateTime;
import java.util.Optional;

/** Merchant-owned locked state adapter for atomic channel event application. */
@Repository
@RequiredArgsConstructor
public class MyBatisChannelApplicationStateRepository implements ChannelApplicationStatePort {
    private final OnboardingApplicationMapper applicationMapper;
    private final KycDraftVersionMapper kycVersionMapper;

    @Override
    public Optional<ChannelApplicationState> lock(Long tenantId,
                                                  Long applicationId,
                                                  ChannelProductKey product,
                                                  String configVersion) {
        OnboardingApplicationDO application = applicationMapper
            .selectOne(new LambdaQueryWrapper<OnboardingApplicationDO>()
                .eq(OnboardingApplicationDO::getTenantId, tenantId)
                .eq(OnboardingApplicationDO::getId, applicationId)
                .eq(OnboardingApplicationDO::getChannelCode, product.channelCode())
                .eq(OnboardingApplicationDO::getProductCode, product.productCode())
                .eq(OnboardingApplicationDO::getChannelConfigVersion, configVersion)
                .in(OnboardingApplicationDO::getStatus, "SUBMITTED", "APPROVED", "CHANNEL_PROCESSING")
                .eq(OnboardingApplicationDO::getDeleted, 0L)
                .last("FOR UPDATE"));
        if (application == null) {
            return Optional.empty();
        }
        KycDraftVersionDO kyc = kycVersionMapper.selectOne(new LambdaQueryWrapper<KycDraftVersionDO>()
            .select(KycDraftVersionDO::getId, KycDraftVersionDO::getRowVersion)
            .eq(KycDraftVersionDO::getTenantId, tenantId)
            .eq(KycDraftVersionDO::getOnboardingApplicationId, applicationId)
            .eq(KycDraftVersionDO::getId, application.getKycVersionId())
            .eq(KycDraftVersionDO::getDeleted, 0L)
            .last("FOR UPDATE"));
        if (kyc == null || kyc.getRowVersion() == null || kyc.getRowVersion() <= 0) {
            return Optional.empty();
        }
        ChannelOnboardingState state = new ChannelOnboardingState(status(application
            .getReportingStatus()), status(application.getAgreementStatus()), status(application
                .getCardBindingStatus()), status(application.getReserveAccountStatus()), status(application
                    .getChannelFinalStatus()));
        ChannelStateRanks ranks = new ChannelStateRanks(rank(application.getReportingRank()), rank(application
            .getAgreementRank()), rank(application.getCardBindingRank()), rank(application
                .getReserveAccountRank()), rank(application.getChannelFinalRank()));
        return Optional.of(new ChannelApplicationState(tenantId, applicationId, application.getMerchantId(), kyc
            .getRowVersion(), product, configVersion, application.getChannelBusinessSerial(), state, ranks, Boolean.TRUE
                .equals(application.getChannelFinalTerminal()), application.getRowVersion()));
    }

    @Override
    public boolean apply(ChannelApplicationState current,
                         ChannelStateMergeResult merged,
                         String businessSerial,
                         String rawStatusCode,
                         LocalDateTime updateTime) {
        String applicationStatus = applicationStatus(merged);
        return applicationMapper.lambdaUpdate()
            .eq(OnboardingApplicationDO::getTenantId, current.tenantId())
            .eq(OnboardingApplicationDO::getId, current.applicationId())
            .eq(OnboardingApplicationDO::getMerchantId, current.merchantId())
            .eq(OnboardingApplicationDO::getChannelCode, current.product().channelCode())
            .eq(OnboardingApplicationDO::getProductCode, current.product().productCode())
            .eq(OnboardingApplicationDO::getChannelConfigVersion, current.configVersion())
            .eq(OnboardingApplicationDO::getRowVersion, current.rowVersion())
            .eq(OnboardingApplicationDO::getDeleted, 0L)
            .set(OnboardingApplicationDO::getChannelBusinessSerial, businessSerial)
            .set(OnboardingApplicationDO::getReportingStatus, merged.state().reportingStatus())
            .set(OnboardingApplicationDO::getAgreementStatus, merged.state().signingStatus())
            .set(OnboardingApplicationDO::getCardBindingStatus, merged.state().cardBindingStatus())
            .set(OnboardingApplicationDO::getReserveAccountStatus, merged.state().reserveAccountStatus())
            .set(OnboardingApplicationDO::getChannelFinalStatus, merged.state().finalStatus())
            .set(OnboardingApplicationDO::getReportingRank, merged.ranks().reportingRank())
            .set(OnboardingApplicationDO::getAgreementRank, merged.ranks().signingRank())
            .set(OnboardingApplicationDO::getCardBindingRank, merged.ranks().cardBindingRank())
            .set(OnboardingApplicationDO::getReserveAccountRank, merged.ranks().reserveAccountRank())
            .set(OnboardingApplicationDO::getChannelFinalRank, merged.ranks().finalRank())
            .set(OnboardingApplicationDO::getChannelFinalTerminal, merged.finalTerminal())
            .set(merged.changed(), OnboardingApplicationDO::getRawChannelStatus, rawStatusCode)
            .set(OnboardingApplicationDO::getStatus, applicationStatus)
            .set(merged.finalTerminal(), OnboardingApplicationDO::getCompletedTime, updateTime)
            .set(OnboardingApplicationDO::getRowVersion, current.rowVersion() + 1)
            .set(OnboardingApplicationDO::getUpdateTime, updateTime)
            .update();
    }

    private String applicationStatus(ChannelStateMergeResult merged) {
        if (!merged.finalTerminal()) {
            return "CHANNEL_PROCESSING";
        }
        return ChannelStageStatus.SUCCEEDED.equals(merged.state().finalStatus()) ? "SUCCEEDED" : "FAILED";
    }

    private ChannelStageStatus status(ChannelStageStatus value) {
        return value == null ? ChannelStageStatus.NOT_STARTED : value;
    }

    private Integer rank(Integer value) {
        return value == null ? 0 : value;
    }
}
