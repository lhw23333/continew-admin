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
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.merchant.agent.application.AgentListQuery;
import top.continew.admin.merchant.agent.application.AgentPage;
import top.continew.admin.merchant.agent.application.AgentQueryService;
import top.continew.admin.merchant.agent.application.AgentSummary;
import top.continew.admin.merchant.agent.domain.AgentStatus;
import top.continew.starter.log.annotation.Log;

/** Scope-aware agent list and detail API. */
@Tag(name = "代理商查询 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/merchant/agents")
public class AgentQueryController {

    private final AgentQueryService agentQueryService;

    @Log(ignore = true)
    @Operation(summary = "分页查询授权代理商", description = "仅返回当前代理商及授权后代，按创建时间和 ID 稳定排序")
    @SaCheckPermission("merchant:agent:list")
    @GetMapping
    public AgentPage page(@RequestParam(required = false) Long agentId,
                          @RequestParam(required = false) String name,
                          @RequestParam(required = false) AgentStatus status,
                          @RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "20") int size,
                          HttpServletRequest request) {
        return agentQueryService.page(UserContextHolder.getTenantId(), UserContextHolder
            .getUserId(), new AgentListQuery(agentId, name, status, page, size, JakartaServletUtil
                .getClientIP(request)));
    }

    @Log(ignore = true)
    @Operation(summary = "查询授权代理商详情", description = "不存在与越权代理商返回相同的访问拒绝")
    @SaCheckPermission("merchant:agent:get")
    @GetMapping("/{agentId}")
    public AgentSummary get(@PathVariable Long agentId, HttpServletRequest request) {
        return agentQueryService.get(UserContextHolder.getTenantId(), UserContextHolder
            .getUserId(), agentId, JakartaServletUtil.getClientIP(request));
    }
}
