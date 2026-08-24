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
import top.continew.admin.service.workflow.WorkflowNotificationBatchResult;
import top.continew.admin.service.workflow.WorkflowTaskNotificationProcessor;
import top.continew.starter.extension.tenant.annotation.TenantIgnore;

/** Periodically scans workflow assignments/overdue tasks and dispatches idempotent ContiNew messages. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "merchant.workflow-notification", name = "scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class WorkflowNotificationJob {

    private final WorkflowTaskNotificationProcessor processor;

    @TenantIgnore
    @Scheduled(fixedDelayString = "${merchant.workflow-notification.poll-interval-ms:30000}")
    public void process() {
        WorkflowNotificationBatchResult result = processor.process(200, 200);
        if (result.scannedTasks() > 0 || result.sent() > 0) {
            log.info("Workflow notification batch completed: scanned={}, enqueued={}, sent={}", result
                .scannedTasks(), result.enqueued(), result.sent());
        }
    }
}
