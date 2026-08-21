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
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.merchant.kyc.attachment.KycAttachment;
import top.continew.admin.merchant.kyc.attachment.KycAttachmentService;
import top.continew.admin.merchant.kyc.attachment.KycAttachmentUploadCommand;
import top.continew.admin.merchant.kyc.attachment.PrivateObjectStoragePort;
import top.continew.starter.log.annotation.Log;

import java.io.IOException;
import java.time.LocalDateTime;

/** Private KYC attachment upload and temporary-access API. */
@Tag(name = "KYC 私有附件 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/merchant/kyc")
public class KycAttachmentController {

    private final KycAttachmentService attachmentService;

    @Log(ignore = true)
    @Operation(summary = "上传 KYC 私有附件", description = "无扫描器时附件进入隔离状态且不可访问")
    @SaCheckPermission("merchant:kyc:attachment:upload")
    @PostMapping("/versions/{kycVersionId}/attachments")
    public KycAttachmentResp upload(@PathVariable Long kycVersionId,
                                    @RequestParam @NotBlank String evidenceType,
                                    @RequestParam(required = false) Integer sort,
                                    @RequestPart @NotNull MultipartFile file) throws IOException {
        KycAttachment attachment = attachmentService.upload(new KycAttachmentUploadCommand(UserContextHolder
            .getTenantId(), UserContextHolder.getUserId(), kycVersionId, evidenceType, file.getOriginalFilename(), file
                .getContentType(), file.getBytes(), sort));
        return new KycAttachmentResp(attachment.id(), attachment.kycVersionId(), attachment.evidenceType(), attachment
            .originalName(), attachment.detectedMime(), attachment.sizeBytes(), attachment.sha256(), attachment
                .scanStatus()
                .name(), attachment.validationStatus().name());
    }

    @Log(ignore = true)
    @Operation(summary = "获取 KYC 附件临时访问地址", description = "仅已通过内容校验和恶意文件扫描的附件可访问")
    @SaCheckPermission("merchant:kyc:attachment:view")
    @GetMapping("/attachments/{attachmentId}/access")
    public TemporaryAccessResp access(@PathVariable Long attachmentId,
                                      HttpServletRequest request,
                                      HttpServletResponse response) {
        applyNoStoreHeaders(response);
        PrivateObjectStoragePort.TemporaryAccess access = attachmentService.createTemporaryAccess(UserContextHolder
            .getTenantId(), UserContextHolder.getUserId(), attachmentId, JakartaServletUtil.getClientIP(request));
        return new TemporaryAccessResp(access.url(), access.expiresAt());
    }

    private void applyNoStoreHeaders(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, max-age=0, must-revalidate, private");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        response.setDateHeader(HttpHeaders.EXPIRES, 0);
        response.setHeader("Referrer-Policy", "no-referrer");
    }

    public record KycAttachmentResp(Long id, Long kycVersionId, String evidenceType, String originalName,
                                    String detectedMime, Long sizeBytes, String sha256, String scanStatus,
                                    String validationStatus) {
    }

    public record TemporaryAccessResp(String url, LocalDateTime expiresAt) {
        @Override
        public String toString() {
            return "TemporaryAccessResp[url=<redacted>, expiresAt=%s]".formatted(expiresAt);
        }
    }
}
