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

/** Identifier-only payload delivered by the shared workflow outbox. */
public record LimitAdjustmentWorkflowStartPayload(Long requestId, Long merchantId, Long owningAgentId, Long applicantId,
                                                  Long businessVersion, String channelCode, String processDefinitionKey,
                                                  String businessKey) {

    public LimitAdjustmentWorkflowStartPayload {
        if (requestId == null || requestId <= 0 || merchantId == null || merchantId <= 0 || owningAgentId == null || owningAgentId <= 0 || applicantId == null || applicantId <= 0 || businessVersion == null || businessVersion <= 0 || channelCode == null || channelCode
            .isBlank() || processDefinitionKey == null || processDefinitionKey
                .isBlank() || businessKey == null || businessKey.isBlank()) {
            throw new IllegalArgumentException("Limit adjustment workflow payload is invalid");
        }
    }
}