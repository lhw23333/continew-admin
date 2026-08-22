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

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Project-owned Flowable engine policy and bounded async-executor settings. */
@ConfigurationProperties(prefix = "workflow.flowable")
public class FlowableEnginePolicyProperties {

    private SchemaStrategy schemaStrategy = SchemaStrategy.DEFAULT_ACT_FLW_PREFIXES;
    private AsyncExecutorProperties asyncExecutor = new AsyncExecutorProperties();
    private MonitoringProperties monitoring = new MonitoringProperties();

    public SchemaStrategy getSchemaStrategy() {
        return schemaStrategy;
    }

    public void setSchemaStrategy(SchemaStrategy schemaStrategy) {
        this.schemaStrategy = schemaStrategy;
    }

    public AsyncExecutorProperties getAsyncExecutor() {
        return asyncExecutor;
    }

    public void setAsyncExecutor(AsyncExecutorProperties asyncExecutor) {
        this.asyncExecutor = asyncExecutor;
    }

    public MonitoringProperties getMonitoring() {
        return monitoring;
    }

    public void setMonitoring(MonitoringProperties monitoring) {
        this.monitoring = monitoring;
    }

    public void validate() {
        if (!SchemaStrategy.DEFAULT_ACT_FLW_PREFIXES.equals(schemaStrategy)) {
            throw new IllegalStateException("Unsupported Flowable schema strategy");
        }
        if (asyncExecutor == null || monitoring == null) {
            throw new IllegalStateException("Flowable engine policy is incomplete");
        }
        asyncExecutor.validate();
        monitoring.validate();
    }

    public enum SchemaStrategy { DEFAULT_ACT_FLW_PREFIXES }

    public static class AsyncExecutorProperties {

        private int corePoolSize = 4;
        private int maxPoolSize = 16;
        private int queueSize = 256;
        private Duration keepAlive = Duration.ofSeconds(60);
        private Duration shutdownWait = Duration.ofSeconds(30);
        private int retries = 3;
        private int maxAsyncJobsPerAcquisition = 8;
        private int maxTimerJobsPerAcquisition = 8;
        private Duration asyncJobAcquireWait = Duration.ofSeconds(10);
        private Duration timerJobAcquireWait = Duration.ofSeconds(10);
        private Duration queueFullWait = Duration.ofSeconds(5);
        private Duration asyncJobLockTime = Duration.ofMinutes(5);
        private Duration timerJobLockTime = Duration.ofMinutes(5);
        private Duration resetExpiredJobsInterval = Duration.ofSeconds(60);
        private int resetExpiredJobsPageSize = 100;

        public int getCorePoolSize() {
            return corePoolSize;
        }

        public void setCorePoolSize(int corePoolSize) {
            this.corePoolSize = corePoolSize;
        }

        public int getMaxPoolSize() {
            return maxPoolSize;
        }

        public void setMaxPoolSize(int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
        }

        public int getQueueSize() {
            return queueSize;
        }

        public void setQueueSize(int queueSize) {
            this.queueSize = queueSize;
        }

        public Duration getKeepAlive() {
            return keepAlive;
        }

        public void setKeepAlive(Duration keepAlive) {
            this.keepAlive = keepAlive;
        }

        public Duration getShutdownWait() {
            return shutdownWait;
        }

        public void setShutdownWait(Duration shutdownWait) {
            this.shutdownWait = shutdownWait;
        }

        public int getRetries() {
            return retries;
        }

        public void setRetries(int retries) {
            this.retries = retries;
        }

        public int getMaxAsyncJobsPerAcquisition() {
            return maxAsyncJobsPerAcquisition;
        }

        public void setMaxAsyncJobsPerAcquisition(int maxAsyncJobsPerAcquisition) {
            this.maxAsyncJobsPerAcquisition = maxAsyncJobsPerAcquisition;
        }

        public int getMaxTimerJobsPerAcquisition() {
            return maxTimerJobsPerAcquisition;
        }

        public void setMaxTimerJobsPerAcquisition(int maxTimerJobsPerAcquisition) {
            this.maxTimerJobsPerAcquisition = maxTimerJobsPerAcquisition;
        }

        public Duration getAsyncJobAcquireWait() {
            return asyncJobAcquireWait;
        }

        public void setAsyncJobAcquireWait(Duration asyncJobAcquireWait) {
            this.asyncJobAcquireWait = asyncJobAcquireWait;
        }

        public Duration getTimerJobAcquireWait() {
            return timerJobAcquireWait;
        }

        public void setTimerJobAcquireWait(Duration timerJobAcquireWait) {
            this.timerJobAcquireWait = timerJobAcquireWait;
        }

        public Duration getQueueFullWait() {
            return queueFullWait;
        }

        public void setQueueFullWait(Duration queueFullWait) {
            this.queueFullWait = queueFullWait;
        }

        public Duration getAsyncJobLockTime() {
            return asyncJobLockTime;
        }

        public void setAsyncJobLockTime(Duration asyncJobLockTime) {
            this.asyncJobLockTime = asyncJobLockTime;
        }

        public Duration getTimerJobLockTime() {
            return timerJobLockTime;
        }

        public void setTimerJobLockTime(Duration timerJobLockTime) {
            this.timerJobLockTime = timerJobLockTime;
        }

        public Duration getResetExpiredJobsInterval() {
            return resetExpiredJobsInterval;
        }

        public void setResetExpiredJobsInterval(Duration resetExpiredJobsInterval) {
            this.resetExpiredJobsInterval = resetExpiredJobsInterval;
        }

        public int getResetExpiredJobsPageSize() {
            return resetExpiredJobsPageSize;
        }

        public void setResetExpiredJobsPageSize(int resetExpiredJobsPageSize) {
            this.resetExpiredJobsPageSize = resetExpiredJobsPageSize;
        }

        private void validate() {
            positive(corePoolSize, "corePoolSize");
            if (maxPoolSize < corePoolSize) {
                throw new IllegalStateException("Flowable async maxPoolSize must be at least corePoolSize");
            }
            positive(queueSize, "queueSize");
            nonNegative(retries, "retries");
            positive(maxAsyncJobsPerAcquisition, "maxAsyncJobsPerAcquisition");
            positive(maxTimerJobsPerAcquisition, "maxTimerJobsPerAcquisition");
            positive(resetExpiredJobsPageSize, "resetExpiredJobsPageSize");
            positive(keepAlive, "keepAlive");
            positive(shutdownWait, "shutdownWait");
            positive(asyncJobAcquireWait, "asyncJobAcquireWait");
            positive(timerJobAcquireWait, "timerJobAcquireWait");
            positive(queueFullWait, "queueFullWait");
            positive(asyncJobLockTime, "asyncJobLockTime");
            positive(timerJobLockTime, "timerJobLockTime");
            positive(resetExpiredJobsInterval, "resetExpiredJobsInterval");
        }

        private void positive(int value, String name) {
            if (value <= 0) {
                throw new IllegalStateException("Flowable async " + name + " must be positive");
            }
        }

        private void nonNegative(int value, String name) {
            if (value < 0) {
                throw new IllegalStateException("Flowable async " + name + " must not be negative");
            }
        }

        private void positive(Duration value, String name) {
            if (value == null || value.isZero() || value.isNegative()) {
                throw new IllegalStateException("Flowable async " + name + " must be positive");
            }
        }
    }

    public static class MonitoringProperties {

        private Duration cacheTtl = Duration.ofSeconds(5);
        private long deadLetterDegradedThreshold;

        public Duration getCacheTtl() {
            return cacheTtl;
        }

        public void setCacheTtl(Duration cacheTtl) {
            this.cacheTtl = cacheTtl;
        }

        public long getDeadLetterDegradedThreshold() {
            return deadLetterDegradedThreshold;
        }

        public void setDeadLetterDegradedThreshold(long deadLetterDegradedThreshold) {
            this.deadLetterDegradedThreshold = deadLetterDegradedThreshold;
        }

        private void validate() {
            if (cacheTtl == null || cacheTtl.isNegative() || cacheTtl.isZero()) {
                throw new IllegalStateException("Flowable monitoring cacheTtl must be positive");
            }
            if (deadLetterDegradedThreshold < 0) {
                throw new IllegalStateException("Flowable dead-letter threshold must not be negative");
            }
        }
    }
}
