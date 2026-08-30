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

package top.continew.admin.merchant.master.application;

import top.continew.admin.merchant.master.domain.MerchantStatus;
import top.continew.admin.merchant.master.domain.MerchantType;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Combined merchant filters with bounded deterministic pagination. */
public record MerchantListQuery(Long merchantId, String merchantNo, String loginAccount, String legalName,
                                String shortName, String contact, String legalRepresentative, MerchantType merchantType,
                                Long owningAgentId, String channelCode, List<String> applicationStatuses,
                                LocalDateTime applicationUpdatedTo, MerchantStatus status, LocalDateTime createdFrom,
                                LocalDateTime createdTo, int page, int size, String ipAddress) {

    private static final Set<String> APPLICATION_STATUSES = Set
        .of("DRAFT", "SUBMITTED", "SUPPLEMENT_REQUIRED", "APPROVED", "REJECTED", "CHANNEL_PROCESSING", "SUCCEEDED", "FAILED");

    public MerchantListQuery {
        requirePositive(merchantId, "merchantId");
        requirePositive(owningAgentId, "owningAgentId");
        merchantNo = normalize(merchantNo, 64, "merchantNo");
        loginAccount = normalize(loginAccount, 64, "loginAccount");
        legalName = normalize(legalName, 200, "legalName");
        shortName = normalize(shortName, 100, "shortName");
        contact = normalize(contact, 100, "contact");
        legalRepresentative = normalize(legalRepresentative, 100, "legalRepresentative");
        channelCode = normalize(channelCode, 64, "channelCode");
        applicationStatuses = applicationStatuses == null ? List.of() : applicationStatuses.stream().map(statusValue -> statusValue
            .trim()
            .toUpperCase(Locale.ROOT)).filter(statusValue -> !statusValue.isEmpty()).distinct().sorted().toList();
        if (!APPLICATION_STATUSES.containsAll(applicationStatuses)) {
            throw new IllegalArgumentException("Onboarding application status filter is invalid");
        }
        if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
            throw new IllegalArgumentException("Merchant creation range is invalid");
        }
        if (page < 1 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Merchant page parameters are invalid");
        }
        ipAddress = normalize(ipAddress, 128, "ipAddress");
    }

    public MerchantListQuery(Long merchantId,
                             String merchantNo,
                             String loginAccount,
                             String legalName,
                             String shortName,
                             String contact,
                             String legalRepresentative,
                             MerchantType merchantType,
                             Long owningAgentId,
                             String channelCode,
                             MerchantStatus status,
                             LocalDateTime createdFrom,
                             LocalDateTime createdTo,
                             int page,
                             int size,
                             String ipAddress) {
        this(merchantId, merchantNo, loginAccount, legalName, shortName, contact, legalRepresentative, merchantType, owningAgentId, channelCode, List
            .of(), null, status, createdFrom, createdTo, page, size, ipAddress);
    }

    public static List<String> parseApplicationStatuses(String value) {
        return value == null || value.isBlank()
            ? List.of()
            : Arrays.stream(value.split(",")).toList();
    }

    private static void requirePositive(Long value, String name) {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static String normalize(String value, int maximumLength, String name) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException(name + " is too long");
        }
        return normalized;
    }
}
