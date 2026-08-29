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

package top.continew.admin.config.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import top.continew.admin.channel.api.ChannelAdapterException;
import top.continew.admin.channel.api.ChannelConfigurationException;
import top.continew.admin.channel.api.ChannelTransportException;
import top.continew.admin.merchant.agent.domain.AgentAccessDeniedException;
import top.continew.admin.merchant.agent.domain.AgentDomainException;
import top.continew.admin.merchant.kyc.attachment.KycAttachmentAccessDeniedException;
import top.continew.admin.merchant.kyc.attachment.KycAttachmentException;
import top.continew.admin.merchant.master.domain.MerchantAccessDeniedException;
import top.continew.admin.merchant.master.domain.MerchantDomainException;
import top.continew.admin.merchant.security.crypto.SensitiveDataProtectionException;
import top.continew.admin.workflow.api.WorkflowOperationException;
import top.continew.starter.web.model.R;

/** Sanitized, actionable API errors for merchant-domain and workflow failures. */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@RestControllerAdvice
public class MerchantApiExceptionHandler {

    @ExceptionHandler({AgentAccessDeniedException.class, MerchantAccessDeniedException.class,
        KycAttachmentAccessDeniedException.class})
    public R handleAccessDenied(RuntimeException exception, HttpServletRequest request) {
        log.warn("[{}] {} merchant resource access denied", request.getMethod(), request.getRequestURI());
        return fail(HttpStatus.NOT_FOUND, "业务资源不可访问");
    }

    @ExceptionHandler({AgentDomainException.class, MerchantDomainException.class, KycAttachmentException.class})
    public R handleDomain(RuntimeException exception, HttpServletRequest request) {
        log.warn("[{}] {} merchant request rejected: {}", request.getMethod(), request
            .getRequestURI(), exception.getMessage());
        return fail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(WorkflowOperationException.class)
    public R handleWorkflow(WorkflowOperationException exception, HttpServletRequest request) {
        log.warn("[{}] {} workflow request rejected: {}", request.getMethod(), request
            .getRequestURI(), exception.code());
        return switch (exception.code()) {
            case NOT_FOUND, TENANT_MISMATCH -> fail(HttpStatus.NOT_FOUND, "工作流任务不可访问");
            case NOT_ASSIGNED -> fail(HttpStatus.CONFLICT, "任务未分配给当前用户");
            case ALREADY_CLAIMED -> fail(HttpStatus.CONFLICT, "任务已被其他用户认领");
            case MAPPING_CONFLICT, DEPLOYMENT_CONFLICT -> fail(HttpStatus.CONFLICT, "工作流状态已变化，请刷新后重试");
            case SEPARATION_OF_DUTIES -> fail(HttpStatus.CONFLICT, "申请人不能认领或审核自己的申请");
            case ENGINE_FAILURE -> fail(HttpStatus.SERVICE_UNAVAILABLE, "工作流服务暂时不可用");
            default -> fail(HttpStatus.BAD_REQUEST, "工作流请求无效");
        };
    }

    @ExceptionHandler(SensitiveDataProtectionException.class)
    public R handleSensitiveData(SensitiveDataProtectionException exception, HttpServletRequest request) {
        log.error("[{}] {} sensitive-data protection unavailable", request.getMethod(), request.getRequestURI());
        return fail(HttpStatus.SERVICE_UNAVAILABLE, "敏感数据保护服务暂时不可用");
    }

    @ExceptionHandler({ChannelAdapterException.class, ChannelConfigurationException.class, ChannelTransportException.class})
    public R handleChannel(RuntimeException exception, HttpServletRequest request) {
        log.warn("[{}] {} channel operation unavailable: {}", request.getMethod(), request
            .getRequestURI(), exception.getClass().getSimpleName());
        return fail(HttpStatus.SERVICE_UNAVAILABLE, "渠道服务暂时不可用");
    }

    private R fail(HttpStatus status, String message) {
        return R.fail(String.valueOf(status.value()), message);
    }
}
