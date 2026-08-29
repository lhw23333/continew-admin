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

package top.continew.admin.config.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import top.continew.admin.merchant.onboarding.outbox.WorkflowOutboxProcessor;
import top.continew.admin.workflow.api.WorkflowDeploymentService;
import top.continew.admin.workflow.definition.MerchantLimitAdjustmentWorkflowDefinition;
import top.continew.admin.workflow.definition.MerchantOnboardingReviewWorkflowDefinition;

/** Explicitly enabled workflow bootstrap for synthetic acceptance tenants only. */
@Component
public class SyntheticWorkflowBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SyntheticWorkflowBootstrapRunner.class);

    private final WorkflowDeploymentService deploymentService;
    private final MerchantOnboardingReviewWorkflowDefinition onboardingDefinition;
    private final MerchantLimitAdjustmentWorkflowDefinition limitDefinition;
    private final WorkflowOutboxProcessor outboxProcessor;
    private final boolean enabled;
    private final long tenantId;
    private final long actorUserId;
    private final long repairEventId;

    public SyntheticWorkflowBootstrapRunner(WorkflowDeploymentService deploymentService,
                                            MerchantOnboardingReviewWorkflowDefinition onboardingDefinition,
                                            MerchantLimitAdjustmentWorkflowDefinition limitDefinition,
                                            WorkflowOutboxProcessor outboxProcessor,
                                            @Value("${merchant.synthetic.workflow-bootstrap-enabled:false}") boolean enabled,
                                            @Value("${merchant.synthetic.workflow-tenant-id:0}") long tenantId,
                                            @Value("${merchant.synthetic.workflow-actor-user-id:0}") long actorUserId,
                                            @Value("${merchant.synthetic.workflow-repair-event-id:0}") long repairEventId) {
        this.deploymentService = deploymentService;
        this.onboardingDefinition = onboardingDefinition;
        this.limitDefinition = limitDefinition;
        this.outboxProcessor = outboxProcessor;
        this.enabled = enabled;
        this.tenantId = tenantId;
        this.actorUserId = actorUserId;
        this.repairEventId = repairEventId;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        if (tenantId <= 0 || actorUserId <= 0) {
            throw new IllegalStateException("Synthetic workflow bootstrap identifiers are invalid");
        }
        deploymentService.deploy(onboardingDefinition.deploymentCommand(tenantId, actorUserId));
        deploymentService.deploy(limitDefinition.deploymentCommand(tenantId, actorUserId));
        if (repairEventId > 0 && !outboxProcessor.requeueRepair(tenantId, repairEventId)) {
            log.warn("Synthetic workflow repair event could not be requeued: tenantId={}, eventId={}", tenantId, repairEventId);
        }
        log.info("Synthetic workflow definitions are ready for tenant [{}]", tenantId);
    }
}
