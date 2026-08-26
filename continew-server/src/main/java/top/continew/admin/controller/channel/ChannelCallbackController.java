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

package top.continew.admin.controller.channel;

import cn.dev33.satoken.annotation.SaIgnore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.channel.api.ChannelCallbackException;
import top.continew.admin.channel.api.ChannelEventProcessingException;
import top.continew.admin.channel.dto.ChannelProductKey;
import top.continew.admin.channel.dto.RawChannelCallback;
import top.continew.admin.channel.service.ChannelCallbackVerifier;
import top.continew.admin.channel.service.ChannelEventProcessor;
import top.continew.admin.config.channel.ChannelCallbackTenantExecutor;
import top.continew.starter.log.annotation.Log;

/** Public provider callback boundary. Event persistence is wired by the next channel-processing phase. */
@SaIgnore
@Tag(name = "渠道回调 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/channel/callbacks")
@ConditionalOnProperty(prefix = "channel.callback", name = "enabled", havingValue = "true")
public class ChannelCallbackController {
    public static final String TIMESTAMP_HEADER = "X-Channel-Timestamp";
    public static final String NONCE_HEADER = "X-Channel-Nonce";
    public static final String KEY_VERSION_HEADER = "X-Channel-Key-Version";
    public static final String SIGNATURE_HEADER = "X-Channel-Signature";

    private final ChannelCallbackVerifier verifier;
    private final ChannelEventProcessor eventProcessor;
    private final ChannelCallbackTenantExecutor tenantExecutor;

    @Log(ignore = true)
    @Operation(summary = "接收并验证渠道回调", description = "验签、时间窗、密钥版本和防重放全部通过后才进入事件处理边界")
    @PostMapping("/{tenantId}/{channelCode}/{productCode}/{configVersion}")
    public ResponseEntity<CallbackAcknowledgement> receive(@PathVariable Long tenantId,
                                                           @PathVariable String channelCode,
                                                           @PathVariable String productCode,
                                                           @PathVariable String configVersion,
                                                           @RequestHeader(name = TIMESTAMP_HEADER, required = false) String timestamp,
                                                           @RequestHeader(name = NONCE_HEADER, required = false) String nonce,
                                                           @RequestHeader(name = KEY_VERSION_HEADER, required = false) String keyVersion,
                                                           @RequestHeader(name = SIGNATURE_HEADER, required = false) String signature,
                                                           @RequestBody(required = false) byte[] payload,
                                                           HttpServletRequest request) {
        tenantExecutor.execute(tenantId, () -> eventProcessor.process(verifier
            .verify(new RawChannelCallback(tenantId, new ChannelProductKey(channelCode, productCode), configVersion, timestamp, nonce, keyVersion, signature, payload, request
                .getRemoteAddr()))));
        return ResponseEntity.accepted().body(new CallbackAcknowledgement("ACCEPTED"));
    }

    @ExceptionHandler(ChannelCallbackException.class)
    public ResponseEntity<CallbackAcknowledgement> reject(ChannelCallbackException exception) {
        HttpStatus status = switch (exception.code()) {
            case CONFIGURATION_UNAVAILABLE, REPLAY_STORE_FAILED, AUDIT_FAILED -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(new CallbackAcknowledgement("REJECTED"));
    }

    @ExceptionHandler(ChannelEventProcessingException.class)
    public ResponseEntity<CallbackAcknowledgement> reject(ChannelEventProcessingException exception) {
        HttpStatus status = switch (exception.code()) {
            case CONFIGURATION_UNAVAILABLE, CONCURRENT_STATE_CHANGE, PERSISTENCE_FAILED ->
                HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(new CallbackAcknowledgement("REJECTED"));
    }

    public record CallbackAcknowledgement(String status) {
    }
}
