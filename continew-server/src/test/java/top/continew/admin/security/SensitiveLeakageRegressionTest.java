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

package top.continew.admin.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import top.continew.admin.config.log.SensitiveEndpointLogSanitizer;
import top.continew.admin.merchant.kyc.attachment.KycAttachmentAccessDeniedException;
import top.continew.admin.merchant.kyc.attachment.KycAttachmentService;
import top.continew.admin.merchant.security.crypto.AesGcmSensitiveDataCipher;
import top.continew.admin.merchant.security.crypto.HmacSha256KeyedHashService;
import top.continew.admin.merchant.security.crypto.SensitiveValueProtector;
import top.continew.admin.merchant.security.crypto.VersionedKeyProvider;
import top.continew.admin.merchant.security.reveal.PrivilegedRevealDeniedException;
import top.continew.admin.merchant.security.reveal.PrivilegedRevealService;
import top.continew.admin.merchant.security.value.EncryptedBankAccount;
import top.continew.admin.merchant.security.value.EncryptedIdentityNumber;
import top.continew.admin.merchant.security.value.EncryptedMobileNumber;
import top.continew.admin.workflow.api.InvalidWorkflowVariableException;
import top.continew.admin.workflow.api.WorkflowVariablePolicy;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SensitiveLeakageRegressionTest {

    private static final String IDENTITY = "11010519491231002X";
    private static final String BANK_ACCOUNT = "6222020200001234567";
    private static final String MOBILE = "13800138000";
    private static final String PASSWORD = "P@ssword-DoNotLeak-2026";
    private static final List<String> SENTINELS = List.of(IDENTITY, BANK_ACCOUNT, MOBILE, PASSWORD);

    @Test
    void completeSyntheticValuesStayOutOfWorkflowLogsErrorsCachesAndExports() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, String> artifacts = new LinkedHashMap<>();

        WorkflowVariablePolicy workflowPolicy = new WorkflowVariablePolicy();
        for (Map.Entry<String, String> forbidden : Map
            .of("identityNumber", IDENTITY, "bankAccountNumber", BANK_ACCOUNT, "mobile", MOBILE, "password", PASSWORD)
            .entrySet()) {
            InvalidWorkflowVariableException exception = assertThrows(InvalidWorkflowVariableException.class, () -> workflowPolicy
                .validateAndCopy(Map.of(forbidden.getKey(), forbidden.getValue())));
            artifacts.put("workflow-error-" + forbidden.getKey(), exception.getMessage());
        }
        artifacts.put("flowable-variables", objectMapper.writeValueAsString(workflowPolicy.validateAndCopy(Map
            .of("tenantId", 1001L, "merchantId", 2001L, "applicationId", 3001L, "kycVersion", 1L))));

        SensitiveEndpointLogSanitizer logSanitizer = new SensitiveEndpointLogSanitizer(objectMapper);
        String rawLogBody = objectMapper.writeValueAsString(Map
            .of("identityNumber", IDENTITY, "bankAccountNumber", BANK_ACCOUNT, "mobile", MOBILE, "password", PASSWORD));
        artifacts.put("generic-log", logSanitizer.sanitizeBody("https://localhost/profile", rawLogBody));
        artifacts.put("authentication-log", String.valueOf(logSanitizer
            .sanitizeBody("https://localhost/auth/login", rawLogBody)));
        artifacts.put("log-headers", objectMapper.writeValueAsString(logSanitizer.sanitizeHeaders(Map
            .of("Authorization", "Bearer " + PASSWORD, "X-Trace-Id", "trace-1"))));

        artifacts.put("reveal-error", new PrivilegedRevealDeniedException().getMessage());
        artifacts.put("attachment-error", new KycAttachmentAccessDeniedException().getMessage());

        SensitiveValueProtector protector = protector();
        EncryptedIdentityNumber identity = EncryptedIdentityNumber.fromPlaintext(IDENTITY, protector);
        EncryptedBankAccount bankAccount = EncryptedBankAccount.fromPlaintext(BANK_ACCOUNT, protector);
        EncryptedMobileNumber mobile = EncryptedMobileNumber.fromPlaintext(MOBILE, protector);
        Map<String, String> maskedSnapshot = Map.of("identity", identity.maskedValue(), "bankAccount", bankAccount
            .maskedValue(), "mobile", mobile.maskedValue());
        artifacts.put("cache-snapshot", objectMapper.writeValueAsString(maskedSnapshot));
        artifacts.put("export-snapshot", objectMapper.writeValueAsString(maskedSnapshot));

        assertNoCompleteSentinel(artifacts);
        assertNoCacheAnnotations(PrivilegedRevealService.class, KycAttachmentService.class);
    }

    private void assertNoCompleteSentinel(Map<String, String> artifacts) {
        artifacts.forEach((name, artifact) -> SENTINELS.forEach(sentinel -> assertFalse(artifact
            .contains(sentinel), () -> name + " contains complete sensitive sentinel")));
    }

    private void assertNoCacheAnnotations(Class<?>... types) {
        for (Class<?> type : types) {
            for (Annotation annotation : type.getAnnotations()) {
                assertFalse(isCacheAnnotation(annotation), () -> type.getName() + " has cache annotation");
            }
            for (Method method : type.getDeclaredMethods()) {
                for (Annotation annotation : method.getAnnotations()) {
                    assertFalse(isCacheAnnotation(annotation), () -> method + " has cache annotation");
                }
            }
        }
    }

    private boolean isCacheAnnotation(Annotation annotation) {
        return annotation.annotationType().getName().toLowerCase().contains("cache");
    }

    private SensitiveValueProtector protector() {
        VersionedKeyProvider provider = new VersionedKeyProvider() {
            private final SecretKey dataKey = new SecretKeySpec(new byte[32], "AES");
            private final SecretKey hashKey = new SecretKeySpec(new byte[32], "HmacSHA256");

            @Override
            public VersionedKey currentDataKey() {
                return new VersionedKey("data-v1", dataKey);
            }

            @Override
            public VersionedKey dataKey(String version) {
                return new VersionedKey(version, dataKey);
            }

            @Override
            public VersionedKey currentHashKey() {
                return new VersionedKey("hash-v1", hashKey);
            }
        };
        return new SensitiveValueProtector(new AesGcmSensitiveDataCipher(provider), new HmacSha256KeyedHashService(provider));
    }
}
