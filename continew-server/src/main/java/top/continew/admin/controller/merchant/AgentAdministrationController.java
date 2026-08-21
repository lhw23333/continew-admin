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
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.common.util.SecureUtils;
import top.continew.admin.merchant.agent.application.AgentSummary;
import top.continew.admin.merchant.agent.domain.AgentStatus;
import top.continew.admin.service.merchant.AgentAdministrationService;
import top.continew.starter.core.util.validation.ValidationUtils;
import top.continew.starter.log.annotation.Log;
import top.continew.starter.validation.constraints.Mobile;

/** Scoped agent profile, lifecycle, and password administration API. */
@Tag(name = "代理商管理操作 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/merchant/agents")
public class AgentAdministrationController {

    private final AgentAdministrationService administrationService;

    @Log(ignore = true)
    @Operation(summary = "修改代理商资料", description = "登录账号、父代理商及部门关系不可修改")
    @SaCheckPermission("merchant:agent:update")
    @PatchMapping("/{agentId}/profile")
    public AgentSummary updateProfile(@PathVariable Long agentId,
                                      @RequestBody @Valid AgentProfileUpdateReq req,
                                      HttpServletRequest request) {
        return administrationService.updateProfile(UserContextHolder.getTenantId(), UserContextHolder
            .getUserId(), agentId, req.getName(), req.getContactName(), req.getContactMobile(), req.getRemarks(), req
                .getExpectedVersion(), JakartaServletUtil.getClientIP(request));
    }

    @Log(ignore = true)
    @Operation(summary = "变更下级代理商状态", description = "禁止通过下级管理停用自身，停用后立即撤销会话")
    @SaCheckPermission("merchant:agent:lifecycle")
    @PatchMapping("/{agentId}/lifecycle")
    public AgentSummary changeLifecycle(@PathVariable Long agentId,
                                        @RequestBody @Valid AgentLifecycleReq req,
                                        HttpServletRequest request) {
        return administrationService.changeLifecycle(UserContextHolder.getTenantId(), UserContextHolder
            .getUserId(), agentId, req.getStatus(), req.getReason(), req.getExpectedVersion(), JakartaServletUtil
                .getClientIP(request));
    }

    @Log(ignore = true)
    @Operation(summary = "重置下级代理商临时密码", description = "重置后撤销会话并要求下次登录立即修改密码")
    @SaCheckPermission("merchant:agent:resetPassword")
    @PostMapping("/{agentId}/password/reset")
    public void resetPassword(@PathVariable Long agentId,
                              @RequestBody @Valid AgentPasswordResetReq req,
                              HttpServletRequest request) {
        String temporaryPassword = SecureUtils.decryptPasswordByRsaPrivateKey(req
            .getTemporaryPassword(), "临时密码解密失败", true);
        String confirmPassword = SecureUtils.decryptPasswordByRsaPrivateKey(req.getConfirmPassword(), "确认密码解密失败", true);
        ValidationUtils.throwIfNotEqual(temporaryPassword, confirmPassword, "两次输入的临时密码不一致");
        administrationService.resetTemporaryPassword(UserContextHolder.getTenantId(), UserContextHolder
            .getUserId(), agentId, temporaryPassword, req.getReason(), JakartaServletUtil.getClientIP(request));
    }

    @Getter
    @Setter
    @ToString(exclude = "contactMobile")
    public static class AgentProfileUpdateReq {

        @NotBlank
        @Size(max = 100)
        private String name;

        @NotBlank
        @Size(max = 100)
        private String contactName;

        @Mobile
        private String contactMobile;

        @Size(max = 255)
        private String remarks;

        @NotNull
        @PositiveOrZero
        private Long expectedVersion;
    }

    @Getter
    @Setter
    public static class AgentLifecycleReq {

        @NotNull
        private AgentStatus status;

        @NotBlank
        @Size(max = 255)
        private String reason;

        @NotNull
        @PositiveOrZero
        private Long expectedVersion;
    }

    @Getter
    @Setter
    @ToString(exclude = {"temporaryPassword", "confirmPassword"})
    public static class AgentPasswordResetReq {

        @NotBlank
        @Size(max = 4096)
        private String temporaryPassword;

        @NotBlank
        @Size(max = 4096)
        private String confirmPassword;

        @NotBlank
        @Size(max = 255)
        private String reason;
    }
}
