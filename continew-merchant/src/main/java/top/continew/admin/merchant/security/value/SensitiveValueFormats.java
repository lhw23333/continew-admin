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

import java.util.Locale;

final class SensitiveValueFormats {

    private SensitiveValueFormats() {
    }

    static String normalizeIdentity(String raw) {
        String normalized = requireRaw(raw).replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        if (!normalized.matches("[0-9A-Z]{8,32}")) {
            throw new IllegalArgumentException("Identity number format is invalid");
        }
        return normalized;
    }

    static String normalizeBankAccount(String raw) {
        String normalized = requireRaw(raw).replaceAll("[\\s-]", "");
        if (!normalized.matches("[0-9]{8,32}")) {
            throw new IllegalArgumentException("Bank account format is invalid");
        }
        return normalized;
    }

    static String normalizeMobile(String raw) {
        String normalized = requireRaw(raw).replaceAll("[\\s()+-]", "");
        if (normalized.startsWith("86") && normalized.length() == 13) {
            normalized = normalized.substring(2);
        }
        if (!normalized.matches("1[3-9][0-9]{9}")) {
            throw new IllegalArgumentException("Mobile number format is invalid");
        }
        return normalized;
    }

    static String mask(String normalized, int visiblePrefix, int visibleSuffix) {
        int hiddenLength = normalized.length() - visiblePrefix - visibleSuffix;
        if (hiddenLength <= 0) {
            throw new IllegalArgumentException("Sensitive value is too short to mask");
        }
        return normalized.substring(0, visiblePrefix) + "*".repeat(hiddenLength) + normalized.substring(normalized
            .length() - visibleSuffix);
    }

    private static String requireRaw(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Sensitive value must not be blank");
        }
        return raw.trim();
    }
}
