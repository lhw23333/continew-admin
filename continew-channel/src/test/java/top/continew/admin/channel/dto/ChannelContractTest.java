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

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChannelContractTest {

    @Test
    void onboardingCommandKeepsExactVersionReferencesAndCopiesEvidenceIds() {
        List<Long> evidenceIds = new ArrayList<>(List.of(31L, 32L));
        ChannelOnboardingSubmitCommand command = new ChannelOnboardingSubmitCommand(context(ChannelBusinessType.ONBOARDING), 21L, "REQ-1", evidenceIds);
        evidenceIds.add(33L);
        assertEquals(List.of(31L, 32L), command.evidenceObjectIds());
        assertEquals(21L, command.kycVersionId());
        assertThrows(UnsupportedOperationException.class, () -> command.evidenceObjectIds().add(34L));
    }

    @Test
    void signingLinkNeverRendersTheSecretUrl() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 24, 12, 0);
        ChannelSigningLinkCommand command = new ChannelSigningLinkCommand(context(ChannelBusinessType.ONBOARDING), 3L, ChannelSigningAction.SIGN_AGREEMENT, now
            .plusMinutes(10));
        assertEquals(3L, command.merchantId());
        ChannelSigningLinkResult result = new ChannelSigningLinkResult(meta(now), ChannelSigningAction.SIGN_AGREEMENT, "https://synthetic.example/sign?token=secret", now
            .plusMinutes(10));
        assertFalse(result.toString().contains("token=secret"));
        assertThrows(IllegalArgumentException.class, () -> new ChannelSigningLinkResult(meta(now), ChannelSigningAction.SIGN_AGREEMENT, "http://synthetic.example/sign", now
            .plusMinutes(10)));
    }

    @Test
    void accountInfoRequiresMaskedAccountNumbers() {
        assertThrows(IllegalArgumentException.class, () -> new ChannelAccountInfoResult(meta(LocalDateTime
            .of(2026, 8, 24, 12, 0)), "ACCOUNT-1", "6222021234567890", "BANK", ChannelAccountStatus.ACTIVE));
    }

    @Test
    void limitCommandRequiresLimitBusinessContextAndNormalizedAmounts() {
        assertThrows(IllegalArgumentException.class, () -> new ChannelLimitAdjustmentCommand(context(ChannelBusinessType.ONBOARDING), 1L, "INBOUND", "CNY", BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.TEN, "BUSINESS_GROWTH"));
        ChannelLimitAdjustmentCommand command = new ChannelLimitAdjustmentCommand(context(ChannelBusinessType.LIMIT_ADJUSTMENT), 1L, "INBOUND", "CNY", BigDecimal.ZERO, new BigDecimal("1000"), new BigDecimal("1000"), "BUSINESS_GROWTH");
        assertEquals(new BigDecimal("1E+3"), command.normalizedLimit());
    }

    @Test
    void onboardingEventRequiresIndependentNormalizedState() {
        assertThrows(IllegalArgumentException.class, () -> new ChannelEvent(1L, "EVENT-1", ChannelEventType.STATUS_CHANGED, new ChannelProductKey("SYNTHETIC", "ONBOARDING"), ChannelBusinessType.ONBOARDING, 2L, 1L, "SERIAL-1", "REQUEST-1", "PROCESSING", "MAP-1", ChannelOperationStatus.PROCESSING, null, LocalDateTime
            .of(2026, 8, 24, 12, 0), LocalDateTime.of(2026, 8, 24, 12, 1)));
    }

    private ChannelCommandContext context(ChannelBusinessType businessType) {
        return new ChannelCommandContext(1L, new ChannelProductKey("SYNTHETIC", "ONBOARDING"), "CFG-1", businessType, 2L, 1L, "SERIAL-1", "TRACE-1");
    }

    private ChannelResultMeta meta(LocalDateTime time) {
        return new ChannelResultMeta(new ChannelProductKey("SYNTHETIC", "ONBOARDING"), "CFG-1", "SERIAL-1", "REQUEST-1", "ACCEPTED", "MAP-1", ChannelOperationStatus.ACCEPTED, "Accepted", time);
    }
}
