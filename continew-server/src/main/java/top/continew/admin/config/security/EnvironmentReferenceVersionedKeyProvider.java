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

import org.springframework.core.env.Environment;
import top.continew.admin.merchant.security.crypto.SensitiveDataProtectionException;
import top.continew.admin.merchant.security.crypto.VersionedKeyProvider;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

/** Resolves versioned key references of the form {@code env://VARIABLE_NAME}. */
public final class EnvironmentReferenceVersionedKeyProvider implements VersionedKeyProvider {

    private static final String ENV_PREFIX = "env://";

    private final Environment environment;
    private final String currentDataKeyReference;
    private final String currentHashKeyReference;

    public EnvironmentReferenceVersionedKeyProvider(Environment environment,
                                                    String currentDataKeyReference,
                                                    String currentHashKeyReference) {
        this.environment = Objects.requireNonNull(environment, "environment");
        this.currentDataKeyReference = normalize(currentDataKeyReference);
        this.currentHashKeyReference = normalize(currentHashKeyReference);
    }

    @Override
    public VersionedKey currentDataKey() {
        return resolve(requireConfigured(currentDataKeyReference), "AES", true);
    }

    @Override
    public VersionedKey dataKey(String version) {
        return resolve(version, "AES", true);
    }

    @Override
    public VersionedKey currentHashKey() {
        return resolve(requireConfigured(currentHashKeyReference), "HmacSHA256", false);
    }

    private VersionedKey resolve(String reference, String algorithm, boolean aesLengthRequired) {
        if (reference == null || !reference.startsWith(ENV_PREFIX) || reference.length() <= ENV_PREFIX.length()) {
            throw unavailable();
        }
        String encoded = environment.getProperty(reference.substring(ENV_PREFIX.length()));
        if (encoded == null || encoded.isBlank()) {
            throw unavailable();
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(encoded.trim());
        } catch (IllegalArgumentException ex) {
            throw unavailable();
        }
        try {
            if ((aesLengthRequired && keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) || (!aesLengthRequired && keyBytes.length < 32)) {
                throw unavailable();
            }
            SecretKey key = new SecretKeySpec(keyBytes, algorithm);
            return new VersionedKey(reference, key);
        } finally {
            Arrays.fill(keyBytes, (byte)0);
        }
    }

    private String requireConfigured(String reference) {
        if (reference == null) {
            throw unavailable();
        }
        return reference;
    }

    private String normalize(String reference) {
        return reference == null || reference.isBlank() ? null : reference.trim();
    }

    private SensitiveDataProtectionException unavailable() {
        return new SensitiveDataProtectionException("Sensitive key material is unavailable", new IllegalStateException());
    }
}
