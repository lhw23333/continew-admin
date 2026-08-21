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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.merchant.onboarding.application.OnboardingDraftService;
import top.continew.admin.merchant.onboarding.application.OnboardingDraftView;
import top.continew.starter.log.annotation.Log;

import java.util.List;

/** Explicit-save onboarding draft API. */
@Tag(name = "商户进件草稿 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/merchant/merchants/{merchantId}/onboarding-drafts")
public class OnboardingDraftController {

    private final OnboardingDraftService onboardingDraftService;

    @Log(ignore = true)
    @Operation(summary = "创建或恢复活动草稿", description = "同一商户渠道产品重复调用返回现有活动草稿")
    @SaCheckPermission("merchant:onboarding:create")
    @PostMapping
    public OnboardingDraftView createOrLoad(@PathVariable Long merchantId,
                                            @RequestBody @Valid DraftCreateReq req,
                                            HttpServletRequest request) {
        return onboardingDraftService.createOrLoad(UserContextHolder.getTenantId(), UserContextHolder
            .getUserId(), merchantId, req.getChannelCode(), req.getProductCode(), JakartaServletUtil
                .getClientIP(request));
    }

    @Log(ignore = true)
    @Operation(summary = "加载已保存草稿", description = "只返回服务端最后一次显式保存的步骤状态")
    @SaCheckPermission("merchant:onboarding:create")
    @GetMapping("/{applicationId}")
    public OnboardingDraftView load(@PathVariable Long merchantId, @PathVariable Long applicationId) {
        return onboardingDraftService.load(UserContextHolder.getTenantId(), UserContextHolder
            .getUserId(), merchantId, applicationId);
    }

    @Log(ignore = true)
    @Operation(summary = "显式保存草稿进度", description = "使用KYC业务版本执行乐观并发控制")
    @SaCheckPermission("merchant:onboarding:create")
    @PatchMapping("/{applicationId}/progress")
    public OnboardingDraftView saveProgress(@PathVariable Long merchantId,
                                            @PathVariable Long applicationId,
                                            @RequestBody @Valid DraftProgressReq req,
                                            HttpServletRequest request) {
        return onboardingDraftService.saveProgress(UserContextHolder.getTenantId(), UserContextHolder
            .getUserId(), merchantId, applicationId, req.getSavedStep(), req.getCompletedSteps(), req
                .getExpectedVersion(), JakartaServletUtil.getClientIP(request));
    }

    @Getter
    @Setter
    public static class DraftCreateReq {
        @NotBlank
        @Size(max = 64)
        private String channelCode;
        @NotBlank
        @Size(max = 64)
        private String productCode;
    }

    @Getter
    @Setter
    public static class DraftProgressReq {
        @NotNull
        @Min(1)
        @Max(5)
        private Integer savedStep;
        @NotNull
        @Size(max = 5)
        private List<@Min(1) @Max(5) Integer> completedSteps;
        @NotNull
        @PositiveOrZero
        private Long expectedVersion;
    }
}
