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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import top.continew.admin.channel.api.ChannelTransportException;
import top.continew.admin.channel.dto.ChannelCommandContext;
import top.continew.admin.channel.dto.ChannelOperation;
import top.continew.admin.channel.dto.ChannelOperationResiliencePolicy;
import top.continew.admin.channel.dto.ChannelTransportResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;

/** Per-version operation bulkhead, circuit breaker, and bounded safe-query retry executor. */
@Component
public class ChannelResilienceExecutor {
    private final Clock clock;
    private final RetrySleeper sleeper;
    private final ConcurrentMap<PolicyKey, CircuitState> circuits = new ConcurrentHashMap<>();
    private final ConcurrentMap<PolicyKey, Semaphore> bulkheads = new ConcurrentHashMap<>();

    @Autowired
    public ChannelResilienceExecutor() {
        this(Clock.systemUTC(), duration -> {
            try {
                Thread.sleep(duration.toMillis());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new ChannelTransportException(ChannelTransportException.Code.TRANSPORT_FAILED, ChannelTransportException.TransmissionState.NOT_SENT);
            }
        });
    }

    ChannelResilienceExecutor(Clock clock, RetrySleeper sleeper) {
        if (clock == null || sleeper == null) {
            throw new IllegalArgumentException("Channel resilience executor configuration is invalid");
        }
        this.clock = clock;
        this.sleeper = sleeper;
    }

    public ChannelTransportResponse execute(ChannelCommandContext context,
                                            ChannelOperation operation,
                                            ChannelOperationResiliencePolicy policy,
                                            TransportAttempt attempt) {
        if (context == null || operation == null || policy == null || attempt == null) {
            throw new IllegalArgumentException("Channel resilience execution is invalid");
        }
        PolicyKey key = new PolicyKey(context.tenantId(), context.product().channelCode(), context.product()
            .productCode(), context.configVersion(), operation);
        CircuitState circuit = circuits.computeIfAbsent(key, ignored -> new CircuitState(policy
            .circuitFailureThreshold(), policy.circuitOpenDuration()));
        Instant now = clock.instant();
        if (!circuit.tryAcquire(now)) {
            throw new ChannelTransportException(ChannelTransportException.Code.CIRCUIT_OPEN, ChannelTransportException.TransmissionState.NOT_SENT);
        }
        Semaphore bulkhead = bulkheads.computeIfAbsent(key, ignored -> new Semaphore(policy
            .maxConcurrentCalls(), true));
        if (!bulkhead.tryAcquire()) {
            circuit.cancelProbe();
            throw new ChannelTransportException(ChannelTransportException.Code.BULKHEAD_FULL, ChannelTransportException.TransmissionState.NOT_SENT);
        }
        try {
            for (int attemptNumber = 1; attemptNumber <= policy.maxAttempts(); attemptNumber++) {
                try {
                    ChannelTransportResponse response = attempt.execute(attemptNumber);
                    circuit.onSuccess();
                    return response;
                } catch (ChannelTransportException ex) {
                    boolean recordable = isRecordable(ex);
                    if (recordable) {
                        circuit.onFailure(clock.instant());
                    } else {
                        circuit.cancelProbe();
                    }
                    boolean retry = operation.safeToRetry() && recordable && attemptNumber < policy
                        .maxAttempts() && circuit.allowsRetry();
                    if (!retry) {
                        throw ex;
                    }
                    sleeper.sleep(retryDelay(policy, attemptNumber));
                } catch (RuntimeException ex) {
                    ChannelTransportException failure = new ChannelTransportException(ChannelTransportException.Code.TRANSPORT_FAILED, ChannelTransportException.TransmissionState.UNKNOWN);
                    circuit.onFailure(clock.instant());
                    boolean retry = operation.safeToRetry() && attemptNumber < policy.maxAttempts() && circuit
                        .allowsRetry();
                    if (!retry) {
                        throw failure;
                    }
                    sleeper.sleep(retryDelay(policy, attemptNumber));
                }
            }
            throw new ChannelTransportException(ChannelTransportException.Code.TRANSPORT_FAILED, ChannelTransportException.TransmissionState.UNKNOWN);
        } finally {
            bulkhead.release();
        }
    }

    private boolean isRecordable(ChannelTransportException exception) {
        return exception.code() == ChannelTransportException.Code.TIMEOUT || exception
            .code() == ChannelTransportException.Code.TRANSPORT_FAILED || exception
                .code() == ChannelTransportException.Code.UNCERTAIN_RESULT;
    }

    private Duration retryDelay(ChannelOperationResiliencePolicy policy, int attemptNumber) {
        Duration delay = policy.retryDelay().multipliedBy(attemptNumber);
        return delay.compareTo(Duration.ofMinutes(1)) > 0 ? Duration.ofMinutes(1) : delay;
    }

    @FunctionalInterface
    public interface TransportAttempt {
        ChannelTransportResponse execute(int attemptNumber);
    }

    @FunctionalInterface
    interface RetrySleeper {
        void sleep(Duration duration);
    }

    private record PolicyKey(Long tenantId, String channelCode, String productCode, String configVersion,
                             ChannelOperation operation) {
    }

    private static final class CircuitState {
        private enum Mode { CLOSED, OPEN, HALF_OPEN }

        private final int failureThreshold;
        private final Duration openDuration;
        private Mode mode = Mode.CLOSED;
        private int consecutiveFailures;
        private Instant openUntil;
        private boolean probeInFlight;

        private CircuitState(int failureThreshold, Duration openDuration) {
            this.failureThreshold = failureThreshold;
            this.openDuration = openDuration;
        }

        private synchronized boolean tryAcquire(Instant now) {
            if (mode == Mode.OPEN && !now.isBefore(openUntil)) {
                mode = Mode.HALF_OPEN;
                probeInFlight = false;
            }
            if (mode == Mode.OPEN) {
                return false;
            }
            if (mode == Mode.HALF_OPEN) {
                if (probeInFlight) {
                    return false;
                }
                probeInFlight = true;
            }
            return true;
        }

        private synchronized void onSuccess() {
            mode = Mode.CLOSED;
            consecutiveFailures = 0;
            openUntil = null;
            probeInFlight = false;
        }

        private synchronized void onFailure(Instant now) {
            consecutiveFailures++;
            if (mode == Mode.HALF_OPEN || consecutiveFailures >= failureThreshold) {
                mode = Mode.OPEN;
                openUntil = now.plus(openDuration);
                probeInFlight = false;
            }
        }

        private synchronized void cancelProbe() {
            if (mode == Mode.HALF_OPEN) {
                probeInFlight = false;
            }
        }

        private synchronized boolean allowsRetry() {
            return mode == Mode.CLOSED;
        }
    }
}
