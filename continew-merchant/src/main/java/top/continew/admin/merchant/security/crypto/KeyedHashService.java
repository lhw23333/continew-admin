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

/** Produces a non-reversible, purpose-bound lookup hash. */
public interface KeyedHashService {

    HashValue hash(String purpose, String normalizedValue);

    record HashValue(String value, String keyVersion) {
        public HashValue {
            if (value == null || value.isBlank() || keyVersion == null || keyVersion.isBlank()) {
                throw new IllegalArgumentException("Hash value and key version must not be blank");
            }
        }
    }
}
