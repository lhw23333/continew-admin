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

import io.micrometer.core.instrument.binder.MeterBinder;
import org.flowable.engine.ManagementService;
import org.flowable.engine.ProcessEngine;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.ProcessEngineConfigurationConfigurer;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

/** Applies and verifies project-owned Flowable process-engine policy. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FlowableEnginePolicyProperties.class)
public class FlowableEnginePolicyConfiguration {

    @Bean
    public ProcessEngineConfigurationConfigurer processEngineConfigurationConfigurer(FlowableEnginePolicyProperties properties) {
        properties.validate();
        FlowableEnginePolicyProperties.AsyncExecutorProperties async = properties.getAsyncExecutor();
        return configuration -> configureAsyncExecutor(configuration, async);
    }

    @Bean
    public FlowableEnginePolicyVerifier flowableEnginePolicyVerifier(ProcessEngine processEngine,
                                                                     ManagementService managementService,
                                                                     FlowableEnginePolicyProperties properties) {
        return new FlowableEnginePolicyVerifier(processEngine, managementService, properties);
    }

    @Bean
    public FlowableJobMonitor flowableJobMonitor(ManagementService managementService,
                                                 FlowableEnginePolicyProperties properties) {
        return new FlowableJobMonitor(managementService, properties.getMonitoring(), Clock.systemUTC());
    }

    @Bean(name = "flowableJobs")
    public HealthIndicator flowableJobHealthIndicator(FlowableJobMonitor monitor,
                                                      FlowableEnginePolicyProperties properties) {
        return new FlowableJobHealthIndicator(monitor, properties.getMonitoring());
    }

    @Bean
    public MeterBinder flowableJobMetrics(FlowableJobMonitor monitor) {
        return new FlowableJobMetrics(monitor);
    }

    private void configureAsyncExecutor(SpringProcessEngineConfiguration configuration,
                                        FlowableEnginePolicyProperties.AsyncExecutorProperties properties) {
        configuration.setAsyncExecutorCorePoolSize(properties.getCorePoolSize());
        configuration.setAsyncExecutorMaxPoolSize(properties.getMaxPoolSize());
        configuration.setAsyncExecutorThreadPoolQueueSize(properties.getQueueSize());
        configuration.setAsyncExecutorThreadKeepAliveTime(toMillis(properties.getKeepAlive(), "keepAlive"));
        configuration.setAsyncExecutorSecondsToWaitOnShutdown(toSeconds(properties.getShutdownWait(), "shutdownWait"));
        configuration.setAsyncExecutorNumberOfRetries(properties.getRetries());
        configuration.setAsyncExecutorMaxAsyncJobsDuePerAcquisition(properties.getMaxAsyncJobsPerAcquisition());
        configuration.setAsyncExecutorMaxTimerJobsPerAcquisition(properties.getMaxTimerJobsPerAcquisition());
        configuration.setAsyncExecutorDefaultAsyncJobAcquireWaitTime(toMillisInt(properties
            .getAsyncJobAcquireWait(), "asyncJobAcquireWait"));
        configuration.setAsyncExecutorDefaultTimerJobAcquireWaitTime(toMillisInt(properties
            .getTimerJobAcquireWait(), "timerJobAcquireWait"));
        configuration.setAsyncExecutorDefaultQueueSizeFullWaitTime(toMillisInt(properties
            .getQueueFullWait(), "queueFullWait"));
        configuration.setAsyncExecutorAsyncJobLockTimeInMillis(toMillisInt(properties
            .getAsyncJobLockTime(), "asyncJobLockTime"));
        configuration.setAsyncExecutorTimerLockTimeInMillis(toMillisInt(properties
            .getTimerJobLockTime(), "timerJobLockTime"));
        configuration.setAsyncExecutorResetExpiredJobsInterval(toMillisInt(properties
            .getResetExpiredJobsInterval(), "resetExpiredJobsInterval"));
        configuration.setAsyncExecutorResetExpiredJobsPageSize(properties.getResetExpiredJobsPageSize());
    }

    private long toMillis(Duration duration, String name) {
        try {
            return duration.toMillis();
        } catch (ArithmeticException ex) {
            throw new IllegalStateException("Flowable async " + name + " is too large", ex);
        }
    }

    private int toMillisInt(Duration duration, String name) {
        long millis = toMillis(duration, name);
        if (millis > Integer.MAX_VALUE) {
            throw new IllegalStateException("Flowable async " + name + " is too large");
        }
        return (int)millis;
    }

    private long toSeconds(Duration duration, String name) {
        try {
            return duration.toSeconds();
        } catch (ArithmeticException ex) {
            throw new IllegalStateException("Flowable async " + name + " is too large", ex);
        }
    }
}
