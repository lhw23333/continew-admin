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

import java.time.LocalDateTime;
import java.util.Set;

/** Append-only channel product eligibility and requirement version. */
public record ChannelProductVersion(Long id, Long tenantId, ChannelProductKey key, String configVersion,
                                    String requirementVersion, Set<String> supportedMerchantTypes,
                                    ChannelRequirementSummary requirements, ChannelProductStatus status,
                                    LocalDateTime effectiveTime, LocalDateTime expiresTime, LocalDateTime createTime) {

    public ChannelProductVersion {
        if (id == null || id <= 0 || tenantId == null || tenantId <= 0 || key == null || requirements == null || status == null || effectiveTime == null || createTime == null) {
            throw new IllegalArgumentException("Required channel product fields must not be null");
        }
        configVersion = required(configVersion, "configVersion");
        requirementVersion = required(requirementVersion, "requirementVersion");
        if (supportedMerchantTypes == null || supportedMerchantTypes.isEmpty() || supportedMerchantTypes
            .size() > 20 || supportedMerchantTypes.stream()
                .anyMatch(value -> value == null || value.isBlank() || value.length() > 32)) {
            throw new IllegalArgumentException("supportedMerchantTypes is invalid");
        }
        supportedMerchantTypes = Set.copyOf(supportedMerchantTypes);
        if (expiresTime != null && !expiresTime.isAfter(effectiveTime)) {
            throw new IllegalArgumentException("Channel product expiry must be after its effective time");
        }
    }

    public boolean isEnabledFor(String merchantType) {
        return ChannelProductStatus.ENABLED.equals(status) && supportedMerchantTypes.contains(merchantType);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank() || value.trim().length() > 64) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value.trim();
    }
}
