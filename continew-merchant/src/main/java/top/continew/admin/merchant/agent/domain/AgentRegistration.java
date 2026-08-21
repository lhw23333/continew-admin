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

import top.continew.admin.merchant.security.value.EncryptedMobileNumber;

/** Server-resolved agent registration command. */
public record AgentRegistration(Long id, Long tenantId, Long parentId, Long userId, Long deptId, String agentNo,
                                String name, String contactName, EncryptedMobileNumber contactMobile,
                                String promotionCode) {

    public AgentRegistration(Long id,
                             Long tenantId,
                             Long parentId,
                             Long userId,
                             String agentNo,
                             String name,
                             String contactName,
                             EncryptedMobileNumber contactMobile,
                             String promotionCode) {
        this(id, tenantId, parentId, userId, null, agentNo, name, contactName, contactMobile, promotionCode);
    }

    public AgentRegistration {
        requirePositive(id, "id");
        requirePositive(tenantId, "tenantId");
        if (parentId == null || parentId < 0) {
            throw new IllegalArgumentException("parentId must not be negative");
        }
        requirePositive(userId, "userId");
        if (deptId != null) {
            requirePositive(deptId, "deptId");
        }
        requireText(agentNo, "agentNo");
        requireText(name, "name");
    }

    private static void requirePositive(Long value, String name) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
