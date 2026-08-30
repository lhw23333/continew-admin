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

package top.continew.admin.controller.workflow;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.extra.servlet.JakartaServletUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.merchant.review.application.OnboardingReviewAction;
import top.continew.admin.merchant.review.application.OnboardingReviewCommand;
import top.continew.admin.merchant.review.application.OnboardingReviewResult;
import top.continew.admin.merchant.review.application.OnboardingReviewService;
import top.continew.admin.merchant.review.application.OnboardingTransferCommand;
import top.continew.admin.merchant.review.application.WorkflowTaskCenterService;
import top.continew.admin.merchant.review.application.WorkflowTaskDetail;
import top.continew.admin.merchant.review.application.WorkflowTaskView;
import top.continew.admin.merchant.master.domain.MerchantDomainException;
import top.continew.admin.workflow.api.WorkflowService;
import top.continew.admin.workflow.command.ClaimTaskCommand;
import top.continew.admin.workflow.command.UnclaimTaskCommand;
import top.continew.admin.workflow.definition.MerchantOnboardingReviewWorkflowDefinition;
import top.continew.admin.workflow.dto.WorkflowPage;
import top.continew.admin.workflow.dto.WorkflowProcessHistory;
import top.continew.admin.workflow.dto.WorkflowTask;
import top.continew.admin.workflow.query.WorkflowDoneQuery;
import top.continew.admin.workflow.query.WorkflowTaskQuery;
import top.continew.starter.log.annotation.Log;

import java.time.LocalDateTime;
import java.util.List;

/** Scoped onboarding human-review actions. */
@Tag(name = "商户进件审核任务 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/workflow/tasks")
public class WorkflowReviewController {

    private final OnboardingReviewService onboardingReviewService;
    private final WorkflowTaskCenterService taskCenterService;
    private final WorkflowService workflowService;

    @Log(ignore = true)
    @Operation(summary = "查询待办审核任务")
    @SaCheckPermission("workflow:task:list")
    @GetMapping("/todo")
    public WorkflowTaskPageResp todo(@RequestParam(required = false) String processDefinitionKey,
                                     @RequestParam(required = false) String businessKey,
                                     @RequestParam(required = false) String taskName,
                                     @RequestParam(required = false) String taskDefinitionKey,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dueBefore,
                                     @RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        return page(taskCenterService.pageTodo(new WorkflowTaskQuery(UserContextHolder.getTenantId(), UserContextHolder
            .getUserId(), onboardingProcessKey(processDefinitionKey), businessKey, taskName, taskDefinitionKey, dueBefore, false, page, size)));
    }

    @Log(ignore = true)
    @Operation(summary = "查询已认领审核任务")
    @SaCheckPermission("workflow:task:list")
    @GetMapping("/claimed")
    public WorkflowTaskPageResp claimed(@RequestParam(required = false) String processDefinitionKey,
                                        @RequestParam(required = false) String businessKey,
                                        @RequestParam(required = false) String taskName,
                                        @RequestParam(required = false) String taskDefinitionKey,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dueBefore,
                                        @RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return page(taskCenterService.pageTodo(new WorkflowTaskQuery(UserContextHolder.getTenantId(), UserContextHolder
            .getUserId(), onboardingProcessKey(processDefinitionKey), businessKey, taskName, taskDefinitionKey, dueBefore, true, page, size)));
    }

    @Log(ignore = true)
    @Operation(summary = "查询已办审核任务")
    @SaCheckPermission("workflow:task:list")
    @GetMapping("/done")
    public WorkflowTaskPageResp done(@RequestParam(required = false) String processDefinitionKey,
                                     @RequestParam(required = false) String businessKey,
                                     @RequestParam(required = false) String taskName,
                                     @RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        return page(taskCenterService.pageDone(new WorkflowDoneQuery(UserContextHolder.getTenantId(), UserContextHolder
            .getUserId(), onboardingProcessKey(processDefinitionKey), businessKey, taskName, page, size)));
    }

    @Log(ignore = true)
    @Operation(summary = "查询审核任务详情")
    @SaCheckPermission("workflow:task:get")
    @GetMapping("/{taskId}")
    public WorkflowTaskDetail detail(@PathVariable String taskId) {
        return taskCenterService.detail(UserContextHolder.getTenantId(), UserContextHolder.getUserId(), taskId);
    }

    @Log(ignore = true)
    @Operation(summary = "查询审核流程历史")
    @SaCheckPermission("workflow:task:history")
    @GetMapping("/processes/{processInstanceId}/history")
    public WorkflowProcessHistory history(@PathVariable String processInstanceId) {
        return taskCenterService.history(UserContextHolder.getTenantId(), UserContextHolder
            .getUserId(), processInstanceId);
    }

    @Operation(summary = "认领审核任务")
    @SaCheckPermission("workflow:task:claim")
    @PostMapping("/{taskId}/claim")
    public void claim(@PathVariable String taskId) {
        workflowService.claim(new ClaimTaskCommand(UserContextHolder.getTenantId(), taskId, UserContextHolder
            .getUserId()));
    }

    @Operation(summary = "取消认领审核任务")
    @SaCheckPermission("workflow:task:claim")
    @PostMapping("/{taskId}/unclaim")
    public void unclaim(@PathVariable String taskId) {
        WorkflowTask task = workflowService.task(UserContextHolder.getTenantId(), UserContextHolder
            .getUserId(), taskId);
        if ("supplementTask".equals(task.taskDefinitionKey())) {
            throw new MerchantDomainException("Applicant supplement task cannot be unclaimed");
        }
        workflowService.unclaim(new UnclaimTaskCommand(UserContextHolder.getTenantId(), taskId, UserContextHolder
            .getUserId()));
    }

    private WorkflowTaskPageResp page(WorkflowPage<WorkflowTaskView> page) {
        return new WorkflowTaskPageResp(page.items(), page.total());
    }

    private String onboardingProcessKey(String requested) {
        if (requested != null && !requested.isBlank() && !MerchantOnboardingReviewWorkflowDefinition.PROCESS_KEY
            .equals(requested.trim())) {
            throw new MerchantDomainException("Unsupported workflow process definition");
        }
        return MerchantOnboardingReviewWorkflowDefinition.PROCESS_KEY;
    }

    public record WorkflowTaskPageResp(List<WorkflowTaskView> list, long total) {
        public WorkflowTaskPageResp {
            list = List.copyOf(list);
        }
    }

    @Log(ignore = true)
    @Operation(summary = "执行进件审核动作")
    @SaCheckPermission("workflow:task:review")
    @PostMapping("/{taskId}/actions")
    public OnboardingReviewResult review(@PathVariable String taskId,
                                         @RequestBody @Valid ReviewActionReq req,
                                         HttpServletRequest request) {
        return onboardingReviewService.review(new OnboardingReviewCommand(UserContextHolder
            .getTenantId(), UserContextHolder
                .getUserId(), taskId, req.businessVersion, req.action, req.opinion, req.issueCodes, JakartaServletUtil
                    .getClientIP(request)));
    }

    @Log(ignore = true)
    @Operation(summary = "转派进件审核任务")
    @SaCheckPermission("workflow:task:transfer")
    @PostMapping("/{taskId}/transfer")
    public OnboardingReviewResult transfer(@PathVariable String taskId,
                                           @RequestBody @Valid TransferReq req,
                                           HttpServletRequest request) {
        return onboardingReviewService.transfer(new OnboardingTransferCommand(UserContextHolder
            .getTenantId(), UserContextHolder
                .getUserId(), taskId, req.targetUserId, req.businessVersion, req.reason, JakartaServletUtil
                    .getClientIP(request)));
    }

    @Getter
    @Setter
    public static class ReviewActionReq {
        @NotNull
        @Positive
        private Long businessVersion;
        @NotNull
        private OnboardingReviewAction action;
        @Size(max = 2000)
        private String opinion;
        @Size(max = 20)
        private List<@NotBlank @Size(max = 64) String> issueCodes;
    }

    @Getter
    @Setter
    public static class TransferReq {
        @NotNull
        @Positive
        private Long targetUserId;
        @NotNull
        @Positive
        private Long businessVersion;
        @NotBlank
        @Size(max = 2000)
        private String reason;
    }
}
