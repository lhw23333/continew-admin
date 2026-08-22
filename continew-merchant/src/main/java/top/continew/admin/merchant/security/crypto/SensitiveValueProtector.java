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

package top.continew.admin.merchant.security.crypto;

import java.util.Arrays;
import java.util.Objects;

/** Coordinates encryption, keyed hashing, and masked display construction. */
public final class SensitiveValueProtector {

    private final SensitiveDataCipher cipher;
    private final KeyedHashService hashService;

    public SensitiveValueProtector(SensitiveDataCipher cipher, KeyedHashService hashService) {
        this.cipher = Objects.requireNonNull(cipher, "cipher");
        this.hashService = Objects.requireNonNull(hashService, "hashService");
    }

    public ProtectedData protect(String purpose, String normalizedValue, String maskedValue) {
        SensitiveDataCipher.EncryptedData encrypted = cipher.encrypt(purpose, normalizedValue);
        KeyedHashService.HashValue hash = hashService.hash(purpose, normalizedValue);
        return new ProtectedData(encrypted.ciphertext(), encrypted.keyVersion(), hash.value(), hash
            .keyVersion(), maskedValue);
    }

    public String reveal(String purpose, ProtectedData protectedData) {
        return cipher.decrypt(purpose, new SensitiveDataCipher.EncryptedData(protectedData.ciphertext(), protectedData
            .keyVersion()));
    }

    /** Encrypts a structured sensitive payload without creating a searchable hash or display value. */
    public SensitiveDataCipher.EncryptedData encryptPayload(String purpose, String normalizedPayload) {
        return cipher.encrypt(purpose, normalizedPayload);
    }

    /** Decrypts a structured sensitive payload for an explicitly authorized domain operation. */
    public String decryptPayload(String purpose, SensitiveDataCipher.EncryptedData encryptedData) {
        return cipher.decrypt(purpose, encryptedData);
    }

    public record ProtectedData(byte[] ciphertext, String keyVersion, String normalizedHash, String hashKeyVersion,
                                String maskedValue) {
        public ProtectedData {
            ciphertext = Arrays.copyOf(ciphertext, ciphertext.length);
            requireText(keyVersion, "keyVersion");
            requireText(normalizedHash, "normalizedHash");
            requireText(hashKeyVersion, "hashKeyVersion");
            requireText(maskedValue, "maskedValue");
        }

        @Override
        public byte[] ciphertext() {
            return Arrays.copyOf(ciphertext, ciphertext.length);
        }

        private static void requireText(String value, String name) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(name + " must not be blank");
            }
        }
    }
}
