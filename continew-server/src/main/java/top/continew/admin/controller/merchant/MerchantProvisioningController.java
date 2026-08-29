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
import jakarta.validation.constraints.Positive;
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
import top.continew.admin.merchant.master.domain.MerchantType;
import top.continew.admin.service.merchant.MerchantCreateCommand;
import top.continew.admin.service.merchant.MerchantProvisioningResult;
import top.continew.admin.service.merchant.MerchantProvisioningService;
import top.continew.starter.core.util.validation.ValidationUtils;
import top.continew.starter.log.annotation.Log;
import top.continew.starter.validation.constraints.Mobile;

/** Atomic merchant master and distinct operator/reviewer identity creation API. */
@Tag(name = "商户创建 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/merchant/merchants")
public class MerchantProvisioningController {

    private final MerchantProvisioningService provisioningService;

    @Log(ignore = true)
    @Operation(summary = "创建商户和双岗位账号", description = "商户号、登录账号和角色由服务端生成并原子绑定")
    @SaCheckPermission("merchant:merchant:create")
    @PostMapping
    public MerchantProvisioningResult create(@RequestBody @Valid MerchantCreateReq req, HttpServletRequest request) {
        ValidationUtils.throwIf(UserContextHolder.getTenantId() == null || UserContextHolder
            .getTenantId() <= 0, "默认租户不能创建商户，请先进入业务租户");
        String operatorPassword = decrypt(req.getOperatorTemporaryPassword(), "操作员临时密码解密失败");
        String operatorConfirm = decrypt(req.getOperatorConfirmPassword(), "操作员确认密码解密失败");
        ValidationUtils.throwIfNotEqual(operatorPassword, operatorConfirm, "操作员两次输入的临时密码不一致");
        String reviewerPassword = decrypt(req.getReviewerTemporaryPassword(), "复核员临时密码解密失败");
        String reviewerConfirm = decrypt(req.getReviewerConfirmPassword(), "复核员确认密码解密失败");
        ValidationUtils.throwIfNotEqual(reviewerPassword, reviewerConfirm, "复核员两次输入的临时密码不一致");
        return provisioningService.create(new MerchantCreateCommand(UserContextHolder.getTenantId(), UserContextHolder
            .getUserId(), req.getOwningAgentId(), req.getMerchantType(), req.getLegalName(), req.getShortName(), req
                .getLegalIdentifier(), req.getContactName(), req.getContactMobile(), req.getReviewerMobile(), req
                    .getIndustry(), req.getProductDescription(), operatorPassword, reviewerPassword, JakartaServletUtil
                        .getClientIP(request)));
    }

    private String decrypt(String value, String message) {
        return SecureUtils.decryptPasswordByRsaPrivateKey(value, message, true);
    }

    @Getter
    @Setter
    @ToString(exclude = {"legalIdentifier", "contactMobile", "reviewerMobile", "operatorTemporaryPassword",
        "operatorConfirmPassword", "reviewerTemporaryPassword", "reviewerConfirmPassword"})
    public static class MerchantCreateReq {

        @NotNull
        @Positive
        private Long owningAgentId;

        @NotNull
        private MerchantType merchantType;

        @NotBlank
        @Size(max = 200)
        private String legalName;

        @NotBlank
        @Size(max = 100)
        private String shortName;

        @NotBlank
        @Size(max = 64)
        private String legalIdentifier;

        @NotBlank
        @Size(max = 100)
        private String contactName;

        @NotBlank
        @Mobile
        private String contactMobile;

        @NotBlank
        @Mobile
        private String reviewerMobile;

        @Size(max = 100)
        private String industry;

        @Size(max = 255)
        private String productDescription;

        @NotBlank
        @Size(max = 4096)
        private String operatorTemporaryPassword;

        @NotBlank
        @Size(max = 4096)
        private String operatorConfirmPassword;

        @NotBlank
        @Size(max = 4096)
        private String reviewerTemporaryPassword;

        @NotBlank
        @Size(max = 4096)
        private String reviewerConfirmPassword;
    }
}
