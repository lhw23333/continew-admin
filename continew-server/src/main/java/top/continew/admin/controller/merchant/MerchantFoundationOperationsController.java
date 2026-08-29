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

package top.continew.admin.controller.merchant;

import cn.dev33.satoken.annotation.SaCheckRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.service.merchant.MerchantFoundationOperationsService;
import top.continew.admin.service.merchant.MerchantFoundationOperationsService.MerchantFoundationReadiness;
import top.continew.admin.workflow.dto.WorkflowDeploymentRef;

import java.util.List;

/** Platform-only merchant tenant readiness and controlled workflow deployment operations. */
@Tag(name = "商户租户基础运维 API")
@Validated
@RestController
@RequiredArgsConstructor
@SaCheckRole("super_admin")
@RequestMapping("/merchant/tenant-operations/{tenantId}")
public class MerchantFoundationOperationsController {

    private final MerchantFoundationOperationsService operationsService;

    @Operation(summary = "查询商户租户就绪状态")
    @GetMapping("/readiness")
    public MerchantFoundationReadiness readiness(@PathVariable Long tenantId) {
        return operationsService.readiness(tenantId);
    }

    @Operation(summary = "幂等补齐商户租户基础数据")
    @PostMapping("/bootstrap")
    public MerchantFoundationReadiness bootstrap(@PathVariable Long tenantId) {
        return operationsService.bootstrap(tenantId);
    }

    @Operation(summary = "显式部署受审商户工作流")
    @PostMapping("/deploy-workflows")
    public List<WorkflowDeploymentRef> deployWorkflows(@PathVariable Long tenantId) {
        return operationsService.deployWorkflows(tenantId);
    }

    @Operation(summary = "对账并修复已结束工作流映射")
    @PostMapping("/reconcile-workflows")
    public WorkflowReconciliationResp reconcileWorkflows(@PathVariable Long tenantId) {
        return new WorkflowReconciliationResp(operationsService.reconcileWorkflowMappings(tenantId));
    }

    public record WorkflowReconciliationResp(int repairedMappings) {
    }
}
