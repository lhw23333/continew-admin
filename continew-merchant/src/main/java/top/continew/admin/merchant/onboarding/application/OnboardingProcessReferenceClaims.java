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

package top.continew.admin.merchant.onboarding.application;

import top.continew.admin.channel.dto.ChannelSigningAction;

import java.time.Instant;

/** Verified ownership and action claims carried by a short-lived process reference. */
public record OnboardingProcessReferenceClaims(Long tenantId, Long merchantId, Long applicationId, String channelCode,
                                               ChannelSigningAction action, Instant issuedAt, Instant expiresAt) {

    public OnboardingProcessReferenceClaims {
        if (tenantId == null || tenantId <= 0 || merchantId == null || merchantId <= 0 || applicationId == null || applicationId <= 0 || channelCode == null || !channelCode
            .matches("[A-Z0-9][A-Z0-9._-]{0,63}") || action == null || issuedAt == null || expiresAt == null || !expiresAt
                .isAfter(issuedAt)) {
            throw new IllegalArgumentException("Onboarding process reference claims are invalid");
        }
    }
}
