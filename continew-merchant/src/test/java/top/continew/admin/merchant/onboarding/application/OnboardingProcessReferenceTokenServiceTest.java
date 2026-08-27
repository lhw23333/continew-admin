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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.continew.admin.channel.dto.ChannelSigningAction;
import top.continew.admin.merchant.security.crypto.VersionedKeyProvider;

import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OnboardingProcessReferenceTokenServiceTest {

    private MutableClock clock;
    private OnboardingProcessReferenceTokenService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-08-27T12:00:00Z"));
        VersionedKeyProvider keyProvider = keyProvider();
        service = new OnboardingProcessReferenceTokenService(keyProvider, clock, new SecureRandom());
    }

    @Test
    void signedTokenRetainsEveryOwnershipActionAndExpiryClaim() {
        var issued = service.issue(101L, 201L, 301L, "channel-a", ChannelSigningAction.SIGN_AGREEMENT, Duration
            .ofMinutes(10));

        OnboardingProcessReferenceClaims claims = service.verify(issued.token());
        assertEquals(101L, claims.tenantId());
        assertEquals(201L, claims.merchantId());
        assertEquals(301L, claims.applicationId());
        assertEquals("CHANNEL-A", claims.channelCode());
        assertEquals(ChannelSigningAction.SIGN_AGREEMENT, claims.action());
        assertEquals(Instant.parse("2026-08-27T12:10:00Z"), claims.expiresAt());
        assertFalse(issued.toString().contains(issued.token()));
    }

    @Test
    void payloadAndSignatureTamperingUseOneInvalidResult() {
        String token = service.issue(101L, 201L, 301L, "CHANNEL-A", ChannelSigningAction.BIND_CARD, Duration
            .ofMinutes(10)).token();
        int separator = token.indexOf('.');
        String tamperedPayload = replace(token, 5);
        String tamperedSignature = replace(token, separator + 5);

        assertEquals(ProcessReferenceException.Code.INVALID, assertThrows(ProcessReferenceException.class, () -> service
            .verify(tamperedPayload)).code());
        assertEquals(ProcessReferenceException.Code.INVALID, assertThrows(ProcessReferenceException.class, () -> service
            .verify(tamperedSignature)).code());
    }

    @Test
    void expiryIsRejectedAtTheExactBoundary() {
        String token = service.issue(101L, 201L, 301L, "CHANNEL-A", ChannelSigningAction.OPEN_RESERVE_ACCOUNT, Duration
            .ofMinutes(10)).token();
        clock.instant = Instant.parse("2026-08-27T12:10:00Z");

        ProcessReferenceException exception = assertThrows(ProcessReferenceException.class, () -> service
            .verify(token));
        assertEquals(ProcessReferenceException.Code.EXPIRED, exception.code());
    }

    private String replace(String value, int index) {
        char replacement = value.charAt(index) == 'A' ? 'B' : 'A';
        return value.substring(0, index) + replacement + value.substring(index + 1);
    }

    private VersionedKeyProvider keyProvider() {
        return new VersionedKeyProvider() {
            private final VersionedKey key = new VersionedKey("TEST-HASH-V1", new SecretKeySpec(new byte[32], "HmacSHA256"));

            @Override
            public VersionedKey currentDataKey() {
                return key;
            }

            @Override
            public VersionedKey dataKey(String version) {
                return key;
            }

            @Override
            public VersionedKey currentHashKey() {
                return key;
            }
        };
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
