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

package top.continew.admin.merchant.agent.domain;

import java.time.LocalDateTime;

/** Immutable value copy proving which agent defaults were applied when a KYC draft was created. */
public record KycDraftDefaultSnapshot(Long id, Long tenantId, Long kycVersionId, Long agentDefaultVersionId,
                                      AgentMerchantDefaults defaults, LocalDateTime copiedTime, Long createUser,
                                      LocalDateTime createTime) {

    public KycDraftDefaultSnapshot {
        if (id == null || tenantId == null || kycVersionId == null || agentDefaultVersionId == null || defaults == null || copiedTime == null || createUser == null || createTime == null) {
            throw new IllegalArgumentException("Required KYC draft-default snapshot fields must not be null");
        }
    }
}
