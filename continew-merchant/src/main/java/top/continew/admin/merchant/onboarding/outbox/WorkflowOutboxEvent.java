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

/** Claimed identifier-only outbox event ready for one delivery attempt. */
public record WorkflowOutboxEvent(Long id, Long tenantId, String aggregateType, Long aggregateId, Long aggregateVersion,
                                  String eventType, String eventKey, String payloadJson, String status,
                                  Integer retryCount, String lockedBy, LocalDateTime lockedTime, String traceId) {
}
