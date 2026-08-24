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

package top.continew.admin.merchant.onboarding.outbox;

import java.time.LocalDateTime;
import java.util.List;

/** Durable claim and delivery-state transitions for workflow outbox events. */
public interface WorkflowOutboxRepository {

    List<WorkflowOutboxEvent> claimAvailable(Long tenantId,
                                             String workerId,
                                             LocalDateTime now,
                                             LocalDateTime staleBefore,
                                             int batchSize);

    boolean markPublished(Long eventId, String workerId, String resultHeadersJson, LocalDateTime publishedTime);

    boolean markRetry(Long eventId,
                      String workerId,
                      int retryCount,
                      LocalDateTime nextRetryTime,
                      String errorCategory,
                      String safeErrorMessage,
                      LocalDateTime updateTime);

    boolean markRepairRequired(Long eventId,
                               String workerId,
                               int retryCount,
                               String errorCategory,
                               String safeErrorMessage,
                               LocalDateTime updateTime);

    boolean requeueRepair(Long tenantId, Long eventId, LocalDateTime updateTime);
}
