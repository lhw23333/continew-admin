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

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.extra.servlet.JakartaServletUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.merchant.limit.application.LimitAdjustmentChannelExecutionCommand;
import top.continew.admin.merchant.limit.application.LimitAdjustmentChannelExecutionService;
import top.continew.admin.merchant.limit.application.LimitAdjustmentConfirmCommand;
import top.continew.admin.merchant.limit.application.LimitAdjustmentCreateResult;
import top.continew.admin.merchant.limit.application.LimitAdjustmentDetail;
import top.continew.admin.merchant.limit.application.LimitAdjustmentHistory;
import top.continew.admin.merchant.limit.application.LimitAdjustmentListQuery;
import top.continew.admin.merchant.limit.application.LimitAdjustmentPage;
import top.continew.admin.merchant.limit.application.LimitAdjustmentPreview;
import top.continew.admin.merchant.limit.application.LimitAdjustmentPreviewCommand;
import top.continew.admin.merchant.limit.application.LimitAdjustmentPreviewService;
import top.continew.admin.merchant.limit.application.LimitAdjustmentProcessResult;
import top.continew.admin.merchant.limit.application.LimitAdjustmentProcessService;
import top.continew.admin.merchant.limit.application.LimitAdjustmentQueryService;
import top.continew.admin.merchant.limit.application.LimitAdjustmentReviewAction;
import top.continew.admin.merchant.limit.application.LimitAdjustmentReviewCommand;
import top.continew.admin.merchant.limit.domain.LimitApprovalStatus;
import top.continew.admin.merchant.limit.domain.LimitChannelStatus;
import top.continew.admin.merchant.limit.domain.LimitEffectiveStatus;
import top.continew.admin.merchant.master.domain.MerchantDomainException;
import top.continew.admin.workflow.dto.WorkflowProcessHistory;
import top.continew.starter.log.annotation.Log;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Scope-aware limit adjustment creation, history, review, and channel task API. */
@Tag(name = "商户限额调整 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/merchant/merchants/{merchantId}/limit-adjustments")
public class LimitAdjustmentController {

    private final LimitAdjustmentPreviewService previewService;
    private final LimitAdjustmentQueryService queryService;
    private final LimitAdjustmentProcessService processService;
    private final LimitAdjustmentChannelExecutionService channelExecutionService;

    @Log(ignore = true)
    @Operation(summary = "分页查询商户限额调整历史")
    @SaCheckPermission("merchant:limit:list")
    @GetMapping
    public LimitAdjustmentPage page(@PathVariable Long merchantId,
                                    @RequestParam(required = false) String requestNo,
                                    @RequestParam(required = false) String channelCode,
                                    @RequestParam(required = false) String platformCode,
                                    @RequestParam(required = false) LimitApprovalStatus approvalStatus,
                                    @RequestParam(required = false) LimitChannelStatus channelStatus,
                                    @RequestParam(required = false) LimitEffectiveStatus effectiveStatus,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime appliedFrom,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime appliedTo,
                                    @RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        return queryService.page(UserContextHolder.getTenantId(), UserContextHolder
            .getUserId(), merchantId, new LimitAdjustmentListQuery(requestNo, channelCode, platformCode, approvalStatus, channelStatus, effectiveStatus, appliedFrom, appliedTo, page, size));
    }

    @Log(ignore = true)
    @Operation(summary = "查询限额调整详情")
    @SaCheckPermission("merchant:limit:list")
    @GetMapping("/{requestId}")
    public LimitAdjustmentDetail get(@PathVariable Long merchantId, @PathVariable Long requestId) {
        return queryService.get(UserContextHolder.getTenantId(), UserContextHolder.getUserId(), merchantId, requestId);
    }

    @Log(ignore = true)
    @Operation(summary = "查询不可变限额调整历史")
    @SaCheckPermission("merchant:limit:list")
    @GetMapping("/{requestId}/history")
    public List<LimitAdjustmentHistory> history(@PathVariable Long merchantId, @PathVariable Long requestId) {
        return queryService.history(UserContextHolder.getTenantId(), UserContextHolder
            .getUserId(), merchantId, requestId);
    }

    @Log(ignore = true)
    @Operation(summary = "查询限额调整流程历史")
    @SaCheckPermission("merchant:limit:list")
    @GetMapping("/{requestId}/workflow-history")
    public WorkflowProcessHistory workflowHistory(@PathVariable Long merchantId, @PathVariable Long requestId) {
        return queryService.workflowHistory(UserContextHolder.getTenantId(), UserContextHolder
            .getUserId(), merchantId, requestId);
    }

    @Log(ignore = true)
    @Operation(summary = "预览限额调整归一化结果")
    @SaCheckPermission("merchant:limit:create")
    @PostMapping("/preview")
    public LimitAdjustmentPreview preview(@PathVariable Long merchantId, @RequestBody @Valid PreviewReq req) {
        return previewService.preview(new LimitAdjustmentPreviewCommand(UserContextHolder
            .getTenantId(), UserContextHolder.getUserId(), merchantId, req.channelCode, req.platformCode, req
                .currency, req.requestedLimit));
    }

    @Operation(summary = "确认并创建限额调整")
    @SaCheckPermission("merchant:limit:create")
    @PostMapping
    public LimitAdjustmentCreateResult create(@PathVariable Long merchantId,
                                              @RequestBody @Valid ConfirmReq req,
                                              HttpServletRequest request) {
        return previewService.confirm(new LimitAdjustmentConfirmCommand(UserContextHolder
            .getTenantId(), UserContextHolder.getUserId(), merchantId, req.getChannelCode(), req.getPlatformCode(), req
                .getCurrency(), req.getRequestedLimit(), req.confirmedNormalizedLimit, req.confirmedPolicyVersion, req
                    .reason, JakartaServletUtil.getClientIP(request)));
    }

    @Operation(summary = "审核限额调整任务")
    @SaCheckPermission("workflow:task:review")
    @PostMapping("/{requestId}/tasks/{taskId}/review")
    public LimitAdjustmentProcessResult review(@PathVariable Long merchantId,
                                               @PathVariable Long requestId,
                                               @PathVariable String taskId,
                                               @RequestBody @Valid ReviewReq req,
                                               HttpServletRequest request) {
        requireCurrentTask(merchantId, requestId, taskId);
        return processService.review(new LimitAdjustmentReviewCommand(UserContextHolder
            .getTenantId(), UserContextHolder.getUserId(), taskId, req.businessVersion, req.action, req
                .opinion, JakartaServletUtil.getClientIP(request)));
    }

    @Operation(summary = "执行限额渠道提交或查询任务")
    @SaCheckPermission("workflow:task:review")
    @PostMapping("/{requestId}/tasks/{taskId}/execute-channel")
    public LimitAdjustmentProcessResult executeChannel(@PathVariable Long merchantId,
                                                       @PathVariable Long requestId,
                                                       @PathVariable String taskId,
                                                       @RequestBody @Valid ChannelExecutionReq req,
                                                       HttpServletRequest request) {
        requireCurrentTask(merchantId, requestId, taskId);
        return channelExecutionService.execute(new LimitAdjustmentChannelExecutionCommand(UserContextHolder
            .getTenantId(), UserContextHolder.getUserId(), taskId, req.businessVersion, req.traceId, JakartaServletUtil
                .getClientIP(request)));
    }

    private void requireCurrentTask(Long merchantId, Long requestId, String taskId) {
        LimitAdjustmentDetail detail = queryService.get(UserContextHolder.getTenantId(), UserContextHolder
            .getUserId(), merchantId, requestId);
        if (detail.currentTask() == null || !detail.currentTask().taskId().equals(taskId)) {
            throw new MerchantDomainException("Limit workflow task is unavailable");
        }
    }

    @Getter
    @Setter
    public static class PreviewReq {
        @NotBlank
        @Size(max = 64)
        private String channelCode;
        @NotBlank
        @Size(max = 64)
        private String platformCode;
        @NotBlank
        @Size(min = 3, max = 3)
        private String currency;
        @NotNull
        @DecimalMin(value = "0.01")
        @Digits(integer = 18, fraction = 2)
        private BigDecimal requestedLimit;
    }

    @Getter
    @Setter
    public static class ConfirmReq extends PreviewReq {
        @NotNull
        @DecimalMin(value = "0.01")
        @Digits(integer = 18, fraction = 2)
        private BigDecimal confirmedNormalizedLimit;
        @NotBlank
        @Size(max = 64)
        private String confirmedPolicyVersion;
        @NotBlank
        @Size(max = 1000)
        private String reason;
    }

    @Getter
    @Setter
    public static class ReviewReq {
        @NotNull
        @Positive
        private Long businessVersion;
        @NotNull
        private LimitAdjustmentReviewAction action;
        @Size(max = 2000)
        private String opinion;
    }

    @Getter
    @Setter
    public static class ChannelExecutionReq {
        @NotNull
        @Positive
        private Long businessVersion;
        @Size(max = 191)
        private String traceId;
    }
}