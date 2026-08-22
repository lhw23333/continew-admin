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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.merchant.review.application.OnboardingReviewAction;
import top.continew.admin.merchant.review.application.OnboardingReviewCommand;
import top.continew.admin.merchant.review.application.OnboardingReviewResult;
import top.continew.admin.merchant.review.application.OnboardingReviewService;
import top.continew.admin.merchant.review.application.OnboardingTransferCommand;
import top.continew.starter.log.annotation.Log;

import java.util.List;

/** Scoped onboarding human-review actions. */
@Tag(name = "商户进件审核任务 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/workflow/tasks")
public class WorkflowReviewController {

    private final OnboardingReviewService onboardingReviewService;

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
