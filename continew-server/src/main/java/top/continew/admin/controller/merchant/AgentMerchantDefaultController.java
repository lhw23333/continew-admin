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
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.merchant.agent.application.AgentMerchantDefaultCreateCommand;
import top.continew.admin.merchant.agent.application.AgentMerchantDefaultService;
import top.continew.admin.merchant.agent.domain.AgentMerchantDefaultProduct;
import top.continew.admin.merchant.agent.domain.AgentMerchantDefaultVersion;
import top.continew.starter.log.annotation.Log;

import java.time.LocalDateTime;
import java.util.List;

/** Scoped append-only agent merchant-default API. */
@Tag(name = "代理商商户默认配置 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/merchant/agents")
public class AgentMerchantDefaultController {

    private final AgentMerchantDefaultService defaultService;

    @Log(ignore = true)
    @Operation(summary = "发布商户默认版本", description = "渠道产品和定价引用按版本保存，已发布版本不可修改")
    @SaCheckPermission("merchant:agent:defaults")
    @PostMapping("/{agentId}/merchant-default-versions")
    public AgentMerchantDefaultVersion create(@PathVariable Long agentId,
                                              @RequestBody @Valid AgentMerchantDefaultCreateReq req,
                                              HttpServletRequest request) {
        List<AgentMerchantDefaultProduct> products = req.getProducts()
            .stream()
            .map(product -> new AgentMerchantDefaultProduct(product.getChannelCode(), product.getProductCode(), product
                .getPricingVersionId()))
            .toList();
        return defaultService.create(new AgentMerchantDefaultCreateCommand(UserContextHolder
            .getTenantId(), UserContextHolder.getUserId(), agentId, products, req.getEffectiveTime(), req
                .getExpiresTime(), req.getReason(), JakartaServletUtil.getClientIP(request)));
    }

    @Operation(summary = "查询商户默认版本历史")
    @SaCheckPermission("merchant:agent:defaults")
    @GetMapping("/{agentId}/merchant-default-versions")
    public List<AgentMerchantDefaultVersion> list(@PathVariable Long agentId) {
        return defaultService.list(UserContextHolder.getTenantId(), UserContextHolder.getUserId(), agentId);
    }

    @Getter
    @Setter
    public static class AgentMerchantDefaultCreateReq {

        @Valid
        @NotEmpty
        @Size(max = 100)
        private List<DefaultProductReq> products;

        @NotNull
        private LocalDateTime effectiveTime;

        private LocalDateTime expiresTime;

        @NotBlank
        @Size(max = 255)
        private String reason;
    }

    @Getter
    @Setter
    public static class DefaultProductReq {

        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]{0,63}")
        private String channelCode;

        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]{0,63}")
        private String productCode;

        @NotNull
        @Positive
        private Long pricingVersionId;
    }
}
