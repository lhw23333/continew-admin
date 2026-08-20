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

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Fails production startup before bean creation when required secrets are missing or use repository/example values.
 */
public class ProductionSecretEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final List<SecretRequirement> REQUIRED = List
        .of(new SecretRequirement("spring.datasource.password", 8), new SecretRequirement("spring.data.redis.password", 8), new SecretRequirement("continew-starter.encrypt.field.password", 16), new SecretRequirement("continew-starter.encrypt.field.public-key", 64), new SecretRequirement("continew-starter.encrypt.field.private-key", 128), new SecretRequirement("sa-token.jwt-secret-key", 32), new SecretRequirement("merchant.security.data-key-ref", 8), new SecretRequirement("merchant.security.hash-key-ref", 8), new SecretRequirement("merchant.channel.signing-key-ref", 8), new SecretRequirement("merchant.channel.encryption-key-ref", 8));

    private static final List<SecretRequirement> SCHEDULE_REQUIRED = List
        .of(new SecretRequirement("snail-job.token", 16), new SecretRequirement("snail-job.server.api.password", 16));

    private static final Set<String> KNOWN_PLACEHOLDERS = Set
        .of("123456", "admin", "password", "secret", "abcdefghijklmnop", "asdasdasifhueuiwyurfewbfjsdafjk", "sj_wyz3dmsdbdokdujotssobjgqp1bmsvnj", "changeme", "change-me", "replace-me");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }
        List<String> invalid = new ArrayList<>();
        REQUIRED.forEach(requirement -> validate(environment, requirement, invalid));
        if (environment.getProperty("snail-job.enabled", Boolean.class, false)) {
            SCHEDULE_REQUIRED.forEach(requirement -> validate(environment, requirement, invalid));
        }
        if (!invalid.isEmpty()) {
            invalid.sort(Comparator.naturalOrder());
            throw new IllegalStateException("Production secret validation failed for properties: " + String
                .join(", ", invalid));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    private void validate(ConfigurableEnvironment environment, SecretRequirement requirement, List<String> invalid) {
        String value;
        try {
            value = environment.getProperty(requirement.property());
        } catch (IllegalArgumentException ex) {
            invalid.add(requirement.property());
            return;
        }
        if (isInvalid(value, requirement.minimumLength())) {
            invalid.add(requirement.property());
        }
    }

    private boolean isInvalid(String value, int minimumLength) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String trimmed = value.trim();
        String normalized = trimmed.toLowerCase(Locale.ROOT);
        return trimmed.length() < minimumLength || trimmed.contains("${") || trimmed.contains("********") || trimmed
            .contains("你的") || normalized.contains("example") || normalized
                .contains("placeholder") || KNOWN_PLACEHOLDERS.contains(normalized);
    }

    private record SecretRequirement(String property, int minimumLength) {
    }
}
