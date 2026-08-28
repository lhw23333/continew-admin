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
import java.util.List;
import java.util.Optional;

/** Tenant-explicit request, state transition, and append-only history persistence boundary. */
public interface LimitAdjustmentRepository {

    Optional<LimitAdjustment> findActive(Long tenantId, Long merchantId, String channelCode, String platformCode);

    Optional<LimitAdjustment> findById(Long tenantId, Long merchantId, Long requestId);

    Optional<LimitAdjustment> findByRequestId(Long tenantId, Long requestId);

    default LimitAdjustmentPageSlice page(Long tenantId, Long merchantId, LimitAdjustmentListQuery query) {
        throw new UnsupportedOperationException();
    }

    Optional<BigDecimal> findCurrentEffectiveLimit(Long tenantId,
                                                   Long merchantId,
                                                   String channelCode,
                                                   String platformCode,
                                                   String currency);

    LimitAdjustment insert(LimitAdjustmentDraft draft);

    LimitAdjustment bindWorkflow(Long tenantId,
                                 Long requestId,
                                 Long expectedVersion,
                                 String processInstanceId,
                                 Long actorUserId,
                                 LocalDateTime updateTime);

    LimitAdjustment applyReviewDecision(Long tenantId,
                                        Long requestId,
                                        Long expectedVersion,
                                        LimitApprovalStatus approvalStatus,
                                        String opinion,
                                        Long actorUserId,
                                        LocalDateTime approvalTime);

    LimitAdjustment applyChannelResult(Long tenantId,
                                       Long requestId,
                                       Long expectedVersion,
                                       LimitChannelStatus channelStatus,
                                       LimitEffectiveStatus effectiveStatus,
                                       BigDecimal effectiveLimit,
                                       LocalDateTime effectiveTime,
                                       String channelResultCode,
                                       String channelResultMessage,
                                       Long actorUserId,
                                       LocalDateTime updateTime);

    void appendHistory(LimitAdjustmentHistoryDraft draft);

    List<LimitAdjustmentHistory> listHistory(Long tenantId, Long requestId);
}