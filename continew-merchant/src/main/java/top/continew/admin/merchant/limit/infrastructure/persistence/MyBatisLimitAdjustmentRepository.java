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

package top.continew.admin.merchant.limit.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import top.continew.admin.merchant.limit.application.LimitAdjustmentConflictException;
import top.continew.admin.merchant.limit.application.LimitAdjustmentDraft;
import top.continew.admin.merchant.limit.application.LimitAdjustmentHistory;
import top.continew.admin.merchant.limit.application.LimitAdjustmentHistoryDraft;
import top.continew.admin.merchant.limit.application.LimitAdjustmentRepository;
import top.continew.admin.merchant.limit.domain.LimitAdjustment;
import top.continew.admin.merchant.limit.domain.LimitApprovalStatus;
import top.continew.admin.merchant.limit.domain.LimitChannelStatus;
import top.continew.admin.merchant.limit.domain.LimitEffectiveStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** MyBatis limit-request persistence with database-enforced active-dimension uniqueness. */
@Repository
@RequiredArgsConstructor
public class MyBatisLimitAdjustmentRepository implements LimitAdjustmentRepository {

    private final LimitAdjustmentMapper requestMapper;
    private final LimitAdjustmentHistoryMapper historyMapper;

    @Override
    public Optional<LimitAdjustment> findActive(Long tenantId,
                                                Long merchantId,
                                                String channelCode,
                                                String platformCode) {
        return Optional.ofNullable(requestMapper.lambdaQuery()
            .eq(LimitAdjustmentDO::getTenantId, tenantId)
            .eq(LimitAdjustmentDO::getMerchantId, merchantId)
            .eq(LimitAdjustmentDO::getChannelCode, channelCode)
            .eq(LimitAdjustmentDO::getPlatformCode, platformCode)
            .eq(LimitAdjustmentDO::getActiveRequestGuard, "ACTIVE")
            .eq(LimitAdjustmentDO::getDeleted, 0L)
            .one()).map(this::toDomain);
    }

    @Override
    public Optional<LimitAdjustment> findById(Long tenantId, Long merchantId, Long requestId) {
        return Optional.ofNullable(requestMapper.lambdaQuery()
            .eq(LimitAdjustmentDO::getTenantId, tenantId)
            .eq(LimitAdjustmentDO::getMerchantId, merchantId)
            .eq(LimitAdjustmentDO::getId, requestId)
            .eq(LimitAdjustmentDO::getDeleted, 0L)
            .one()).map(this::toDomain);
    }

    @Override
    public Optional<BigDecimal> findCurrentEffectiveLimit(Long tenantId,
                                                          Long merchantId,
                                                          String channelCode,
                                                          String platformCode,
                                                          String currency) {
        LimitAdjustmentDO current = requestMapper.lambdaQuery()
            .select(LimitAdjustmentDO::getEffectiveLimit)
            .eq(LimitAdjustmentDO::getTenantId, tenantId)
            .eq(LimitAdjustmentDO::getMerchantId, merchantId)
            .eq(LimitAdjustmentDO::getChannelCode, channelCode)
            .eq(LimitAdjustmentDO::getPlatformCode, platformCode)
            .eq(LimitAdjustmentDO::getCurrency, currency)
            .eq(LimitAdjustmentDO::getEffectiveStatus, LimitEffectiveStatus.EFFECTIVE)
            .isNotNull(LimitAdjustmentDO::getEffectiveLimit)
            .eq(LimitAdjustmentDO::getDeleted, 0L)
            .orderByDesc(LimitAdjustmentDO::getEffectiveTime)
            .orderByDesc(LimitAdjustmentDO::getId)
            .last("LIMIT 1")
            .one();
        return Optional.ofNullable(current).map(LimitAdjustmentDO::getEffectiveLimit);
    }

    @Override
    public LimitAdjustment insert(LimitAdjustmentDraft draft) {
        LimitAdjustmentDO row = new LimitAdjustmentDO();
        row.setId(draft.id());
        row.setTenantId(draft.tenantId());
        row.setRequestNo(draft.requestNo());
        row.setMerchantId(draft.merchantId());
        row.setOwningAgentId(draft.owningAgentId());
        row.setChannelCode(draft.channelCode());
        row.setPlatformCode(draft.platformCode());
        row.setCurrency(draft.currency());
        row.setOriginalLimit(draft.originalLimit());
        row.setRequestedLimit(draft.requestedLimit());
        row.setNormalizedLimit(draft.normalizedLimit());
        row.setReason(draft.reason());
        row.setEligibilityVersion(draft.eligibilityVersion());
        row.setChannelConfigVersion(draft.channelConfigVersion());
        row.setApprovalStatus(LimitApprovalStatus.PENDING);
        row.setChannelStatus(LimitChannelStatus.NOT_SUBMITTED);
        row.setEffectiveStatus(LimitEffectiveStatus.NOT_EFFECTIVE);
        row.setActiveRequestGuard("ACTIVE");
        row.setApplicantId(draft.applicantId());
        row.setApplicationTime(draft.applicationTime());
        row.setRowVersion(0L);
        row.setCreateUser(draft.applicantId());
        row.setCreateTime(draft.applicationTime());
        row.setDeleted(0L);
        try {
            if (requestMapper.insert(row) != 1) {
                throw new IllegalStateException("Limit adjustment persistence failed");
            }
        } catch (DataIntegrityViolationException ex) {
            throw new LimitAdjustmentConflictException(ex);
        }
        return toDomain(row);
    }

    @Override
    public void appendHistory(LimitAdjustmentHistoryDraft draft) {
        LimitAdjustment request = draft.request();
        LimitAdjustmentHistoryDO row = new LimitAdjustmentHistoryDO();
        row.setId(draft.id());
        row.setTenantId(request.tenantId());
        row.setRequestId(request.id());
        row.setRequestVersion(request.rowVersion());
        row.setAction(draft.action());
        row.setApprovalStatus(request.approvalStatus());
        row.setChannelStatus(request.channelStatus());
        row.setEffectiveStatus(request.effectiveStatus());
        row.setOriginalLimit(request.originalLimit());
        row.setRequestedLimit(request.requestedLimit());
        row.setNormalizedLimit(request.normalizedLimit());
        row.setEffectiveLimit(request.effectiveLimit());
        row.setActorUserId(draft.actorUserId());
        row.setOpinion(request.opinion());
        row.setChannelResultCode(request.channelResultCode());
        row.setChannelResultMessage(request.channelResultMessage());
        row.setOccurredTime(draft.occurredTime());
        if (historyMapper.insert(row) != 1) {
            throw new IllegalStateException("Limit adjustment history persistence failed");
        }
    }

    @Override
    public List<LimitAdjustmentHistory> listHistory(Long tenantId, Long requestId) {
        return historyMapper.lambdaQuery()
            .eq(LimitAdjustmentHistoryDO::getTenantId, tenantId)
            .eq(LimitAdjustmentHistoryDO::getRequestId, requestId)
            .orderByAsc(LimitAdjustmentHistoryDO::getRequestVersion)
            .orderByAsc(LimitAdjustmentHistoryDO::getOccurredTime)
            .orderByAsc(LimitAdjustmentHistoryDO::getId)
            .list()
            .stream()
            .map(this::toHistory)
            .toList();
    }

    private LimitAdjustment toDomain(LimitAdjustmentDO row) {
        return new LimitAdjustment(row.getId(), row.getTenantId(), row.getRequestNo(), row.getMerchantId(), row
            .getOwningAgentId(), row.getChannelCode(), row.getPlatformCode(), row.getCurrency(), row
                .getOriginalLimit(), row.getRequestedLimit(), row.getNormalizedLimit(), row.getEffectiveLimit(), row
                    .getReason(), row.getEligibilityVersion(), row.getChannelConfigVersion(), row
                        .getProcessInstanceId(), row.getApprovalStatus(), row.getChannelStatus(), row
                            .getEffectiveStatus(), row.getActiveRequestGuard(), row.getApplicantId(), row
                                .getApplicationTime(), row.getApprovalTime(), row.getEffectiveTime(), row
                                    .getOpinion(), row.getChannelResultCode(), row.getChannelResultMessage(), row
                                        .getRowVersion(), row.getCreateTime(), row.getUpdateTime());
    }

    private LimitAdjustmentHistory toHistory(LimitAdjustmentHistoryDO row) {
        return new LimitAdjustmentHistory(row.getId(), row.getTenantId(), row.getRequestId(), row
            .getRequestVersion(), row.getAction(), row.getApprovalStatus(), row.getChannelStatus(), row
                .getEffectiveStatus(), row.getOriginalLimit(), row.getRequestedLimit(), row.getNormalizedLimit(), row
                    .getEffectiveLimit(), row.getActorUserId(), row.getOpinion(), row.getChannelResultCode(), row
                        .getChannelResultMessage(), row.getOccurredTime());
    }
}
