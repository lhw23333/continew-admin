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
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.merchant.agent.application.AgentPricingCreateCommand;
import top.continew.admin.merchant.agent.application.AgentPricingService;
import top.continew.admin.merchant.agent.domain.AgentPricingRules;
import top.continew.admin.merchant.agent.domain.AgentPricingVersion;
import top.continew.starter.log.annotation.Log;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Scoped append-only agent pricing API. */
@Tag(name = "代理商定价 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/merchant/agents")
public class AgentPricingController {

    private final AgentPricingService pricingService;

    @Log(ignore = true)
    @Operation(summary = "发布代理商定价版本", description = "父级有效版本由服务端解析，已发布版本不可修改或删除")
    @SaCheckPermission("merchant:agent:pricing")
    @PostMapping("/{agentId}/pricing-versions")
    public AgentPricingVersion create(@PathVariable Long agentId,
                                      @RequestBody @Valid AgentPricingCreateReq req,
                                      HttpServletRequest request) {
        return pricingService.create(new AgentPricingCreateCommand(UserContextHolder.getTenantId(), UserContextHolder
            .getUserId(), agentId, req.getChannelCode(), req.getProductCode(), req
                .getCurrency(), new AgentPricingRules(req.getPercentageCost(), req.getFixedFee(), req
                    .getProfitShareRatio()), req.getEffectiveTime(), req.getExpiresTime(), req
                        .getReason(), JakartaServletUtil.getClientIP(request)));
    }

    @Operation(summary = "查询代理商定价版本")
    @SaCheckPermission("merchant:agent:pricing")
    @GetMapping("/{agentId}/pricing-versions")
    public List<AgentPricingVersion> list(@PathVariable Long agentId,
                                          @RequestParam String channelCode,
                                          @RequestParam String productCode,
                                          @RequestParam(defaultValue = "CNY") String currency) {
        return pricingService.list(UserContextHolder.getTenantId(), UserContextHolder
            .getUserId(), agentId, channelCode, productCode, currency);
    }

    @Getter
    @Setter
    public static class AgentPricingCreateReq {

        @NotBlank
        @Size(max = 64)
        @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]{0,63}")
        private String channelCode;

        @NotBlank
        @Size(max = 64)
        @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]{0,63}")
        private String productCode;

        @NotBlank
        @Pattern(regexp = "[A-Za-z]{3}")
        private String currency = "CNY";

        @NotNull
        @DecimalMin("0")
        @DecimalMax("1")
        @Digits(integer = 1, fraction = 8)
        private BigDecimal percentageCost;

        @NotNull
        @DecimalMin("0")
        @DecimalMax("1000000000")
        @Digits(integer = 10, fraction = 2)
        private BigDecimal fixedFee;

        @NotNull
        @DecimalMin("0")
        @DecimalMax("1")
        @Digits(integer = 1, fraction = 8)
        private BigDecimal profitShareRatio;

        @NotNull
        private LocalDateTime effectiveTime;

        private LocalDateTime expiresTime;

        @NotBlank
        @Size(max = 255)
        private String reason;
    }
}
