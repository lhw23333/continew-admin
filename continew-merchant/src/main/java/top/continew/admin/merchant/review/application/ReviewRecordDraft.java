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

package top.continew.admin.merchant.review.application;

import java.time.LocalDateTime;

/** Append-only sanitized human review record values. */
public record ReviewRecordDraft(Long id, Long tenantId, String businessType, Long businessId, Long businessVersion,
                                String processInstanceId, String taskId, Long reviewerUserId, String action,
                                String opinion, String issueCodesJson, String decisionPayloadJson,
                                LocalDateTime decisionTime) {
}
