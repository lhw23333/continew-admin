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

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/** Actuator health contribution containing only aggregate Flowable job counts. */
public final class FlowableJobHealthIndicator implements HealthIndicator {

    private final FlowableJobMonitor monitor;
    private final FlowableEnginePolicyProperties.MonitoringProperties properties;

    public FlowableJobHealthIndicator(FlowableJobMonitor monitor,
                                      FlowableEnginePolicyProperties.MonitoringProperties properties) {
        this.monitor = monitor;
        this.properties = properties;
    }

    @Override
    public Health health() {
        try {
            FlowableJobSnapshot snapshot = monitor.snapshot();
            Health.Builder builder = snapshot.deadLetterJobs() > properties.getDeadLetterDegradedThreshold()
                ? Health.status("DEGRADED")
                : Health.up();
            return builder.withDetail("executableJobs", snapshot.executableJobs())
                .withDetail("timerJobs", snapshot.timerJobs())
                .withDetail("suspendedJobs", snapshot.suspendedJobs())
                .withDetail("deadLetterJobs", snapshot.deadLetterJobs())
                .withDetail("historyJobs", snapshot.historyJobs())
                .withDetail("sampledAt", snapshot.sampledAt().toString())
                .build();
        } catch (RuntimeException ex) {
            return Health.down().withDetail("errorCategory", "FLOWABLE_JOB_QUERY_FAILED").build();
        }
    }
}
