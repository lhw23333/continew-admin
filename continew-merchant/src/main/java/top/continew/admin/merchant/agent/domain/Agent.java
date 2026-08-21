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

import java.time.LocalDateTime;
import java.util.Locale;

/** Agent aggregate. Authorization is derived from closure rows, never from {@link #path()}. */
public record Agent(Long id, Long tenantId, Long parentId, String path, Long userId, Long deptId, String agentNo,
                    String name, String contactName, EncryptedMobileNumber contactMobile, String remarks,
                    String promotionCode, AgentPromotionCodeStatus promotionCodeStatus, AgentStatus status,
                    String disabledReason, Long rowVersion, LocalDateTime createTime, LocalDateTime updateTime) {

    public Agent {
        if (id == null || tenantId == null || userId == null || parentId == null || path == null || status == null || promotionCodeStatus == null || rowVersion == null || createTime == null) {
            throw new IllegalArgumentException("Required agent fields must not be null");
        }
    }

    public static Agent create(AgentRegistration registration, String path, LocalDateTime now) {
        String normalizedPromotionCode = normalizePromotionCode(registration.promotionCode());
        return new Agent(registration.id(), registration.tenantId(), registration.parentId(), path, registration
            .userId(), registration.deptId(), registration.agentNo().trim(), registration.name()
                .trim(), trimToNull(registration.contactName()), registration
                    .contactMobile(), null, normalizedPromotionCode, normalizedPromotionCode == null
                        ? AgentPromotionCodeStatus.DISABLED
                        : AgentPromotionCodeStatus.ACTIVE, AgentStatus.ENABLED, null, 0L, now, null);
    }

    public Agent changeStatus(AgentStatus newStatus, String reason, LocalDateTime now) {
        if (newStatus == status) {
            throw new AgentDomainException("Agent is already in the requested lifecycle state");
        }
        String normalizedReason = trimToNull(reason);
        if (newStatus == AgentStatus.DISABLED && normalizedReason == null) {
            throw new AgentDomainException("Disable reason is required");
        }
        return new Agent(id, tenantId, parentId, path, userId, deptId, agentNo, name, contactName, contactMobile, remarks, promotionCode, promotionCodeStatus, newStatus, newStatus == AgentStatus.DISABLED
            ? normalizedReason
            : null, rowVersion + 1, createTime, now);
    }

    public Agent updateProfile(String newName,
                               String newContactName,
                               EncryptedMobileNumber newContactMobile,
                               String newRemarks,
                               LocalDateTime now) {
        String normalizedName = trimToNull(newName);
        String normalizedContactName = trimToNull(newContactName);
        String normalizedRemarks = trimToNull(newRemarks);
        if (normalizedName == null || normalizedName
            .length() > 100 || normalizedContactName == null || normalizedContactName
                .length() > 100 || normalizedRemarks != null && normalizedRemarks.length() > 255) {
            throw new AgentDomainException("Agent profile fields are invalid");
        }
        return new Agent(id, tenantId, parentId, path, userId, deptId, agentNo, normalizedName, normalizedContactName, newContactMobile, normalizedRemarks, promotionCode, promotionCodeStatus, status, disabledReason, rowVersion + 1, createTime, now);
    }

    public Agent assignPromotionCode(String code, LocalDateTime now) {
        if (promotionCode != null) {
            throw new AgentDomainException("Agent promotion code is already assigned");
        }
        String normalizedCode = normalizePromotionCode(code);
        if (normalizedCode == null || normalizedCode.length() > 32) {
            throw new AgentDomainException("Agent promotion code is invalid");
        }
        return new Agent(id, tenantId, parentId, path, userId, deptId, agentNo, name, contactName, contactMobile, remarks, normalizedCode, AgentPromotionCodeStatus.ACTIVE, status, disabledReason, rowVersion + 1, createTime, now);
    }

    public Agent changePromotionCodeStatus(AgentPromotionCodeStatus newStatus, LocalDateTime now) {
        if (newStatus == null || newStatus.equals(promotionCodeStatus) || promotionCode == null) {
            throw new AgentDomainException("Promotion code status change is invalid");
        }
        return new Agent(id, tenantId, parentId, path, userId, deptId, agentNo, name, contactName, contactMobile, remarks, promotionCode, newStatus, status, disabledReason, rowVersion + 1, createTime, now);
    }

    public boolean isEnabled() {
        return AgentStatus.ENABLED == status;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalizePromotionCode(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }
}
