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

import top.continew.admin.merchant.security.crypto.SensitiveValueProtector;

import java.util.Arrays;
import java.util.Objects;

abstract class AbstractEncryptedValue {

    private final byte[] ciphertext;
    private final String keyVersion;
    private final String normalizedHash;
    private final String hashKeyVersion;
    private final String maskedValue;

    protected AbstractEncryptedValue(SensitiveValueProtector.ProtectedData protectedData) {
        this.ciphertext = protectedData.ciphertext();
        this.keyVersion = protectedData.keyVersion();
        this.normalizedHash = protectedData.normalizedHash();
        this.hashKeyVersion = protectedData.hashKeyVersion();
        this.maskedValue = protectedData.maskedValue();
    }

    public byte[] ciphertext() {
        return Arrays.copyOf(ciphertext, ciphertext.length);
    }

    public String keyVersion() {
        return keyVersion;
    }

    public String normalizedHash() {
        return normalizedHash;
    }

    public String hashKeyVersion() {
        return hashKeyVersion;
    }

    public String maskedValue() {
        return maskedValue;
    }

    protected String reveal(SensitiveValueProtector protector, String purpose) {
        return protector
            .reveal(purpose, new SensitiveValueProtector.ProtectedData(ciphertext, keyVersion, normalizedHash, hashKeyVersion, maskedValue));
    }

    @Override
    public final String toString() {
        return maskedValue;
    }

    @Override
    public final boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        AbstractEncryptedValue other = (AbstractEncryptedValue)obj;
        return Arrays.equals(ciphertext, other.ciphertext) && keyVersion.equals(other.keyVersion) && normalizedHash
            .equals(other.normalizedHash) && hashKeyVersion.equals(other.hashKeyVersion) && maskedValue
                .equals(other.maskedValue);
    }

    @Override
    public final int hashCode() {
        int result = Objects.hash(keyVersion, normalizedHash, hashKeyVersion, maskedValue);
        return 31 * result + Arrays.hashCode(ciphertext);
    }
}
