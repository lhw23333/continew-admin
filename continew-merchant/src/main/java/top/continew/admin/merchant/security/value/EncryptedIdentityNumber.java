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

/** Encrypted identity/legal-subject number with normalized lookup hash and masked display. */
public final class EncryptedIdentityNumber extends AbstractEncryptedValue {

    private static final String PURPOSE = "IDENTITY_NUMBER";

    private EncryptedIdentityNumber(SensitiveValueProtector.ProtectedData protectedData) {
        super(protectedData);
    }

    public static EncryptedIdentityNumber fromPlaintext(String raw, SensitiveValueProtector protector) {
        String normalized = SensitiveValueFormats.normalizeIdentity(raw);
        return new EncryptedIdentityNumber(protector.protect(PURPOSE, normalized, SensitiveValueFormats
            .mask(normalized, 3, 4)));
    }

    public static EncryptedIdentityNumber restore(byte[] ciphertext,
                                                  String keyVersion,
                                                  String normalizedHash,
                                                  String hashKeyVersion,
                                                  String maskedValue) {
        return new EncryptedIdentityNumber(new SensitiveValueProtector.ProtectedData(ciphertext, keyVersion, normalizedHash, hashKeyVersion, maskedValue));
    }

    public String reveal(SensitiveValueProtector protector) {
        return super.reveal(protector, PURPOSE);
    }
}
