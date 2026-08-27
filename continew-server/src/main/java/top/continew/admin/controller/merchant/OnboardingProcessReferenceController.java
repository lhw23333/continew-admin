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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
import top.continew.admin.channel.dto.ChannelSigningAction;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.merchant.onboarding.application.OnboardingProcessReference;
import top.continew.admin.merchant.onboarding.application.OnboardingProcessReferenceClaims;
import top.continew.admin.merchant.onboarding.application.OnboardingProcessReferenceService;
import top.continew.starter.log.annotation.Log;

/** Authorized generation/regeneration and resolution of process QR/action links. */
@Tag(name = "商户进件流程引用 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/merchant/merchants/{merchantId}/onboarding-drafts/{applicationId}/process-references")
public class OnboardingProcessReferenceController {

    private final OnboardingProcessReferenceService referenceService;

    @Log(ignore = true)
    @Operation(summary = "生成或重新生成流程二维码和操作链接")
    @SaCheckPermission("merchant:onboarding:create")
    @PostMapping
    public OnboardingProcessReference issue(@PathVariable Long merchantId,
                                            @PathVariable Long applicationId,
                                            @RequestBody @Valid IssueReq req,
                                            HttpServletRequest request) {
        return referenceService.issue(UserContextHolder.getTenantId(), UserContextHolder
            .getUserId(), merchantId, applicationId, req.action, JakartaServletUtil.getClientIP(request));
    }

    @Log(ignore = true)
    @Operation(summary = "校验并解析流程操作链接")
    @SaCheckPermission("merchant:onboarding:create")
    @PostMapping("/resolve")
    public OnboardingProcessReferenceClaims resolve(@PathVariable Long merchantId,
                                                    @PathVariable Long applicationId,
                                                    @RequestBody @Valid ResolveReq req,
                                                    HttpServletRequest request) {
        return referenceService.resolve(UserContextHolder.getTenantId(), UserContextHolder
            .getUserId(), merchantId, applicationId, req.token, JakartaServletUtil.getClientIP(request));
    }

    @Getter
    @Setter
    public static class IssueReq {
        @NotNull
        private ChannelSigningAction action;
    }

    @Getter
    @Setter
    public static class ResolveReq {
        @NotBlank
        @Size(max = 2048)
        private String token;
    }
}
