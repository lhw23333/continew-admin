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

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import top.continew.admin.merchant.review.application.WorkflowNotificationDraft;
import top.continew.admin.merchant.review.application.WorkflowNotificationPort;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/** Explicit-tenant idempotency queue bridging workflow events to ContiNew messages. */
@Component
public class WorkflowNotificationQueue implements WorkflowNotificationPort {

    private final JdbcTemplate jdbcTemplate;

    public WorkflowNotificationQueue(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void enqueue(WorkflowNotificationDraft draft) {
        enqueueIfAbsent(draft);
    }

    public boolean enqueueIfAbsent(WorkflowNotificationDraft draft) {
        require(draft);
        LocalDateTime now = LocalDateTime.now();
        try {
            jdbcTemplate.update("""
                INSERT INTO biz_workflow_notification
                (id, tenant_id, notification_key, event_type, recipient_user_id, process_instance_id, task_id,
                 title, content, path, status, create_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?)
                """, stableId(draft.tenantId(), draft.notificationKey()), draft.tenantId(), draft
                .notificationKey(), draft.eventType(), draft.recipientUserId(), draft.processInstanceId(), draft
                    .taskId(), draft.title(), draft.content(), draft.path(), now);
            return true;
        } catch (DuplicateKeyException ignored) {
            // Deterministic notification key already queued or sent.
            return false;
        }
    }

    List<PendingWorkflowNotification> listPending(int limit) {
        if (limit < 1 || limit > 500) {
            throw new IllegalArgumentException("Invalid workflow notification batch size");
        }
        return jdbcTemplate.query("""
            SELECT id, tenant_id, notification_key, event_type, recipient_user_id, process_instance_id, task_id,
                   title, content, path
            FROM biz_workflow_notification
            WHERE status = 'PENDING'
            ORDER BY create_time, id
            LIMIT ?
            FOR UPDATE SKIP LOCKED
            """, this::map, limit);
    }

    boolean markSent(Long id, Long messageId, LocalDateTime sentTime) {
        return jdbcTemplate.update("""
            UPDATE biz_workflow_notification
            SET status = 'SENT', message_id = ?, sent_time = ?
            WHERE id = ? AND status = 'PENDING'
            """, messageId, sentTime, id) == 1;
    }

    private PendingWorkflowNotification map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new PendingWorkflowNotification(resultSet.getLong("id"), resultSet.getLong("tenant_id"), resultSet
            .getString("notification_key"), resultSet.getString("event_type"), resultSet
                .getLong("recipient_user_id"), resultSet.getString("process_instance_id"), resultSet
                    .getString("task_id"), resultSet.getString("title"), resultSet.getString("content"), resultSet
                        .getString("path"));
    }

    private void require(WorkflowNotificationDraft draft) {
        if (draft == null || draft.tenantId() == null || draft.tenantId() <= 0 || draft
            .recipientUserId() == null || draft.recipientUserId() <= 0 || blank(draft.notificationKey()) || draft
                .notificationKey()
                .length() > 191 || blank(draft.eventType()) || draft.eventType().length() > 32 || blank(draft
                    .processInstanceId()) || draft.processInstanceId().length() > 64 || blank(draft.title()) || draft
                        .title()
                        .length() > 50 || blank(draft.content()) || draft.content().length() > 255 || blank(draft
                            .path()) || draft.path().length() > 255) {
            throw new IllegalArgumentException("Invalid workflow notification");
        }
    }

    private Long stableId(Long tenantId, String notificationKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest((tenantId + ":" + notificationKey).getBytes(StandardCharsets.UTF_8));
            long value = ByteBuffer.wrap(digest).getLong() & Long.MAX_VALUE;
            return value == 0 ? 1L : value;
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
