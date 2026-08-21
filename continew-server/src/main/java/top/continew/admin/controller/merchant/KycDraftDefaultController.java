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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.merchant.agent.application.AgentMerchantDefaultService;
import top.continew.admin.merchant.agent.domain.KycDraftDefaultSnapshot;
import top.continew.starter.log.annotation.Log;

/** Applies server-resolved effective agent defaults to a KYC draft exactly once. */
@Tag(name = "KYC 草稿默认配置 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/merchant/onboarding/drafts")
public class KycDraftDefaultController {

    private final AgentMerchantDefaultService defaultService;

    @Log(ignore = true)
    @Operation(summary = "继承代理商默认配置", description = "归属代理商由草稿对应商户在服务端解析")
    @SaCheckPermission("merchant:onboarding:draft")
    @PostMapping("/{kycVersionId}/inherit-agent-defaults")
    public ResponseEntity<KycDraftDefaultSnapshot> inherit(@PathVariable Long kycVersionId,
                                                           HttpServletRequest request) {
        return defaultService.inheritIntoDraft(UserContextHolder.getTenantId(), UserContextHolder
            .getUserId(), kycVersionId, JakartaServletUtil.getClientIP(request))
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
