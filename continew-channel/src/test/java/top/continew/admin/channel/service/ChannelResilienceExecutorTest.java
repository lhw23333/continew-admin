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

package top.continew.admin.channel.service;

import org.junit.jupiter.api.Test;
import top.continew.admin.channel.api.ChannelTransportException;
import top.continew.admin.channel.dto.ChannelBusinessType;
import top.continew.admin.channel.dto.ChannelCommandContext;
import top.continew.admin.channel.dto.ChannelOperation;
import top.continew.admin.channel.dto.ChannelOperationResiliencePolicy;
import top.continew.admin.channel.dto.ChannelProductKey;
import top.continew.admin.channel.dto.ChannelTransportResponse;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChannelResilienceExecutorTest {

    @Test
    void safeQueryRetriesRetryableFailuresWithBoundedDelay() {
        MutableClock clock = new MutableClock();
        List<Duration> delays = new ArrayList<>();
        ChannelResilienceExecutor executor = new ChannelResilienceExecutor(clock, delays::add);
        ChannelOperationResiliencePolicy policy = new ChannelOperationResiliencePolicy(3, Duration
            .ofMillis(10), 10, Duration.ofSeconds(30), 2);
        AtomicInteger attempts = new AtomicInteger();

        ChannelTransportResponse response = executor
            .execute(context(), ChannelOperation.QUERY_ONBOARDING_STATUS, policy, attempt -> {
                if (attempts.incrementAndGet() < 3) {
                    throw new ChannelTransportException(ChannelTransportException.Code.TIMEOUT, ChannelTransportException.TransmissionState.SENT);
                }
                return response();
            });

        assertEquals(200, response.statusCode());
        assertEquals(3, attempts.get());
        assertEquals(List.of(Duration.ofMillis(10), Duration.ofMillis(20)), delays);
    }

    @Test
    void circuitOpensAndAllowsSingleSuccessfulHalfOpenProbe() {
        MutableClock clock = new MutableClock();
        ChannelResilienceExecutor executor = new ChannelResilienceExecutor(clock, duration -> {
        });
        ChannelOperationResiliencePolicy policy = new ChannelOperationResiliencePolicy(1, Duration.ZERO, 2, Duration
            .ofSeconds(10), 2);
        AtomicInteger calls = new AtomicInteger();

        for (int index = 0; index < 2; index++) {
            assertThrows(ChannelTransportException.class, () -> executor
                .execute(context(), ChannelOperation.SUBMIT_ONBOARDING, policy, attempt -> {
                    calls.incrementAndGet();
                    throw new ChannelTransportException(ChannelTransportException.Code.TRANSPORT_FAILED, ChannelTransportException.TransmissionState.UNKNOWN);
                }));
        }
        ChannelTransportException open = assertThrows(ChannelTransportException.class, () -> executor
            .execute(context(), ChannelOperation.SUBMIT_ONBOARDING, policy, attempt -> response()));
        assertEquals(ChannelTransportException.Code.CIRCUIT_OPEN, open.code());
        assertEquals(2, calls.get());

        clock.advance(Duration.ofSeconds(11));
        assertEquals(200, executor.execute(context(), ChannelOperation.SUBMIT_ONBOARDING, policy, attempt -> response())
            .statusCode());
        assertEquals(200, executor.execute(context(), ChannelOperation.SUBMIT_ONBOARDING, policy, attempt -> response())
            .statusCode());
    }

    @Test
    void bulkheadRejectsConcurrentCallBeforeAttempt() throws Exception {
        MutableClock clock = new MutableClock();
        ChannelResilienceExecutor executor = new ChannelResilienceExecutor(clock, duration -> {
        });
        ChannelOperationResiliencePolicy policy = new ChannelOperationResiliencePolicy(1, Duration.ZERO, 5, Duration
            .ofSeconds(30), 1);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService workers = Executors.newSingleThreadExecutor();
        try {
            Future<ChannelTransportResponse> first = workers.submit(() -> executor
                .execute(context(), ChannelOperation.QUERY_ACCOUNT_INFO, policy, attempt -> {
                    entered.countDown();
                    try {
                        if (!release.await(5, TimeUnit.SECONDS)) {
                            throw new AssertionError("Timed out waiting to release bulkhead test call");
                        }
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(ex);
                    }
                    return response();
                }));
            if (!entered.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for bulkhead test call");
            }

            ChannelTransportException rejected = assertThrows(ChannelTransportException.class, () -> executor
                .execute(context(), ChannelOperation.QUERY_ACCOUNT_INFO, policy, attempt -> response()));
            assertEquals(ChannelTransportException.Code.BULKHEAD_FULL, rejected.code());
            assertEquals(ChannelTransportException.TransmissionState.NOT_SENT, rejected.transmissionState());
            release.countDown();
            assertEquals(200, first.get(5, TimeUnit.SECONDS).statusCode());
        } finally {
            release.countDown();
            workers.shutdownNow();
        }
    }

    private ChannelCommandContext context() {
        return new ChannelCommandContext(1L, new ChannelProductKey("SYNTHETIC", "ONBOARDING"), "CFG-1", ChannelBusinessType.ONBOARDING, 2L, 1L, "SERIAL-1", "TRACE-1");
    }

    private ChannelTransportResponse response() {
        return new ChannelTransportResponse(200, "REQUEST-1", "ok".getBytes(StandardCharsets.UTF_8), LocalDateTime
            .of(2026, 8, 26, 12, 0));
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-08-26T12:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
