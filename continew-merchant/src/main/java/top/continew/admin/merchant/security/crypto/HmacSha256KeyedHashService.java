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

import javax.crypto.Mac;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Objects;

/** HMAC-SHA256 keyed hash with purpose separation. */
public final class HmacSha256KeyedHashService implements KeyedHashService {

    private static final String ALGORITHM = "HmacSHA256";

    private final VersionedKeyProvider keyProvider;

    public HmacSha256KeyedHashService(VersionedKeyProvider keyProvider) {
        this.keyProvider = Objects.requireNonNull(keyProvider, "keyProvider");
    }

    @Override
    public HashValue hash(String purpose, String normalizedValue) {
        if (purpose == null || purpose.isBlank() || normalizedValue == null || normalizedValue.isBlank()) {
            throw new IllegalArgumentException("Purpose and normalized value must not be blank");
        }
        VersionedKeyProvider.VersionedKey versionedKey = keyProvider.currentHashKey();
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(versionedKey.key());
            mac.update(purpose.getBytes(StandardCharsets.UTF_8));
            mac.update((byte)0);
            return new HashValue(HexFormat.of()
                .formatHex(mac.doFinal(normalizedValue.getBytes(StandardCharsets.UTF_8))), versionedKey.version());
        } catch (GeneralSecurityException ex) {
            throw new SensitiveDataProtectionException("Sensitive lookup hash failed", ex);
        }
    }
}
