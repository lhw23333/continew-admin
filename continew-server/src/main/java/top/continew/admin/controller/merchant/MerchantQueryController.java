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
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.extra.servlet.JakartaServletUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.merchant.master.application.MerchantActionPermissions;
import top.continew.admin.merchant.master.application.MerchantDetail;
import top.continew.admin.merchant.master.application.MerchantListQuery;
import top.continew.admin.merchant.master.application.MerchantPage;
import top.continew.admin.merchant.master.application.MerchantQueryService;
import top.continew.admin.merchant.master.domain.MerchantStatus;
import top.continew.admin.merchant.master.domain.MerchantType;
import top.continew.starter.log.annotation.Log;

import java.time.LocalDateTime;

/** Scope-aware merchant search and detail API. */
@Tag(name = "商户查询 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/merchant/merchants")
public class MerchantQueryController {

    private final MerchantQueryService merchantQueryService;

    @Log(ignore = true)
    @Operation(summary = "分页查询授权商户", description = "使用租户和代理商闭包范围，按创建时间和ID稳定排序")
    @SaCheckPermission("merchant:merchant:list")
    @GetMapping
    public MerchantPage page(@RequestParam(required = false) Long merchantId,
                             @RequestParam(required = false) String merchantNo,
                             @RequestParam(required = false) String loginAccount,
                             @RequestParam(required = false) String legalName,
                             @RequestParam(required = false) String shortName,
                             @RequestParam(required = false) String contact,
                             @RequestParam(required = false) String legalRepresentative,
                             @RequestParam(required = false) MerchantType merchantType,
                             @RequestParam(required = false) Long owningAgentId,
                             @RequestParam(required = false) String channelCode,
                             @RequestParam(required = false) String applicationStatus,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime applicationUpdatedTo,
                             @RequestParam(required = false) MerchantStatus status,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo,
                             @RequestParam(defaultValue = "1") int page,
                             @RequestParam(defaultValue = "20") int size,
                             HttpServletRequest request) {
        return merchantQueryService.page(UserContextHolder.getTenantId(), UserContextHolder
            .getUserId(), new MerchantListQuery(merchantId, merchantNo, loginAccount, legalName, shortName, contact, legalRepresentative, merchantType, owningAgentId, channelCode, MerchantListQuery
                .parseApplicationStatuses(applicationStatus), applicationUpdatedTo, status, createdFrom, createdTo, page, size, JakartaServletUtil
                .getClientIP(request)), permissions());
    }

    @Log(ignore = true)
    @Operation(summary = "查询授权商户详情", description = "不存在与越权商户返回相同的访问拒绝")
    @SaCheckPermission("merchant:merchant:get")
    @GetMapping("/{merchantId}")
    public MerchantDetail get(@PathVariable Long merchantId, HttpServletRequest request) {
        return merchantQueryService.get(UserContextHolder.getTenantId(), UserContextHolder
            .getUserId(), merchantId, permissions(), JakartaServletUtil.getClientIP(request));
    }

    private MerchantActionPermissions permissions() {
        return new MerchantActionPermissions(StpUtil.hasPermission("merchant:merchant:get"), StpUtil
            .hasPermission("merchant:merchant:update"), StpUtil.hasPermission("merchant:onboarding:create"), StpUtil
                .hasPermission("merchant:merchant:lifecycle"), StpUtil
                    .hasPermission("merchant:merchant:reverify"), StpUtil
                        .hasPermission("merchant:limit:create"), StpUtil.hasPermission("merchant:limit:list"));
    }
}
