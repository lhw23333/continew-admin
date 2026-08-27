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

import java.net.URI;
import java.time.Instant;

/** Generated action link and PNG QR code; secret-bearing values are redacted from diagnostics. */
public record OnboardingProcessReference(Long merchantId, Long applicationId, String channelCode,
                                         ChannelSigningAction action, URI processUrl, String qrCodeMediaType,
                                         String qrCodeBase64, Instant expiresAt) {

    public OnboardingProcessReference {
        if (merchantId == null || merchantId <= 0 || applicationId == null || applicationId <= 0 || channelCode == null || action == null || processUrl == null || !"https"
            .equalsIgnoreCase(processUrl.getScheme()) || processUrl.getHost() == null || processUrl
                .getUserInfo() != null || !"image/png".equals(qrCodeMediaType) || qrCodeBase64 == null || qrCodeBase64
                    .isBlank() || qrCodeBase64.length() > 2_000_000 || expiresAt == null) {
            throw new IllegalArgumentException("Onboarding process reference is invalid");
        }
    }

    @Override
    public String toString() {
        return "OnboardingProcessReference[merchantId=%s, applicationId=%s, channelCode=%s, action=%s, " + "processUrl=<redacted>, qrCodeMediaType=%s, qrCodeBase64=<redacted>, expiresAt=%s]"
            .formatted(merchantId, applicationId, channelCode, action, qrCodeMediaType, expiresAt);
    }
}
