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

import java.time.Duration;

/** Bounded resilience settings for one immutable channel operation configuration. */
public record ChannelOperationResiliencePolicy(Integer maxAttempts, Duration retryDelay,
                                               Integer circuitFailureThreshold, Duration circuitOpenDuration,
                                               Integer maxConcurrentCalls) {
    public ChannelOperationResiliencePolicy {
        if (maxAttempts == null || maxAttempts < 1 || maxAttempts > 5 || retryDelay == null || retryDelay
            .isNegative() || retryDelay.compareTo(Duration
                .ofMinutes(1)) > 0 || circuitFailureThreshold == null || circuitFailureThreshold < 2 || circuitFailureThreshold > 100 || circuitOpenDuration == null || circuitOpenDuration
                    .compareTo(Duration.ofSeconds(1)) < 0 || circuitOpenDuration.compareTo(Duration
                        .ofMinutes(10)) > 0 || maxConcurrentCalls == null || maxConcurrentCalls < 1 || maxConcurrentCalls > 100) {
            throw ChannelContracts.invalid("channel operation resilience policy");
        }
    }

    public static ChannelOperationResiliencePolicy defaults(ChannelOperation operation) {
        if (operation == null) {
            throw ChannelContracts.invalid("channel operation");
        }
        return new ChannelOperationResiliencePolicy(operation.safeToRetry() ? 3 : 1, Duration.ofMillis(100), 5, Duration
            .ofSeconds(30), 8);
    }
}
