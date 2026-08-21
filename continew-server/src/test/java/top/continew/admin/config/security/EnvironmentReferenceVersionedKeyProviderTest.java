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

package top.continew.admin.config.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import top.continew.admin.merchant.security.crypto.AesGcmSensitiveDataCipher;
import top.continew.admin.merchant.security.crypto.HmacSha256KeyedHashService;
import top.continew.admin.merchant.security.crypto.SensitiveDataProtectionException;
import top.continew.admin.merchant.security.crypto.SensitiveValueProtector;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EnvironmentReferenceVersionedKeyProviderTest {

    @Test
    void resolvesOnlyReferencedBase64EnvironmentMaterial() {
        MockEnvironment environment = new MockEnvironment().withProperty("MERCHANT_TEST_DATA_KEY", Base64.getEncoder()
            .encodeToString(new byte[32]))
            .withProperty("MERCHANT_TEST_HASH_KEY", Base64.getEncoder().encodeToString(new byte[32]));
        EnvironmentReferenceVersionedKeyProvider provider = new EnvironmentReferenceVersionedKeyProvider(environment, "env://MERCHANT_TEST_DATA_KEY", "env://MERCHANT_TEST_HASH_KEY");
        SensitiveValueProtector protector = new SensitiveValueProtector(new AesGcmSensitiveDataCipher(provider), new HmacSha256KeyedHashService(provider));

        SensitiveValueProtector.ProtectedData protectedData = protector
            .protect("MOBILE_NUMBER", "13800138000", "138****8000");

        assertEquals("env://MERCHANT_TEST_DATA_KEY", protectedData.keyVersion());
        assertEquals("13800138000", protector.reveal("MOBILE_NUMBER", protectedData));
    }

    @Test
    void unsupportedProviderReferenceFailsWithoutLeakingReferenceOrMaterial() {
        EnvironmentReferenceVersionedKeyProvider provider = new EnvironmentReferenceVersionedKeyProvider(new MockEnvironment(), "kms://merchant/data-key/v1", "kms://merchant/hash-key/v1");

        SensitiveDataProtectionException exception = assertThrows(SensitiveDataProtectionException.class, provider::currentDataKey);

        assertFalse(exception.getMessage().contains("kms://"));
    }
}
