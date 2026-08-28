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
import top.continew.admin.merchant.limit.application.LimitAdjustmentWorkflowOutboxPort;
import top.continew.admin.merchant.limit.application.LimitAdjustmentWorkflowRequestDraft;
import top.continew.admin.merchant.master.domain.MerchantDomainException;
import top.continew.admin.merchant.onboarding.infrastructure.persistence.OutboxEventDO;
import top.continew.admin.merchant.onboarding.infrastructure.persistence.OutboxEventMapper;

/** Writes the limit workflow request into the shared transactional outbox. */
@Repository
@RequiredArgsConstructor
public class MyBatisLimitAdjustmentWorkflowOutboxRepository implements LimitAdjustmentWorkflowOutboxPort {

    public static final String EVENT_TYPE = "MERCHANT_LIMIT_ADJUSTMENT_WORKFLOW_START_REQUESTED";

    private final OutboxEventMapper mapper;

    @Override
    public void enqueue(LimitAdjustmentWorkflowRequestDraft draft) {
        OutboxEventDO event = new OutboxEventDO();
        event.setId(draft.eventId());
        event.setTenantId(draft.tenantId());
        event.setAggregateType("LIMIT_ADJUSTMENT");
        event.setAggregateId(draft.requestId());
        event.setAggregateVersion(draft.businessVersion());
        event.setEventType(EVENT_TYPE);
        event.setEventKey(draft.eventKey());
        event.setPayloadJson(draft.payloadJson());
        event.setStatus("PENDING");
        event.setRetryCount(0);
        event.setOccurredTime(draft.occurredTime());
        event.setTraceId(draft.traceId());
        event.setCreateTime(draft.occurredTime());
        try {
            if (mapper.insert(event) != 1) {
                throw new MerchantDomainException("Limit adjustment workflow request persistence failed");
            }
        } catch (DataIntegrityViolationException ex) {
            throw new MerchantDomainException("Limit adjustment workflow request already exists");
        }
    }
}