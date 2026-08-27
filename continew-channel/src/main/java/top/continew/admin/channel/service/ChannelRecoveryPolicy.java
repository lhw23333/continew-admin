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

import org.springframework.stereotype.Component;

import java.time.Duration;

/** Bounded uncertain-command polling and stale-claim policy. */
@Component
public class ChannelRecoveryPolicy {
    private final int batchSize;
    private final int maxRetries;
    private final Duration baseRetryDelay;
    private final Duration maxRetryDelay;
    private final Duration lockTimeout;

    public ChannelRecoveryPolicy() {
        this(50, 5, Duration.ofSeconds(30), Duration.ofMinutes(15), Duration.ofMinutes(5));
    }

    ChannelRecoveryPolicy(int batchSize,
                          int maxRetries,
                          Duration baseRetryDelay,
                          Duration maxRetryDelay,
                          Duration lockTimeout) {
        if (batchSize < 1 || batchSize > 500 || maxRetries < 1 || maxRetries > 20 || baseRetryDelay == null || baseRetryDelay
            .isNegative() || maxRetryDelay == null || maxRetryDelay
                .compareTo(baseRetryDelay) < 0 || lockTimeout == null || lockTimeout.isZero() || lockTimeout
                    .isNegative()) {
            throw new IllegalArgumentException("Channel recovery policy is invalid");
        }
        this.batchSize = batchSize;
        this.maxRetries = maxRetries;
        this.baseRetryDelay = baseRetryDelay;
        this.maxRetryDelay = maxRetryDelay;
        this.lockTimeout = lockTimeout;
    }

    public int batchSize() {
        return batchSize;
    }

    public int maxRetries() {
        return maxRetries;
    }

    public Duration lockTimeout() {
        return lockTimeout;
    }

    public Duration retryDelay(int retryCount) {
        long multiplier = 1L << Math.min(Math.max(retryCount - 1, 0), 20);
        Duration delay = baseRetryDelay.multipliedBy(multiplier);
        return delay.compareTo(maxRetryDelay) > 0 ? maxRetryDelay : delay;
    }
}
