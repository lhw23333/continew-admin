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

import org.flowable.engine.ManagementService;

import java.time.Clock;
import java.time.Instant;

/** Samples Flowable job queues without exposing job payloads, exception stacks, or process variables. */
public final class FlowableJobMonitor {

    private final ManagementService managementService;
    private final FlowableEnginePolicyProperties.MonitoringProperties properties;
    private final Clock clock;
    private volatile FlowableJobSnapshot cached;

    public FlowableJobMonitor(ManagementService managementService,
                              FlowableEnginePolicyProperties.MonitoringProperties properties,
                              Clock clock) {
        this.managementService = managementService;
        this.properties = properties;
        this.clock = clock;
    }

    public FlowableJobSnapshot snapshot() {
        Instant now = clock.instant();
        FlowableJobSnapshot current = cached;
        if (current != null && current.sampledAt().plus(properties.getCacheTtl()).isAfter(now)) {
            return current;
        }
        synchronized (this) {
            current = cached;
            if (current != null && current.sampledAt().plus(properties.getCacheTtl()).isAfter(now)) {
                return current;
            }
            FlowableJobSnapshot refreshed = new FlowableJobSnapshot(managementService.createJobQuery()
                .count(), managementService.createTimerJobQuery().count(), managementService.createSuspendedJobQuery()
                    .count(), managementService.createDeadLetterJobQuery().count(), managementService
                        .createHistoryJobQuery()
                        .count(), now);
            cached = refreshed;
            return refreshed;
        }
    }

    public void invalidate() {
        cached = null;
    }
}
