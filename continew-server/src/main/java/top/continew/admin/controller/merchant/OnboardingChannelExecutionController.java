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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.service.merchant.OnboardingChannelExecutionService;
import top.continew.admin.service.merchant.OnboardingChannelExecutionService.OnboardingChannelExecutionCommand;
import top.continew.admin.service.merchant.OnboardingChannelExecutionService.OnboardingChannelExecutionResult;

/** Explicit onboarding channel command operations separated from human approval. */
@Tag(name = "商户进件渠道执行 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/merchant/merchants/{merchantId}/onboarding/{applicationId}/channel")
public class OnboardingChannelExecutionController {

    private final OnboardingChannelExecutionService executionService;

    @Operation(summary = "向渠道提交已审核通过的进件")
    @SaCheckPermission("merchant:onboarding:channel:submit")
    @PostMapping("/submit")
    public OnboardingChannelExecutionResult submit(@PathVariable Long merchantId, @PathVariable Long applicationId) {
        return executionService.submit(command(merchantId, applicationId));
    }

    @Operation(summary = "查询渠道进件状态")
    @SaCheckPermission("merchant:onboarding:channel:query")
    @PostMapping("/query")
    public OnboardingChannelExecutionResult query(@PathVariable Long merchantId, @PathVariable Long applicationId) {
        return executionService.query(command(merchantId, applicationId));
    }

    private OnboardingChannelExecutionCommand command(Long merchantId, Long applicationId) {
        return new OnboardingChannelExecutionCommand(UserContextHolder.getTenantId(), UserContextHolder
            .getUserId(), merchantId, applicationId, null);
    }
}
