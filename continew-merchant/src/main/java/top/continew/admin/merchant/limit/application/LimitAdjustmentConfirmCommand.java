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

package top.continew.admin.merchant.limit.application;

import java.math.BigDecimal;

/** Explicit confirmation echoes the preview version/value; the server recalculates both before creation. */
public record LimitAdjustmentConfirmCommand(Long tenantId, Long actorUserId, Long merchantId, String channelCode,
                                            String platformCode, String currency, BigDecimal requestedLimit,
                                            BigDecimal confirmedNormalizedLimit, String confirmedPolicyVersion,
                                            String reason, String ipAddress) {
}
