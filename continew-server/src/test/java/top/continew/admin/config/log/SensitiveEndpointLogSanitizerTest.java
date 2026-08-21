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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SensitiveEndpointLogSanitizerTest {

    private final SensitiveEndpointLogSanitizer sanitizer = new SensitiveEndpointLogSanitizer(new ObjectMapper());

    @Test
    void omitsBodiesForSensitiveEndpointFamilies() {
        String body = "{\"password\":\"secret\",\"mobile\":\"13800138000\"}";

        assertNull(sanitizer.sanitizeBody("https://localhost/auth/login", body));
        assertNull(sanitizer.sanitizeBody("https://localhost/user/profile/password", body));
        assertNull(sanitizer.sanitizeBody("https://localhost/merchant/masters/1/sensitive/reveal", body));
        assertNull(sanitizer.sanitizeBody("https://localhost/merchant/1/kyc/versions", body));
        assertNull(sanitizer.sanitizeBody("https://localhost/channel/callback", body));
        assertNull(sanitizer.sanitizeBody("https://localhost/system/file/upload", body));
        assertNull(sanitizer.sanitizeBody("https://localhost/merchant/list/export", body));
    }

    @Test
    void recursivelyRedactsSensitiveFieldsOnOtherEndpoints() {
        String sanitized = sanitizer.sanitizeBody("https://localhost/profile", """
            {"name":"safe","nested":{"identityNumber":"110101199001011234","bankAccount":"6222020000000000"},
             "items":[{"password":"Secret123","mobile":"13800138000"}]}
            """);

        assertTrue(sanitized.contains("\"name\":\"safe\""));
        assertFalse(sanitized.contains("110101199001011234"));
        assertFalse(sanitized.contains("6222020000000000"));
        assertFalse(sanitized.contains("Secret123"));
        assertFalse(sanitized.contains("13800138000"));
    }

    @Test
    void redactsCredentialHeadersAndLongSensitiveNumbers() {
        Map<String, String> sanitizedHeaders = sanitizer.sanitizeHeaders(Map
            .of("Authorization", "Bearer secret-token", "X-Trace-Id", "trace-1", "Cookie", "session=secret"));

        assertEquals("[REDACTED]", sanitizedHeaders.get("Authorization"));
        assertEquals("[REDACTED]", sanitizedHeaders.get("Cookie"));
        assertEquals("trace-1", sanitizedHeaders.get("X-Trace-Id"));
        assertEquals("Merchant [REDACTED] failed", sanitizer.sanitizeText("Merchant 13800138000 failed"));
    }
}
