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

import org.flowable.common.engine.impl.history.HistoryLevel;
import org.flowable.engine.ManagementService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.job.api.Job;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;

/** Fails startup when the live process engine diverges from the reviewed phase-one policy. */
public final class FlowableEnginePolicyVerifier implements SmartInitializingSingleton, ApplicationListener<ApplicationReadyEvent> {

    private final ProcessEngine processEngine;
    private final ManagementService managementService;
    private final FlowableEnginePolicyProperties properties;

    public FlowableEnginePolicyVerifier(ProcessEngine processEngine,
                                        ManagementService managementService,
                                        FlowableEnginePolicyProperties properties) {
        this.processEngine = processEngine;
        this.managementService = managementService;
        this.properties = properties;
    }

    @Override
    public void afterSingletonsInstantiated() {
        properties.validate();
        ProcessEngineConfiguration configuration = processEngine.getProcessEngineConfiguration();
        if (!HistoryLevel.AUDIT.equals(configuration.getHistoryLevel())) {
            throw new IllegalStateException("Flowable history level must be AUDIT");
        }
        if (!configuration.isAsyncExecutorActivate() || configuration.isAsyncHistoryExecutorActivate()) {
            throw new IllegalStateException("Flowable async executor policy is invalid");
        }
        if (!(configuration instanceof ProcessEngineConfigurationImpl implementation)) {
            throw new IllegalStateException("Unsupported Flowable process-engine configuration");
        }
        if (implementation.getDatabaseTablePrefix() != null && !implementation.getDatabaseTablePrefix().isBlank()) {
            throw new IllegalStateException("Flowable must use the reviewed default ACT_/FLW_ prefixes");
        }
        if (implementation.isTablePrefixIsSchema()) {
            throw new IllegalStateException("Flowable table prefix must not be treated as a dynamic schema");
        }
        String jobTable = managementService.getTableName(Job.class);
        if (jobTable == null || !jobTable.toUpperCase(java.util.Locale.ROOT).startsWith("ACT_")) {
            throw new IllegalStateException("Flowable process tables are outside the reviewed ACT_ prefix");
        }
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        ProcessEngineConfiguration configuration = processEngine.getProcessEngineConfiguration();
        if (!(configuration instanceof ProcessEngineConfigurationImpl implementation) || !implementation
            .getAsyncExecutor()
            .isActive()) {
            throw new IllegalStateException("Flowable async executor is not active after application startup");
        }
    }
}
