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
import java.util.EnumMap;
import java.util.Map;

/** Bounded transport and per-operation timeout policy. */
public record ChannelTimeoutPolicy(Duration connectTimeout, Duration readTimeout,
                                   Map<ChannelOperation, Duration> operationTimeouts,
                                   Map<ChannelOperation, ChannelOperationResiliencePolicy> resiliencePolicies) {
    public ChannelTimeoutPolicy(Duration connectTimeout,
                                Duration readTimeout,
                                Map<ChannelOperation, Duration> operationTimeouts) {
        this(connectTimeout, readTimeout, operationTimeouts, null);
    }

    public ChannelTimeoutPolicy {
        connectTimeout = bounded(connectTimeout, "connectTimeout");
        readTimeout = bounded(readTimeout, "readTimeout");
        if (operationTimeouts == null || operationTimeouts.size() != ChannelOperation.values().length) {
            throw ChannelContracts.invalid("operationTimeouts");
        }
        EnumMap<ChannelOperation, Duration> normalized = new EnumMap<>(ChannelOperation.class);
        operationTimeouts.forEach((operation, duration) -> normalized
            .put(operation, bounded(duration, "operationTimeouts")));
        operationTimeouts = Map.copyOf(normalized);
        EnumMap<ChannelOperation, ChannelOperationResiliencePolicy> normalizedResilience = new EnumMap<>(ChannelOperation.class);
        if (resiliencePolicies == null) {
            for (ChannelOperation operation : ChannelOperation.values()) {
                normalizedResilience.put(operation, ChannelOperationResiliencePolicy.defaults(operation));
            }
        } else {
            if (resiliencePolicies.size() != ChannelOperation.values().length) {
                throw ChannelContracts.invalid("resiliencePolicies");
            }
            resiliencePolicies.forEach((operation, policy) -> {
                if (operation == null || policy == null || !operation.safeToRetry() && policy.maxAttempts() != 1) {
                    throw ChannelContracts.invalid("resiliencePolicies");
                }
                normalizedResilience.put(operation, policy);
            });
        }
        resiliencePolicies = Map.copyOf(normalizedResilience);
    }

    private static Duration bounded(Duration value, String name) {
        if (value == null || value.isNegative() || value.isZero() || value.compareTo(Duration.ofMinutes(10)) > 0) {
            throw ChannelContracts.invalid(name);
        }
        return value;
    }
}
