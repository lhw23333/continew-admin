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
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.common.util.SecureUtils;
import top.continew.admin.service.merchant.SubordinateAgentCreateCommand;
import top.continew.admin.service.merchant.SubordinateAgentProvisioningResult;
import top.continew.admin.service.merchant.SubordinateAgentProvisioningService;
import top.continew.starter.log.annotation.Log;
import top.continew.starter.core.util.validation.ValidationUtils;
import top.continew.starter.validation.constraints.Mobile;

/** Direct subordinate agent creation API. */
@Tag(name = "下级代理商开通 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/merchant/agents/subordinates")
public class SubordinateAgentController {

    private final SubordinateAgentProvisioningService provisioningService;

    @Log(ignore = true)
    @Operation(summary = "开通直属下级代理商", description = "父代理商、部门、用户名和角色均由服务端解析或生成")
    @SaCheckPermission("merchant:agent:create")
    @PostMapping
    public SubordinateAgentProvisioningResult create(@RequestBody @Valid SubordinateAgentCreateReq req) {
        String temporaryPassword = SecureUtils.decryptPasswordByRsaPrivateKey(req
            .getTemporaryPassword(), "临时密码解密失败", true);
        String confirmPassword = SecureUtils.decryptPasswordByRsaPrivateKey(req.getConfirmPassword(), "确认密码解密失败", true);
        ValidationUtils.throwIfNotEqual(temporaryPassword, confirmPassword, "两次输入的临时密码不一致");
        return provisioningService.create(new SubordinateAgentCreateCommand(UserContextHolder
            .getTenantId(), UserContextHolder.getUserId(), req.getAgentNo(), req.getName(), req.getContactName(), req
                .getContactMobile(), temporaryPassword));
    }

    @Getter
    @Setter
    @ToString(exclude = {"contactMobile", "temporaryPassword", "confirmPassword"})
    public static class SubordinateAgentCreateReq {

        @NotBlank
        @Size(max = 64)
        private String agentNo;

        @NotBlank
        @Size(max = 100)
        private String name;

        @NotBlank
        @Size(max = 100)
        private String contactName;

        @NotBlank
        @Mobile
        private String contactMobile;

        @NotBlank
        @Size(max = 4096)
        private String temporaryPassword;

        @NotBlank
        @Size(max = 4096)
        private String confirmPassword;
    }
}
