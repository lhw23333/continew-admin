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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import top.continew.admin.merchant.security.crypto.AesGcmSensitiveDataCipher;
import top.continew.admin.merchant.security.crypto.HmacSha256KeyedHashService;
import top.continew.admin.merchant.security.crypto.SensitiveValueProtector;
import top.continew.admin.merchant.security.crypto.VersionedKeyProvider;

/** Wires merchant sensitive-value protection while allowing an approved KMS/HSM provider to replace the default. */
@Configuration
public class MerchantSensitiveDataConfiguration {

    @Bean
    @ConditionalOnMissingBean(VersionedKeyProvider.class)
    public VersionedKeyProvider environmentReferenceVersionedKeyProvider(Environment environment,
                                                                         @Value("${merchant.security.data-key-ref:}") String dataKeyReference,
                                                                         @Value("${merchant.security.hash-key-ref:}") String hashKeyReference) {
        return new EnvironmentReferenceVersionedKeyProvider(environment, dataKeyReference, hashKeyReference);
    }

    @Bean
    @ConditionalOnMissingBean(SensitiveValueProtector.class)
    public SensitiveValueProtector sensitiveValueProtector(VersionedKeyProvider keyProvider) {
        return new SensitiveValueProtector(new AesGcmSensitiveDataCipher(keyProvider), new HmacSha256KeyedHashService(keyProvider));
    }
}
