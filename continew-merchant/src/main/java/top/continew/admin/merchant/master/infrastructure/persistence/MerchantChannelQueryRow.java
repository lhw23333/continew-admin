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

package top.continew.admin.merchant.master.infrastructure.persistence;

import lombok.Data;
import top.continew.admin.merchant.agent.domain.AgentPricingStatus;

import java.time.LocalDateTime;

/** Flat latest-channel row including the exact KYC-referenced immutable pricing version. */
@Data
public class MerchantChannelQueryRow {
    private Long merchantId;
    private Long applicationId;
    private String applicationNo;
    private String channelCode;
    private String requirementVersion;
    private String channelConfigVersion;
    private Long kycVersionId;
    private String applicationStatus;
    private String reportingStatus;
    private String agreementStatus;
    private String cardBindingStatus;
    private String reserveAccountStatus;
    private String channelFinalStatus;
    private String rawChannelStatus;
    private LocalDateTime submittedTime;
    private LocalDateTime completedTime;
    private LocalDateTime createTime;
    private Long pricingVersionId;
    private Long pricingAgentId;
    private Long parentPricingVersionId;
    private Integer pricingVersionNo;
    private String pricingChannelCode;
    private String pricingProductCode;
    private String pricingCurrency;
    private String pricingRulesJson;
    private LocalDateTime pricingEffectiveTime;
    private LocalDateTime pricingExpiresTime;
    private AgentPricingStatus pricingStatus;
}
