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

package top.continew.admin.service.workflow;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.system.enums.MessageTypeEnum;
import top.continew.admin.system.model.req.MessageReq;
import top.continew.admin.system.service.MessageService;
import top.continew.starter.extension.tenant.util.TenantUtils;

import java.time.LocalDateTime;
import java.util.List;

/** Persists ContiNew messages and marks queue rows sent in the same database transaction. */
@Component
@RequiredArgsConstructor
public class WorkflowNotificationDispatcher {

    private final WorkflowNotificationQueue queue;
    private final MessageService messageService;

    @Transactional
    public int dispatchPending(int limit) {
        List<PendingWorkflowNotification> pending = queue.listPending(limit);
        for (PendingWorkflowNotification notification : pending) {
            MessageReq request = new MessageReq(MessageTypeEnum.SYSTEM);
            request.setTitle(notification.title());
            request.setContent(notification.content());
            request.setPath(notification.path());
            Long[] messageId = new Long[1];
            TenantUtils.execute(notification.tenantId(), () -> messageId[0] = messageService
                .addAndReturnId(request, List.of(String.valueOf(notification.recipientUserId()))));
            if (messageId[0] == null || !queue.markSent(notification.id(), messageId[0], LocalDateTime.now())) {
                throw new IllegalStateException("Workflow notification delivery state changed concurrently");
            }
        }
        return pending.size();
    }
}
