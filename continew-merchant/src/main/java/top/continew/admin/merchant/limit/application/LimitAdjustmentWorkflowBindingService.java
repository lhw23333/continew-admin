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

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.merchant.limit.domain.LimitAdjustment;
import top.continew.admin.merchant.master.domain.MerchantDomainException;

import java.time.LocalDateTime;

/** Idempotently binds a delivered Flowable instance to the authoritative limit request. */
@Service
@RequiredArgsConstructor
public class LimitAdjustmentWorkflowBindingService {

    private final LimitAdjustmentRepository repository;
    private final IdentifierGenerator identifierGenerator;

    @Transactional
    public LimitAdjustment bind(Long tenantId,
                                LimitAdjustmentWorkflowStartPayload payload,
                                String processInstanceId,
                                LocalDateTime boundTime) {
        LimitAdjustment existing = repository.findByRequestId(tenantId, payload.requestId())
            .orElseThrow(() -> new MerchantDomainException("Limit adjustment workflow request is unavailable"));
        if (!existing.merchantId().equals(payload.merchantId()) || !existing.owningAgentId()
            .equals(payload.owningAgentId()) || !existing.applicantId().equals(payload.applicantId())) {
            throw new MerchantDomainException("Limit adjustment workflow identifiers do not match");
        }
        if (processInstanceId.equals(existing.processInstanceId())) {
            return existing;
        }
        LimitAdjustment bound = repository.bindWorkflow(tenantId, payload.requestId(), payload
            .businessVersion() - 1, processInstanceId, payload.applicantId(), boundTime);
        repository.appendHistory(new LimitAdjustmentHistoryDraft(identifierGenerator.nextId(new Object())
            .longValue(), bound, "WORKFLOW_STARTED", payload.applicantId(), boundTime));
        return bound;
    }
}