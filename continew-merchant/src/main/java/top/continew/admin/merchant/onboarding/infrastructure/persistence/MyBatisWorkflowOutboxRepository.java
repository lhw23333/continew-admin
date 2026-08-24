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
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.merchant.onboarding.outbox.WorkflowOutboxEvent;
import top.continew.admin.merchant.onboarding.outbox.WorkflowOutboxRepository;
import top.continew.starter.extension.tenant.annotation.TenantIgnore;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Cross-database optimistic claiming and status transitions for workflow outbox events. */
@Repository
@RequiredArgsConstructor
@TenantIgnore
public class MyBatisWorkflowOutboxRepository implements WorkflowOutboxRepository {

    private static final String PENDING = "PENDING";
    private static final String RETRY = "RETRY";
    private static final String PROCESSING = "PROCESSING";
    private static final String PUBLISHED = "PUBLISHED";
    private static final String REPAIR_REQUIRED = "REPAIR_REQUIRED";

    private final OutboxEventMapper mapper;

    @Override
    @Transactional
    public List<WorkflowOutboxEvent> claimAvailable(Long tenantId,
                                                    String workerId,
                                                    LocalDateTime now,
                                                    LocalDateTime staleBefore,
                                                    int batchSize) {
        List<OutboxEventDO> candidates = new ArrayList<>(mapper.lambdaQuery()
            .eq(tenantId != null, OutboxEventDO::getTenantId, tenantId)
            .in(OutboxEventDO::getStatus, List.of(PENDING, RETRY))
            .and(wrapper -> wrapper.isNull(OutboxEventDO::getNextRetryTime)
                .or()
                .le(OutboxEventDO::getNextRetryTime, now))
            .orderByAsc(OutboxEventDO::getNextRetryTime)
            .orderByAsc(OutboxEventDO::getId)
            .last("LIMIT " + batchSize)
            .list());
        int remaining = batchSize - candidates.size();
        if (remaining > 0) {
            candidates.addAll(mapper.lambdaQuery()
                .eq(tenantId != null, OutboxEventDO::getTenantId, tenantId)
                .eq(OutboxEventDO::getStatus, PROCESSING)
                .and(wrapper -> wrapper.isNull(OutboxEventDO::getLockedTime)
                    .or()
                    .le(OutboxEventDO::getLockedTime, staleBefore))
                .orderByAsc(OutboxEventDO::getLockedTime)
                .orderByAsc(OutboxEventDO::getId)
                .last("LIMIT " + remaining)
                .list());
        }
        List<WorkflowOutboxEvent> claimed = new ArrayList<>(candidates.size());
        for (OutboxEventDO candidate : candidates) {
            if (tryClaim(candidate, workerId, now)) {
                candidate.setStatus(PROCESSING);
                candidate.setLockedBy(workerId);
                candidate.setLockedTime(now);
                claimed.add(toEvent(candidate));
            }
        }
        return List.copyOf(claimed);
    }

    @Override
    public boolean markPublished(Long eventId, String workerId, String resultHeadersJson, LocalDateTime publishedTime) {
        return mapper.lambdaUpdate()
            .eq(OutboxEventDO::getId, eventId)
            .eq(OutboxEventDO::getStatus, PROCESSING)
            .eq(OutboxEventDO::getLockedBy, workerId)
            .set(OutboxEventDO::getStatus, PUBLISHED)
            .set(OutboxEventDO::getHeadersJson, resultHeadersJson)
            .set(OutboxEventDO::getPublishedTime, publishedTime)
            .set(OutboxEventDO::getNextRetryTime, null)
            .set(OutboxEventDO::getLockedBy, null)
            .set(OutboxEventDO::getLockedTime, null)
            .set(OutboxEventDO::getLastErrorCategory, null)
            .set(OutboxEventDO::getLastErrorMessage, null)
            .set(OutboxEventDO::getUpdateTime, publishedTime)
            .update();
    }

    @Override
    public boolean markRetry(Long eventId,
                             String workerId,
                             int retryCount,
                             LocalDateTime nextRetryTime,
                             String errorCategory,
                             String safeErrorMessage,
                             LocalDateTime updateTime) {
        return mapper.lambdaUpdate()
            .eq(OutboxEventDO::getId, eventId)
            .eq(OutboxEventDO::getStatus, PROCESSING)
            .eq(OutboxEventDO::getLockedBy, workerId)
            .set(OutboxEventDO::getStatus, RETRY)
            .set(OutboxEventDO::getRetryCount, retryCount)
            .set(OutboxEventDO::getNextRetryTime, nextRetryTime)
            .set(OutboxEventDO::getLockedBy, null)
            .set(OutboxEventDO::getLockedTime, null)
            .set(OutboxEventDO::getLastErrorCategory, errorCategory)
            .set(OutboxEventDO::getLastErrorMessage, safeErrorMessage)
            .set(OutboxEventDO::getUpdateTime, updateTime)
            .update();
    }

    @Override
    public boolean markRepairRequired(Long eventId,
                                      String workerId,
                                      int retryCount,
                                      String errorCategory,
                                      String safeErrorMessage,
                                      LocalDateTime updateTime) {
        return mapper.lambdaUpdate()
            .eq(OutboxEventDO::getId, eventId)
            .eq(OutboxEventDO::getStatus, PROCESSING)
            .eq(OutboxEventDO::getLockedBy, workerId)
            .set(OutboxEventDO::getStatus, REPAIR_REQUIRED)
            .set(OutboxEventDO::getRetryCount, retryCount)
            .set(OutboxEventDO::getNextRetryTime, null)
            .set(OutboxEventDO::getLockedBy, null)
            .set(OutboxEventDO::getLockedTime, null)
            .set(OutboxEventDO::getLastErrorCategory, errorCategory)
            .set(OutboxEventDO::getLastErrorMessage, safeErrorMessage)
            .set(OutboxEventDO::getUpdateTime, updateTime)
            .update();
    }

    @Override
    public boolean requeueRepair(Long tenantId, Long eventId, LocalDateTime updateTime) {
        return mapper.lambdaUpdate()
            .eq(OutboxEventDO::getTenantId, tenantId)
            .eq(OutboxEventDO::getId, eventId)
            .eq(OutboxEventDO::getStatus, REPAIR_REQUIRED)
            .set(OutboxEventDO::getStatus, PENDING)
            .set(OutboxEventDO::getRetryCount, 0)
            .set(OutboxEventDO::getNextRetryTime, updateTime)
            .set(OutboxEventDO::getLockedBy, null)
            .set(OutboxEventDO::getLockedTime, null)
            .set(OutboxEventDO::getLastErrorCategory, null)
            .set(OutboxEventDO::getLastErrorMessage, null)
            .set(OutboxEventDO::getUpdateTime, updateTime)
            .update();
    }

    private boolean tryClaim(OutboxEventDO candidate, String workerId, LocalDateTime now) {
        var update = mapper.lambdaUpdate()
            .eq(OutboxEventDO::getId, candidate.getId())
            .eq(OutboxEventDO::getStatus, candidate.getStatus());
        if (PROCESSING.equals(candidate.getStatus())) {
            if (candidate.getLockedTime() == null) {
                update.isNull(OutboxEventDO::getLockedTime);
            } else {
                update.eq(OutboxEventDO::getLockedTime, candidate.getLockedTime());
            }
        }
        return update.set(OutboxEventDO::getStatus, PROCESSING)
            .set(OutboxEventDO::getLockedBy, workerId)
            .set(OutboxEventDO::getLockedTime, now)
            .set(OutboxEventDO::getUpdateTime, now)
            .update();
    }

    private WorkflowOutboxEvent toEvent(OutboxEventDO event) {
        return new WorkflowOutboxEvent(event.getId(), event.getTenantId(), event.getAggregateType(), event
            .getAggregateId(), event.getAggregateVersion(), event.getEventType(), event.getEventKey(), event
                .getPayloadJson(), event.getStatus(), event.getRetryCount(), event.getLockedBy(), event
                    .getLockedTime(), event.getTraceId());
    }
}
