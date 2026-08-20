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
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionSecretEnvironmentPostProcessorTest {

    private final ProductionSecretEnvironmentPostProcessor validator = new ProductionSecretEnvironmentPostProcessor();

    @Test
    void ignoresNonProductionProfiles() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        assertDoesNotThrow(() -> validator.postProcessEnvironment(environment, application()));
    }

    @Test
    void rejectsRepositoryDefaultsWithoutEchoingSecret() {
        MockEnvironment environment = validProductionEnvironment();
        environment.setProperty("sa-token.jwt-secret-key", "asdasdasifhueuiwyurfewbfjsdafjk");

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> validator
            .postProcessEnvironment(environment, application()));

        assertTrue(exception.getMessage().contains("sa-token.jwt-secret-key"));
        assertFalse(exception.getMessage().contains("asdasdasifhueuiwyurfewbfjsdafjk"));
    }

    @Test
    void requiresScheduleSecretsOnlyWhenScheduleIsEnabled() {
        MockEnvironment environment = validProductionEnvironment();
        environment.setProperty("snail-job.enabled", "true");
        environment.setProperty("snail-job.token", "");
        environment.setProperty("snail-job.server.api.password", "admin");

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> validator
            .postProcessEnvironment(environment, application()));

        assertTrue(exception.getMessage().contains("snail-job.token"));
        assertTrue(exception.getMessage().contains("snail-job.server.api.password"));
    }

    @Test
    void acceptsExternalProductionSecretsAndReferences() {
        MockEnvironment environment = validProductionEnvironment();
        assertDoesNotThrow(() -> validator.postProcessEnvironment(environment, application()));
    }

    private MockEnvironment validProductionEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        environment.setProperty("spring.datasource.password", "database-password-from-vault");
        environment.setProperty("spring.data.redis.password", "redis-password-from-vault");
        environment.setProperty("continew-starter.encrypt.field.password", "field-encryption-key-material");
        environment
            .setProperty("continew-starter.encrypt.field.public-key", "public-key-material-from-approved-secret-manager-"
                .repeat(3));
        environment
            .setProperty("continew-starter.encrypt.field.private-key", "private-key-material-from-approved-secret-manager-"
                .repeat(4));
        environment.setProperty("sa-token.jwt-secret-key", "jwt-secret-from-approved-secret-manager-0123456789");
        environment.setProperty("merchant.security.data-key-ref", "kms://merchant/data-key/v1");
        environment.setProperty("merchant.security.hash-key-ref", "kms://merchant/hash-key/v1");
        environment.setProperty("merchant.channel.signing-key-ref", "kms://channel/signing-key/v1");
        environment.setProperty("merchant.channel.encryption-key-ref", "kms://channel/encryption-key/v1");
        environment.setProperty("snail-job.enabled", "false");
        return environment;
    }

    private SpringApplication application() {
        return new SpringApplication(Object.class);
    }
}
