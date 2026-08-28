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

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.merchant.limit.domain.LimitAdjustment;
import top.continew.admin.merchant.master.application.MerchantScopeAuthorizationService;
import top.continew.admin.merchant.master.domain.MerchantAccessDeniedException;
import top.continew.admin.merchant.master.domain.MerchantDomainException;
import top.continew.admin.workflow.api.WorkflowMappingService;
import top.continew.admin.workflow.api.WorkflowService;
import top.continew.admin.workflow.definition.MerchantLimitAdjustmentWorkflowDefinition;
import top.continew.admin.workflow.dto.WorkflowInstanceMapping;
import top.continew.admin.workflow.dto.WorkflowProcessHistory;
import top.continew.admin.workflow.dto.WorkflowTask;
import top.continew.admin.workflow.query.WorkflowTaskQuery;
import top.continew.starter.extension.tenant.context.TenantContextHolder;

import java.util.List;

/** Scope-aware list/detail/history projection for limit requests. */
@Service
@RequiredArgsConstructor
public class LimitAdjustmentQueryService {

    private final LimitAdjustmentRepository repository;
    private final MerchantScopeAuthorizationService merchantScopeAuthorizationService;
    private final WorkflowMappingService mappingService;
    private final WorkflowService workflowService;

    @Transactional(readOnly = true)
    public LimitAdjustmentPage page(Long tenantId,
                                    Long actorUserId,
                                    Long merchantId,
                                    LimitAdjustmentListQuery query) {
        requireTenant(tenantId);
        merchantScopeAuthorizationService.requireAccessible(tenantId, actorUserId, merchantId);
        LimitAdjustmentPageSlice slice = repository.page(tenantId, merchantId, query);
        return new LimitAdjustmentPage(slice.list()
            .stream()
            .map(LimitAdjustmentSummary::from)
            .toList(), slice.total(), query.page(), query.size());
    }

    @Transactional(readOnly = true)
    public LimitAdjustmentDetail get(Long tenantId, Long actorUserId, Long merchantId, Long requestId) {
        requireTenant(tenantId);
        merchantScopeAuthorizationService.requireAccessible(tenantId, actorUserId, merchantId);
        LimitAdjustment request = repository.findById(tenantId, merchantId, requestId)
            .orElseThrow(MerchantAccessDeniedException::new);
        List<LimitAdjustmentHistory> history = repository.listHistory(tenantId, requestId);
        WorkflowContext workflow = workflowContext(tenantId, actorUserId, request);
        return new LimitAdjustmentDetail(LimitAdjustmentSummary.from(request), history, workflow
            .businessVersion(), workflow.task());
    }

    @Transactional(readOnly = true)
    public List<LimitAdjustmentHistory> history(Long tenantId,
                                                Long actorUserId,
                                                Long merchantId,
                                                Long requestId) {
        return get(tenantId, actorUserId, merchantId, requestId).history();
    }

    @Transactional(readOnly = true)
    public WorkflowProcessHistory workflowHistory(Long tenantId,
                                                  Long actorUserId,
                                                  Long merchantId,
                                                  Long requestId) {
        LimitAdjustmentDetail detail = get(tenantId, actorUserId, merchantId, requestId);
        String processInstanceId = detail.request().processInstanceId();
        if (processInstanceId == null) {
            throw new MerchantDomainException("Limit workflow has not started");
        }
        return workflowService.history(tenantId, actorUserId, processInstanceId);
    }

    private WorkflowContext workflowContext(Long tenantId, Long actorUserId, LimitAdjustment request) {
        if (request.processInstanceId() == null) {
            return new WorkflowContext(null, null);
        }
        WorkflowInstanceMapping mapping = mappingService.findByProcessInstanceId(tenantId, request.processInstanceId())
            .orElse(null);
        if (mapping == null || !"MERCHANT_LIMIT_ADJUSTMENT".equals(mapping.businessType())) {
            return new WorkflowContext(null, null);
        }
        WorkflowTask task = workflowService.pageTodo(new WorkflowTaskQuery(tenantId, actorUserId, MerchantLimitAdjustmentWorkflowDefinition.PROCESS_KEY, mapping
            .businessKey(), null, false, 1, 10))
            .items()
            .stream()
            .filter(item -> mapping.processInstanceId().equals(item.processInstanceId()))
            .findFirst()
            .orElse(null);
        return new WorkflowContext(mapping.businessVersion(), task);
    }

    private record WorkflowContext(Long businessVersion, WorkflowTask task) {
    }

    private void requireTenant(Long tenantId) {
        if (tenantId == null || !tenantId.equals(TenantContextHolder.getTenantId())) {
            throw new MerchantAccessDeniedException();
        }
    }
}