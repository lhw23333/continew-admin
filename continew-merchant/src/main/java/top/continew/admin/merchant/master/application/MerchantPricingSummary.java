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

package top.continew.admin.merchant.master.application;

import top.continew.admin.merchant.agent.domain.AgentPricingRules;
import top.continew.admin.merchant.agent.domain.AgentPricingStatus;

import java.time.LocalDateTime;

/** Exact immutable pricing version referenced by one channel application's KYC version. */
public record MerchantPricingSummary(Long pricingVersionId, Long agentId, Long parentPricingVersionId,
                                     Integer versionNo, String channelCode, String productCode, String currency,
                                     AgentPricingRules rules, LocalDateTime effectiveTime, LocalDateTime expiresTime,
                                     AgentPricingStatus status) {
}
