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

package top.continew.admin.merchant.limit.application;

import top.continew.admin.merchant.limit.domain.LimitApprovalStatus;
import top.continew.admin.merchant.limit.domain.LimitChannelStatus;
import top.continew.admin.merchant.limit.domain.LimitEffectiveStatus;

import java.time.LocalDateTime;

/** Bounded stable filters for one authorized merchant's limit requests. */
public record LimitAdjustmentListQuery(String requestNo, String channelCode, String platformCode,
                                       LimitApprovalStatus approvalStatus, LimitChannelStatus channelStatus,
                                       LimitEffectiveStatus effectiveStatus, LocalDateTime appliedFrom,
                                       LocalDateTime appliedTo, int page, int size) {

    public LimitAdjustmentListQuery {
        requestNo = normalize(requestNo, 64, "requestNo");
        channelCode = normalize(channelCode, 64, "channelCode");
        platformCode = normalize(platformCode, 64, "platformCode");
        if (appliedFrom != null && appliedTo != null && appliedFrom.isAfter(appliedTo)) {
            throw new IllegalArgumentException("Limit application range is invalid");
        }
        if (page < 1 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Limit page parameters are invalid");
        }
    }

    private static String normalize(String value, int maximumLength, String name) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maximumLength || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Limit " + name + " is invalid");
        }
        return normalized;
    }
}