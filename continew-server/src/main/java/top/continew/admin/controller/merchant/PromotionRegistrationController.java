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

import cn.dev33.satoken.annotation.SaIgnore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.merchant.agent.application.AgentPromotionCodeService;
import top.continew.admin.merchant.agent.application.PromotionOwnership;
import top.continew.starter.extension.tenant.context.TenantContextHolder;
import top.continew.starter.log.annotation.Log;

/** Public registration preview that never accepts a client-selected owning agent ID. */
@Tag(name = "商户推广注册 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/merchant/registration/promotion")
public class PromotionRegistrationController {

    private final AgentPromotionCodeService promotionCodeService;

    @SaIgnore
    @Log(ignore = true)
    @Operation(summary = "校验推广码并预览归属", description = "商户归属完全由服务端推广码映射解析")
    @GetMapping("/{promotionCode}")
    public PromotionPreviewResp preview(@PathVariable String promotionCode) {
        PromotionOwnership ownership = promotionCodeService.resolveOwnership(TenantContextHolder
            .getTenantId(), promotionCode, null);
        return new PromotionPreviewResp(ownership.promotionCode(), ownership.agentName());
    }

    public record PromotionPreviewResp(String promotionCode, String agentName) {
    }
}
