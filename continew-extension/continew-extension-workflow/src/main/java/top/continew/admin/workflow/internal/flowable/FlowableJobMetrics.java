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

package top.continew.admin.workflow.internal.flowable;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

/** Micrometer gauges for aggregate Flowable job queue depth. */
public final class FlowableJobMetrics implements MeterBinder {

    private final FlowableJobMonitor monitor;

    public FlowableJobMetrics(FlowableJobMonitor monitor) {
        this.monitor = monitor;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        gauge(registry, "flowable.jobs.executable", "Executable Flowable jobs", JobCount.EXECUTABLE);
        gauge(registry, "flowable.jobs.timer", "Timer Flowable jobs", JobCount.TIMER);
        gauge(registry, "flowable.jobs.suspended", "Suspended Flowable jobs", JobCount.SUSPENDED);
        gauge(registry, "flowable.jobs.dead_letter", "Dead-letter Flowable jobs", JobCount.DEAD_LETTER);
        gauge(registry, "flowable.jobs.history", "Async-history Flowable jobs", JobCount.HISTORY);
    }

    private void gauge(MeterRegistry registry, String name, String description, JobCount count) {
        Gauge.builder(name, monitor, value -> count.value(value.snapshot()))
            .description(description)
            .strongReference(true)
            .register(registry);
    }

    private enum JobCount {
        EXECUTABLE {
            @Override
            long value(FlowableJobSnapshot snapshot) {
                return snapshot.executableJobs();
            }
        },
        TIMER {
            @Override
            long value(FlowableJobSnapshot snapshot) {
                return snapshot.timerJobs();
            }
        },
        SUSPENDED {
            @Override
            long value(FlowableJobSnapshot snapshot) {
                return snapshot.suspendedJobs();
            }
        },
        DEAD_LETTER {
            @Override
            long value(FlowableJobSnapshot snapshot) {
                return snapshot.deadLetterJobs();
            }
        },
        HISTORY {
            @Override
            long value(FlowableJobSnapshot snapshot) {
                return snapshot.historyJobs();
            }
        };

        abstract long value(FlowableJobSnapshot snapshot);
    }
}
