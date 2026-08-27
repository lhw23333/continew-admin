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

package top.continew.admin.config.log;

import cn.hutool.core.util.URLUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Sanitizes generic HTTP log data immediately before persistence. */
@Component
@RequiredArgsConstructor
public class SensitiveEndpointLogSanitizer {

    private static final String REDACTED = "[REDACTED]";
    private static final List<String> OMIT_BODY_PATTERNS = List
        .of("/auth/login", "/user/profile/password", "/user/profile/phone", "/user/profile/email", "/system/user/*/password", "/merchant/**/sensitive/**", "/merchant/**/kyc/**", "/merchant/**/onboarding/**", "/merchant/**/onboarding-drafts/**", "/merchant/**/attachment/**", "/merchant/**/attachments/**", "/merchant/**/channel/**", "/merchant/**/export", "/merchant/**/export/**", "/channel/**", "/system/file/**", "/common/file", "/file/**", "/system/user/import/**", "/**/export", "/**/export/**");
    private static final Set<String> SENSITIVE_JSON_FIELDS = Set
        .of("password", "oldpassword", "newpassword", "paymentpassword", "credential", "secret", "secretkey", "privatekey", "token", "accesstoken", "refreshtoken", "authorization", "identitynumber", "idcard", "legalidentifier", "bankaccount", "bankaccountnumber", "accountnumber", "mobile", "mobilenumber", "phone", "reservedmobile", "kyc", "kycjson", "channelpayload", "attachmenturl");
    private static final Set<String> SENSITIVE_HEADERS = Set
        .of("authorization", "proxyauthorization", "cookie", "setcookie", "xapikey", "xauthtoken", "xsignature", "xchannelsignature", "xchannelnonce");

    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public String sanitizeBody(String requestUrl, String body) {
        if (body == null || body.isBlank()) {
            return body;
        }
        String path = URLUtil.getPath(requestUrl);
        if (OMIT_BODY_PATTERNS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path))) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            sanitizeNode(root);
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException ex) {
            return sanitizeText(body);
        }
    }

    public Map<String, String> sanitizeHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }
        Map<String, String> sanitized = new LinkedHashMap<>(headers.size());
        headers.forEach((name, value) -> sanitized.put(name, isSensitiveHeader(name) ? REDACTED : value));
        return sanitized;
    }

    public String sanitizeText(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return value.replaceAll("(?<!\\d)\\d{7,}(?!\\d)", REDACTED)
            .replaceAll("(?i)(password|secret|token|authorization)\\s*[=:]\\s*[^,;\\s]+", "$1=" + REDACTED);
    }

    private void sanitizeNode(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode)node;
            objectNode.fieldNames().forEachRemaining(fieldName -> {
                if (SENSITIVE_JSON_FIELDS.contains(normalize(fieldName))) {
                    objectNode.put(fieldName, REDACTED);
                } else {
                    sanitizeNode(objectNode.get(fieldName));
                }
            });
        } else if (node.isArray()) {
            node.forEach(this::sanitizeNode);
        }
    }

    private boolean isSensitiveHeader(String headerName) {
        String normalized = normalize(headerName);
        return SENSITIVE_HEADERS.contains(normalized) || normalized.contains("secret") || normalized.endsWith("token");
    }

    private String normalize(String value) {
        return value == null ? "" : value.replace("-", "").replace("_", "").toLowerCase(Locale.ROOT);
    }
}
