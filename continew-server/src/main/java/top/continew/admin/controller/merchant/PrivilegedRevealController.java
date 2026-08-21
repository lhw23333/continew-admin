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

import cn.hutool.extra.servlet.JakartaServletUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.http.HttpHeaders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.merchant.security.reveal.MerchantSensitiveField;
import top.continew.admin.merchant.security.reveal.PrivilegedRevealCommand;
import top.continew.admin.merchant.security.reveal.PrivilegedRevealResult;
import top.continew.admin.merchant.security.reveal.PrivilegedRevealService;
import top.continew.starter.log.annotation.Log;

import java.time.LocalDateTime;

/** Privileged merchant-sensitive-value API. */
@Tag(name = "商户敏感数据 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/merchant/masters")
public class PrivilegedRevealController {

    private final PrivilegedRevealService privilegedRevealService;

    @Log(ignore = true)
    @Operation(summary = "临时查看完整敏感字段", description = "需要独立权限、业务范围、理由及当前密码二次认证")
    @PostMapping("/{merchantId}/sensitive/reveal")
    public PrivilegedRevealResp reveal(@PathVariable Long merchantId,
                                       @RequestBody @Valid PrivilegedRevealReq req,
                                       HttpServletRequest request,
                                       HttpServletResponse response) {
        applyNoStoreHeaders(response);
        PrivilegedRevealResult result = privilegedRevealService.reveal(new PrivilegedRevealCommand(UserContextHolder
            .getTenantId(), UserContextHolder.getUserId(), merchantId, req.getField(), req.getReason(), req
                .getPassword(), JakartaServletUtil.getClientIP(request)));
        return new PrivilegedRevealResp(result.field(), result.value(), result.revealedAt());
    }

    private void applyNoStoreHeaders(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, max-age=0, must-revalidate, private");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        response.setDateHeader(HttpHeaders.EXPIRES, 0);
        response.setHeader("Referrer-Policy", "no-referrer");
    }

    @Getter
    @Setter
    @ToString(exclude = "password")
    public static class PrivilegedRevealReq {

        @NotNull(message = "敏感字段不能为空")
        private MerchantSensitiveField field;

        @NotBlank(message = "查看理由不能为空")
        @Size(max = 255, message = "查看理由不能超过 255 个字符")
        private String reason;

        @NotBlank(message = "当前密码不能为空")
        @Size(max = 4096, message = "当前密码密文无效")
        private String password;
    }

    public record PrivilegedRevealResp(MerchantSensitiveField field, String value, LocalDateTime revealedAt) {

        @Override
        public String toString() {
            return "PrivilegedRevealResp[field=%s, value=<redacted>, revealedAt=%s]".formatted(field, revealedAt);
        }
    }
}
