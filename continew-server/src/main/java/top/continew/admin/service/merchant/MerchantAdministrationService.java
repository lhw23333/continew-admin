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

package top.continew.admin.service.merchant;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import top.continew.admin.auth.service.OnlineUserService;
import top.continew.admin.common.enums.DisEnableStatusEnum;
import top.continew.admin.merchant.master.application.MerchantMasterService;
import top.continew.admin.merchant.master.application.MerchantProfileView;
import top.continew.admin.merchant.master.domain.Merchant;
import top.continew.admin.merchant.master.domain.MerchantStatus;
import top.continew.admin.merchant.security.audit.application.SecurityAuditWriter;
import top.continew.admin.merchant.security.audit.domain.SecurityAuditRecord;
import top.continew.admin.merchant.security.audit.domain.SecurityAuditResult;
import top.continew.admin.merchant.security.crypto.SensitiveValueProtector;
import top.continew.admin.merchant.security.value.EncryptedMobileNumber;
import top.continew.admin.system.mapper.user.UserMapper;
import top.continew.admin.system.model.entity.user.UserDO;

import java.time.LocalDateTime;

/** Coordinates masked merchant profile updates with user nicknames and immutable audit. */
@Service
@RequiredArgsConstructor
public class MerchantAdministrationService {

    private final MerchantMasterService merchantMasterService;
    private final SensitiveValueProtector sensitiveValueProtector;
    private final UserMapper userMapper;
    private final OnlineUserService onlineUserService;
    private final SecurityAuditWriter securityAuditWriter;
    private final TransactionTemplate transactionTemplate;

    public MerchantProfileView updateProfile(Long tenantId,
                                             Long actorUserId,
                                             Long merchantId,
                                             String shortName,
                                             String contactName,
                                             String contactMobile,
                                             String reviewerMobile,
                                             String industry,
                                             String productDescription,
                                             Long expectedVersion,
                                             String ipAddress) {
        Merchant changed = transactionTemplate.execute(status -> {
            Merchant current = merchantMasterService.requireAccessible(tenantId, actorUserId, merchantId);
            EncryptedMobileNumber contact = contactMobile == null || contactMobile.isBlank()
                ? current.contactMobile()
                : EncryptedMobileNumber.fromPlaintext(contactMobile, sensitiveValueProtector);
            EncryptedMobileNumber reviewer = reviewerMobile == null || reviewerMobile.isBlank()
                ? current.reviewerMobile()
                : EncryptedMobileNumber.fromPlaintext(reviewerMobile, sensitiveValueProtector);
            Merchant updated = merchantMasterService
                .updateProfile(tenantId, actorUserId, merchantId, shortName, contactName, contact, reviewer, industry, productDescription, expectedVersion);
            userMapper.lambdaUpdate()
                .eq(UserDO::getId, updated.operatorUserId())
                .set(UserDO::getNickname, nickname(shortName, "操作员"))
                .update();
            userMapper.lambdaUpdate()
                .eq(UserDO::getId, updated.reviewerUserId())
                .set(UserDO::getNickname, nickname(shortName, "复核员"))
                .update();
            return updated;
        });
        securityAuditWriter.append(new SecurityAuditRecord(tenantId, actorUserId, changed
            .owningAgentId(), "MERCHANT_PROFILE_UPDATE", "MERCHANT", changed.id(), changed
                .rowVersion(), null, "ordinary profile updated; certified identity and ownership unchanged", ipAddress, SecurityAuditResult.SUCCESS, null, LocalDateTime
                    .now()));
        return MerchantProfileView.from(changed);
    }

    public MerchantProfileView changeLifecycle(Long tenantId,
                                               Long actorUserId,
                                               Long merchantId,
                                               MerchantStatus status,
                                               String reason,
                                               Long expectedVersion,
                                               String ipAddress) {
        Merchant changed = transactionTemplate.execute(transactionStatus -> {
            Merchant updated = merchantMasterService
                .changeLifecycle(tenantId, actorUserId, merchantId, status, reason, expectedVersion);
            DisEnableStatusEnum userStatus = MerchantStatus.DISABLED.equals(status)
                ? DisEnableStatusEnum.DISABLE
                : DisEnableStatusEnum.ENABLE;
            userMapper.lambdaUpdate()
                .eq(UserDO::getId, updated.operatorUserId())
                .set(UserDO::getStatus, userStatus)
                .update();
            userMapper.lambdaUpdate()
                .eq(UserDO::getId, updated.reviewerUserId())
                .set(UserDO::getStatus, userStatus)
                .update();
            return updated;
        });
        if (MerchantStatus.DISABLED.equals(status)) {
            onlineUserService.kickOut(changed.operatorUserId());
            onlineUserService.kickOut(changed.reviewerUserId());
        }
        securityAuditWriter.append(new SecurityAuditRecord(tenantId, actorUserId, changed
            .owningAgentId(), "MERCHANT_LIFECYCLE_CHANGE", "MERCHANT", changed.id(), changed
                .rowVersion(), null, sanitize(reason), ipAddress, SecurityAuditResult.SUCCESS, null, LocalDateTime
                    .now()));
        return MerchantProfileView.from(changed);
    }

    private String nickname(String shortName, String suffix) {
        String value = shortName.replaceAll("[^\\p{IsHan}A-Za-z0-9_-]", "-") + suffix;
        return value.substring(0, Math.min(value.length(), 30));
    }

    private String sanitize(String value) {
        if (value == null)
            return null;
        String sanitized = value.replaceAll("[\\p{Cntrl}]", " ")
            .replaceAll("(?<!\\d)\\d{7,}(?!\\d)", "[REDACTED]")
            .replaceAll("\\s+", " ")
            .trim();
        return sanitized.substring(0, Math.min(sanitized.length(), 255));
    }
}
