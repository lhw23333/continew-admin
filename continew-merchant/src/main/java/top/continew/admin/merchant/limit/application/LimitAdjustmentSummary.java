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

package top.continew.admin.merchant.limit.application;

import top.continew.admin.merchant.limit.domain.LimitAdjustment;
import top.continew.admin.merchant.limit.domain.LimitApprovalStatus;
import top.continew.admin.merchant.limit.domain.LimitChannelStatus;
import top.continew.admin.merchant.limit.domain.LimitEffectiveStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Sanitized immutable/state summary for list and detail views. */
public record LimitAdjustmentSummary(Long id, String requestNo, Long merchantId, String channelCode,
                                     String platformCode, String currency, BigDecimal originalLimit,
                                     BigDecimal requestedLimit, BigDecimal normalizedLimit,
                                     BigDecimal effectiveLimit, String reason, String eligibilityVersion,
                                     String channelConfigVersion, String amountPolicyVersion,
                                     String processInstanceId, LimitApprovalStatus approvalStatus,
                                     LimitChannelStatus channelStatus, LimitEffectiveStatus effectiveStatus,
                                     Long applicantId, LocalDateTime applicationTime, LocalDateTime approvalTime,
                                     LocalDateTime effectiveTime, String opinion, String channelResultCode,
                                     String channelResultMessage, Long rowVersion, LocalDateTime createTime,
                                     LocalDateTime updateTime) {

    public static LimitAdjustmentSummary from(LimitAdjustment value) {
        return new LimitAdjustmentSummary(value.id(), value.requestNo(), value.merchantId(), value.channelCode(), value
            .platformCode(), value.currency(), value.originalLimit(), value.requestedLimit(), value
                .normalizedLimit(), value.effectiveLimit(), value.reason(), value.eligibilityVersion(), value
                    .channelConfigVersion(), value.amountPolicyVersion(), value.processInstanceId(), value
                        .approvalStatus(), value.channelStatus(), value.effectiveStatus(), value.applicantId(), value
                            .applicationTime(), value.approvalTime(), value.effectiveTime(), value.opinion(), value
                                .channelResultCode(), value.channelResultMessage(), value.rowVersion(), value
                                    .createTime(), value.updateTime());
    }
}