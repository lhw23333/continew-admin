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

package top.continew.admin.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import top.continew.admin.merchant.onboarding.outbox.WorkflowOutboxBatchResult;
import top.continew.admin.merchant.onboarding.outbox.WorkflowOutboxProcessor;
import top.continew.starter.extension.tenant.annotation.TenantIgnore;

/** Polls identifier-only workflow commands from the transactional outbox. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "merchant.outbox", name = "scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class WorkflowOutboxJob {

    private final WorkflowOutboxProcessor processor;

    @TenantIgnore
    @Scheduled(fixedDelayString = "${merchant.outbox.poll-interval-ms:5000}")
    public void process() {
        WorkflowOutboxBatchResult result = processor.processAvailable();
        if (result.claimed() > 0) {
            log.info("Workflow outbox batch completed: claimed={}, published={}, retried={}, repairRequired={}", result
                .claimed(), result.published(), result.retried(), result.repairRequired());
        }
    }
}
