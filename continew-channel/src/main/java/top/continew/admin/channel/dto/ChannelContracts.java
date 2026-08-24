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

package top.continew.admin.channel.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;

final class ChannelContracts {
    private static final Pattern CODE = Pattern.compile("[A-Z0-9][A-Z0-9._-]{0,63}");
    private static final Pattern REFERENCE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9:._-]{0,190}");

    private ChannelContracts() {
    }

    static Long positive(Long value, String name) {
        if (value == null || value <= 0)
            throw invalid(name);
        return value;
    }

    static String code(String value, String name) {
        String normalized = value == null ? null : value.trim().toUpperCase(java.util.Locale.ROOT);
        if (normalized == null || !CODE.matcher(normalized).matches())
            throw invalid(name);
        return normalized;
    }

    static String reference(String value, String name) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || !REFERENCE.matcher(normalized).matches())
            throw invalid(name);
        return normalized;
    }

    static String optionalText(String value, int maxLength, String name) {
        if (value == null || value.isBlank())
            return null;
        String normalized = value.trim();
        if (normalized.length() > maxLength || normalized.chars().anyMatch(Character::isISOControl))
            throw invalid(name);
        return normalized;
    }

    static BigDecimal nonNegative(BigDecimal value, String name) {
        if (value == null || value.signum() < 0 || value.scale() > 8)
            throw invalid(name);
        return value.stripTrailingZeros();
    }

    static BigDecimal positive(BigDecimal value, String name) {
        BigDecimal normalized = nonNegative(value, name);
        if (normalized.signum() == 0)
            throw invalid(name);
        return normalized;
    }

    static List<Long> positiveIds(List<Long> values, int maxSize, String name) {
        if (values == null || values.size() > maxSize || values.stream()
            .anyMatch(value -> value == null || value <= 0)) {
            throw invalid(name);
        }
        List<Long> normalized = values.stream().distinct().toList();
        if (normalized.size() != values.size())
            throw invalid(name);
        return normalized;
    }

    static IllegalArgumentException invalid(String name) {
        return new IllegalArgumentException(name + " is invalid");
    }
}
