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

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;

/** AES-GCM cipher using a random 96-bit IV and a 128-bit authentication tag. */
public final class AesGcmSensitiveDataCipher implements SensitiveDataCipher {

    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private final VersionedKeyProvider keyProvider;
    private final SecureRandom secureRandom;

    public AesGcmSensitiveDataCipher(VersionedKeyProvider keyProvider) {
        this(keyProvider, new SecureRandom());
    }

    AesGcmSensitiveDataCipher(VersionedKeyProvider keyProvider, SecureRandom secureRandom) {
        this.keyProvider = Objects.requireNonNull(keyProvider, "keyProvider");
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
    }

    @Override
    public EncryptedData encrypt(String purpose, String normalizedPlaintext) {
        requireText(purpose, "purpose");
        requireText(normalizedPlaintext, "normalizedPlaintext");
        VersionedKeyProvider.VersionedKey versionedKey = keyProvider.currentDataKey();
        byte[] iv = new byte[IV_LENGTH_BYTES];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, versionedKey.key(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            cipher.updateAAD(purpose.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(normalizedPlaintext.getBytes(StandardCharsets.UTF_8));
            return new EncryptedData(ByteBuffer.allocate(iv.length + encrypted.length)
                .put(iv)
                .put(encrypted)
                .array(), versionedKey.version());
        } catch (GeneralSecurityException ex) {
            throw new SensitiveDataProtectionException("Sensitive value encryption failed", ex);
        } finally {
            Arrays.fill(iv, (byte)0);
        }
    }

    @Override
    public String decrypt(String purpose, EncryptedData encryptedData) {
        requireText(purpose, "purpose");
        Objects.requireNonNull(encryptedData, "encryptedData");
        byte[] payload = encryptedData.ciphertext();
        if (payload.length <= IV_LENGTH_BYTES) {
            throw new IllegalArgumentException("Ciphertext payload is invalid");
        }
        byte[] iv = Arrays.copyOfRange(payload, 0, IV_LENGTH_BYTES);
        byte[] encrypted = Arrays.copyOfRange(payload, IV_LENGTH_BYTES, payload.length);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keyProvider.dataKey(encryptedData.keyVersion())
                .key(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            cipher.updateAAD(purpose.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException ex) {
            throw new SensitiveDataProtectionException("Sensitive value decryption failed", ex);
        } finally {
            Arrays.fill(payload, (byte)0);
            Arrays.fill(iv, (byte)0);
            Arrays.fill(encrypted, (byte)0);
        }
    }

    private void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
