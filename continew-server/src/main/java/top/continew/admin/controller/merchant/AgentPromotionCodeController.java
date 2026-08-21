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
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
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
import top.continew.admin.merchant.agent.application.AgentPromotionCodeService;
import top.continew.admin.merchant.agent.application.AgentPromotionCodeView;
import top.continew.admin.merchant.agent.domain.AgentPromotionCodeStatus;
import top.continew.starter.log.annotation.Log;

/** Promotion code issue and lifecycle API. */
@Tag(name = "代理商推广码 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/merchant/agents")
public class AgentPromotionCodeController {

    private final AgentPromotionCodeService promotionCodeService;

    @Log(ignore = true)
    @Operation(summary = "签发代理商推广码", description = "推广码租户内唯一，可作为二维码业务引用")
    @SaCheckPermission("merchant:agent:promotionCode")
    @PostMapping("/{agentId}/promotion-code")
    public AgentPromotionCodeView issue(@PathVariable Long agentId,
                                        @RequestBody @Valid PromotionIssueReq req,
                                        HttpServletRequest request) {
        return promotionCodeService.issue(UserContextHolder.getTenantId(), UserContextHolder.getUserId(), agentId, req
            .getExpectedVersion(), JakartaServletUtil.getClientIP(request));
    }

    @Log(ignore = true)
    @Operation(summary = "修改代理商推广码状态", description = "推广码停用后不能建立新的商户归属")
    @SaCheckPermission("merchant:agent:promotionCode")
    @PatchMapping("/{agentId}/promotion-code/status")
    public AgentPromotionCodeView changeStatus(@PathVariable Long agentId,
                                               @RequestBody @Valid PromotionStatusReq req,
                                               HttpServletRequest request) {
        return promotionCodeService.changeStatus(UserContextHolder.getTenantId(), UserContextHolder
            .getUserId(), agentId, req.getStatus(), req.getExpectedVersion(), JakartaServletUtil.getClientIP(request));
    }

    @Getter
    @Setter
    public static class PromotionIssueReq {

        @NotNull
        @PositiveOrZero
        private Long expectedVersion;
    }

    @Getter
    @Setter
    public static class PromotionStatusReq {

        @NotNull
        private AgentPromotionCodeStatus status;

        @NotNull
        @PositiveOrZero
        private Long expectedVersion;
    }
}
