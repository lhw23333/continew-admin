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

package top.continew.admin.merchant.onboarding.outbox;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Bounded workflow outbox retry, batching, and stale-lock policy. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "merchant.outbox")
public class WorkflowOutboxPolicy {

    private boolean enabled = true;
    private int batchSize = 20;
    private int maxRetries = 5;
    private Duration baseRetryDelay = Duration.ofSeconds(5);
    private Duration maxRetryDelay = Duration.ofMinutes(15);
    private Duration lockTimeout = Duration.ofMinutes(5);

    public int boundedBatchSize() {
        return Math.max(1, Math.min(batchSize, 100));
    }

    public int boundedMaxRetries() {
        return Math.max(1, Math.min(maxRetries, 20));
    }

    public Duration boundedLockTimeout() {
        return positive(lockTimeout, Duration.ofMinutes(5), Duration.ofHours(1));
    }

    public Duration retryDelay(int retryCount) {
        Duration base = positive(baseRetryDelay, Duration.ofSeconds(5), Duration.ofMinutes(5));
        Duration maximum = positive(maxRetryDelay, Duration.ofMinutes(15), Duration.ofHours(24));
        long multiplier = 1L << Math.min(Math.max(retryCount - 1, 0), 20);
        long delayMillis;
        try {
            delayMillis = Math.multiplyExact(base.toMillis(), multiplier);
        } catch (ArithmeticException ex) {
            delayMillis = maximum.toMillis();
        }
        return Duration.ofMillis(Math.min(delayMillis, maximum.toMillis()));
    }

    private Duration positive(Duration value, Duration fallback, Duration upperBound) {
        if (value == null || value.isZero() || value.isNegative()) {
            return fallback;
        }
        return value.compareTo(upperBound) > 0 ? upperBound : value;
    }
}
