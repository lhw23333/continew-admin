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

package top.continew.admin.merchant.security.value;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.continew.admin.merchant.security.crypto.AesGcmSensitiveDataCipher;
import top.continew.admin.merchant.security.crypto.HmacSha256KeyedHashService;
import top.continew.admin.merchant.security.crypto.SensitiveValueProtector;
import top.continew.admin.merchant.security.crypto.VersionedKeyProvider;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EncryptedSensitiveValueTest {

    private SensitiveValueProtector protector;

    @BeforeEach
    void setUp() {
        VersionedKeyProvider keyProvider = new TestKeyProvider();
        protector = new SensitiveValueProtector(new AesGcmSensitiveDataCipher(keyProvider), new HmacSha256KeyedHashService(keyProvider));
    }

    @Test
    void identityNumberIsNormalizedEncryptedHashedMaskedAndRestorable() {
        EncryptedIdentityNumber first = EncryptedIdentityNumber.fromPlaintext(" 110105-19491231002x ", protector);
        EncryptedIdentityNumber second = EncryptedIdentityNumber.fromPlaintext("11010519491231002X", protector);

        assertEquals("110***********002X", first.maskedValue());
        assertEquals("data-v1", first.keyVersion());
        assertEquals("hash-v1", first.hashKeyVersion());
        assertEquals(64, first.normalizedHash().length());
        assertEquals(first.normalizedHash(), second.normalizedHash());
        assertNotEquals(Arrays.toString(first.ciphertext()), Arrays.toString(second.ciphertext()));
        assertEquals("11010519491231002X", first.reveal(protector));
        assertFalse(new String(first.ciphertext(), StandardCharsets.UTF_8).contains("11010519491231002X"));
        assertEquals(first.maskedValue(), first.toString());

        EncryptedIdentityNumber restored = EncryptedIdentityNumber.restore(first.ciphertext(), first.keyVersion(), first
            .normalizedHash(), first.hashKeyVersion(), first.maskedValue());
        assertEquals(first, restored);
        assertEquals("11010519491231002X", restored.reveal(protector));
    }

    @Test
    void bankAccountAndMobileUseIndependentPurposeBoundHashes() {
        EncryptedBankAccount bank = EncryptedBankAccount.fromPlaintext("6222 0200 1234 5678", protector);
        EncryptedMobileNumber mobile = EncryptedMobileNumber.fromPlaintext("+86 138-0013-8000", protector);
        EncryptedIdentityNumber sameDigitsAsBank = EncryptedIdentityNumber.fromPlaintext("6222020012345678", protector);

        assertEquals("6222********5678", bank.maskedValue());
        assertEquals("6222020012345678", bank.reveal(protector));
        assertEquals("138****8000", mobile.maskedValue());
        assertEquals("13800138000", mobile.reveal(protector));
        assertNotEquals(bank.normalizedHash(), sameDigitsAsBank.normalizedHash());
    }

    @Test
    void ciphertextAccessIsDefensiveAndInvalidInputIsRejected() {
        EncryptedMobileNumber mobile = EncryptedMobileNumber.fromPlaintext("13800138000", protector);
        byte[] original = mobile.ciphertext();
        byte[] modified = mobile.ciphertext();
        modified[0] ^= 1;
        assertArrayEquals(original, mobile.ciphertext());

        assertThrows(IllegalArgumentException.class, () -> EncryptedIdentityNumber
            .fromPlaintext("bad identity!", protector));
        assertThrows(IllegalArgumentException.class, () -> EncryptedBankAccount.fromPlaintext("1234abcd", protector));
        assertThrows(IllegalArgumentException.class, () -> EncryptedMobileNumber.fromPlaintext("10086", protector));
    }

    private static final class TestKeyProvider implements VersionedKeyProvider {

        private final VersionedKey dataKey = new VersionedKey("data-v1", new SecretKeySpec("0123456789abcdef"
            .getBytes(StandardCharsets.UTF_8), "AES"));
        private final VersionedKey hashKey = new VersionedKey("hash-v1", new SecretKeySpec("merchant-hash-key-material-32bytes"
            .getBytes(StandardCharsets.UTF_8), "HmacSHA256"));

        @Override
        public VersionedKey currentDataKey() {
            return dataKey;
        }

        @Override
        public VersionedKey dataKey(String version) {
            if (!dataKey.version().equals(version)) {
                throw new IllegalArgumentException("Unknown data key version");
            }
            return dataKey;
        }

        @Override
        public VersionedKey currentHashKey() {
            return hashKey;
        }
    }
}
