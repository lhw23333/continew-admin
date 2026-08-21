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
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.merchant.master.application.MerchantProfileView;
import top.continew.admin.merchant.master.domain.MerchantStatus;
import top.continew.admin.service.merchant.MerchantAdministrationService;
import top.continew.admin.service.merchant.MerchantReverificationRoutingService;
import top.continew.admin.service.merchant.MerchantReverificationRoutingService.MerchantReverificationChangeType;
import top.continew.admin.service.merchant.MerchantReverificationRoutingService.MerchantReverificationRoute;
import top.continew.starter.log.annotation.Log;
import top.continew.starter.validation.constraints.Mobile;

import java.util.Set;

/** Ordinary merchant profile edit and controlled certified-change routing API. */
@Tag(name = "商户资料管理 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/merchant/merchants")
public class MerchantAdministrationController {

    private final MerchantAdministrationService administrationService;
    private final MerchantReverificationRoutingService reverificationRoutingService;

    @Log(ignore = true)
    @Operation(summary = "修改商户普通资料", description = "不允许修改法定身份、归属代理商或结算账户")
    @SaCheckPermission("merchant:merchant:update")
    @PatchMapping("/{merchantId}/profile")
    public MerchantProfileView updateProfile(@PathVariable Long merchantId,
                                             @RequestBody @Valid MerchantProfileUpdateReq req,
                                             HttpServletRequest request) {
        return administrationService.updateProfile(UserContextHolder.getTenantId(), UserContextHolder
            .getUserId(), merchantId, req.getShortName(), req.getContactName(), req.getContactMobile(), req
                .getReviewerMobile(), req.getIndustry(), req.getProductDescription(), req
                    .getExpectedVersion(), JakartaServletUtil.getClientIP(request));
    }

    @Log(ignore = true)
    @Operation(summary = "创建认证字段重新核验请求", description = "复用商户进件审核流程，敏感值由后续KYC草稿承载")
    @SaCheckPermission("merchant:merchant:reverify")
    @PostMapping("/{merchantId}/reverification-requests")
    public MerchantReverificationRoute routeReverification(@PathVariable Long merchantId,
                                                           @RequestBody @Valid MerchantReverificationReq req,
                                                           HttpServletRequest request) {
        return reverificationRoutingService.route(UserContextHolder.getTenantId(), UserContextHolder
            .getUserId(), merchantId, req.getChangeTypes(), req.getTargetAgentId(), req.getReason(), JakartaServletUtil
                .getClientIP(request));
    }

    @Log(ignore = true)
    @Operation(summary = "启用或停用商户", description = "同步双岗位账号状态，停用时撤销会话并阻止新的下游操作")
    @SaCheckPermission("merchant:merchant:lifecycle")
    @PatchMapping("/{merchantId}/lifecycle")
    public MerchantProfileView changeLifecycle(@PathVariable Long merchantId,
                                               @RequestBody @Valid MerchantLifecycleReq req,
                                               HttpServletRequest request) {
        return administrationService.changeLifecycle(UserContextHolder.getTenantId(), UserContextHolder
            .getUserId(), merchantId, req.getStatus(), req.getReason(), req.getExpectedVersion(), JakartaServletUtil
                .getClientIP(request));
    }

    @Getter
    @Setter
    public static class MerchantProfileUpdateReq {
        @NotBlank
        @Size(max = 100)
        private String shortName;
        @NotBlank
        @Size(max = 100)
        private String contactName;
        @Mobile
        private String contactMobile;
        @Mobile
        private String reviewerMobile;
        @Size(max = 100)
        private String industry;
        @Size(max = 255)
        private String productDescription;
        @NotNull
        @PositiveOrZero
        private Long expectedVersion;
    }

    @Getter
    @Setter
    public static class MerchantReverificationReq {
        @NotEmpty
        private Set<MerchantReverificationChangeType> changeTypes;
        @Positive
        private Long targetAgentId;
        @NotBlank
        @Size(max = 255)
        private String reason;
    }

    @Getter
    @Setter
    public static class MerchantLifecycleReq {
        @NotNull
        private MerchantStatus status;
        @NotBlank
        @Size(max = 255)
        private String reason;
        @NotNull
        @PositiveOrZero
        private Long expectedVersion;
    }
}
