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

package top.continew.admin.merchant.kyc.attachment;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.continew.admin.channel.api.ChannelEvidenceAccessException;
import top.continew.admin.channel.api.ChannelEvidenceAccessPort;
import top.continew.admin.channel.api.ChannelEvidenceAuditPort;
import top.continew.admin.channel.dto.ChannelEvidenceAccess;
import top.continew.admin.channel.dto.ChannelEvidenceAccessMode;
import top.continew.admin.channel.dto.ChannelEvidenceAuditOutcome;
import top.continew.admin.channel.dto.ChannelEvidenceAuditRecord;
import top.continew.admin.channel.dto.ChannelOnboardingSubmitCommand;
import top.continew.admin.channel.dto.ChannelOperation;
import top.continew.starter.extension.tenant.context.TenantContextHolder;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** Authorizes exact-version private evidence and issues a bounded channel-specific access reference. */
@Service
@RequiredArgsConstructor
public class ChannelEvidenceAccessService implements ChannelEvidenceAccessPort {

    private static final Duration MAX_CHANNEL_ACCESS_EXPIRY = Duration.ofMinutes(5);

    private final KycAttachmentRepository attachmentRepository;
    private final PrivateObjectStoragePort privateObjectStoragePort;
    private final KycAttachmentPolicy attachmentPolicy;
    private final ChannelEvidenceAuditPort auditPort;
    private final Clock clock = Clock.systemUTC();

    @Override
    public ChannelEvidenceAccess issue(ChannelOnboardingSubmitCommand command, Long evidenceObjectId) {
        if (command == null || evidenceObjectId == null || evidenceObjectId <= 0) {
            throw new ChannelEvidenceAccessException(ChannelEvidenceAccessException.Code.OBJECT_UNAVAILABLE);
        }
        if (!command.context().tenantId().equals(TenantContextHolder.getTenantId())) {
            throw denied(command, evidenceObjectId, null, ChannelEvidenceAccessException.Code.TENANT_CONTEXT_MISMATCH);
        }
        if (!command.evidenceObjectIds().contains(evidenceObjectId)) {
            throw denied(command, evidenceObjectId, null, ChannelEvidenceAccessException.Code.OBJECT_NOT_REFERENCED);
        }
        KycAttachment attachment = attachmentRepository.findById(command.context().tenantId(), evidenceObjectId)
            .orElseThrow(() -> denied(command, evidenceObjectId, null, ChannelEvidenceAccessException.Code.OBJECT_UNAVAILABLE));
        if (!attachment.kycVersionId().equals(command.kycVersionId())) {
            throw denied(command, evidenceObjectId, attachment, ChannelEvidenceAccessException.Code.KYC_VERSION_MISMATCH);
        }
        if (!attachment.isAccessible()) {
            throw denied(command, evidenceObjectId, attachment, ChannelEvidenceAccessException.Code.OBJECT_NOT_CLEARED);
        }

        PrivateObjectStoragePort.TemporaryAccess temporaryAccess;
        try {
            temporaryAccess = privateObjectStoragePort.createTemporaryAccess(attachment.storageObjectId(), expiry());
        } catch (RuntimeException ex) {
            throw denied(command, evidenceObjectId, attachment, ChannelEvidenceAccessException.Code.TEMPORARY_ACCESS_FAILED, ex);
        }
        ChannelEvidenceAccess access;
        try {
            access = new ChannelEvidenceAccess(attachment.id(), attachment.evidenceType(), attachment
                .sha256(), attachment.detectedMime(), attachment.sizeBytes(), URI.create(temporaryAccess
                    .url()), temporaryAccess.expiresAt());
        } catch (RuntimeException ex) {
            throw denied(command, evidenceObjectId, attachment, ChannelEvidenceAccessException.Code.TEMPORARY_ACCESS_FAILED, ex);
        }
        appendAudit(command, attachment, temporaryAccess.expiresAt(), ChannelEvidenceAuditOutcome.GRANTED, null);
        return access;
    }

    private Duration expiry() {
        return attachmentPolicy.accessExpiry().compareTo(MAX_CHANNEL_ACCESS_EXPIRY) > 0
            ? MAX_CHANNEL_ACCESS_EXPIRY
            : attachmentPolicy.accessExpiry();
    }

    private ChannelEvidenceAccessException denied(ChannelOnboardingSubmitCommand command,
                                                  Long evidenceObjectId,
                                                  KycAttachment attachment,
                                                  ChannelEvidenceAccessException.Code code) {
        return denied(command, evidenceObjectId, attachment, code, null);
    }

    private ChannelEvidenceAccessException denied(ChannelOnboardingSubmitCommand command,
                                                  Long evidenceObjectId,
                                                  KycAttachment attachment,
                                                  ChannelEvidenceAccessException.Code code,
                                                  RuntimeException cause) {
        appendAudit(command, attachment, null, ChannelEvidenceAuditOutcome.DENIED, code.name(), evidenceObjectId);
        return cause == null
            ? new ChannelEvidenceAccessException(code)
            : new ChannelEvidenceAccessException(code, cause);
    }

    private void appendAudit(ChannelOnboardingSubmitCommand command,
                             KycAttachment attachment,
                             LocalDateTime expiresAt,
                             ChannelEvidenceAuditOutcome outcome,
                             String failureCategory) {
        appendAudit(command, attachment, expiresAt, outcome, failureCategory, attachment.id());
    }

    private void appendAudit(ChannelOnboardingSubmitCommand command,
                             KycAttachment attachment,
                             LocalDateTime expiresAt,
                             ChannelEvidenceAuditOutcome outcome,
                             String failureCategory,
                             Long evidenceObjectId) {
        String evidenceType = attachment == null ? null : attachment.evidenceType();
        String objectSha256 = attachment == null ? null : attachment.sha256();
        try {
            Long auditId = auditPort.append(new ChannelEvidenceAuditRecord(command
                .context(), ChannelOperation.SUBMIT_ONBOARDING, command
                    .kycVersionId(), evidenceObjectId, evidenceType, objectSha256, ChannelEvidenceAccessMode.TEMPORARY_URL, expiresAt, outcome, failureCategory, LocalDateTime
                        .ofInstant(clock.instant(), ZoneOffset.UTC)));
            if (auditId == null) {
                throw new IllegalStateException();
            }
        } catch (RuntimeException ex) {
            throw new ChannelEvidenceAccessException(ChannelEvidenceAccessException.Code.AUDIT_FAILED, ex);
        }
    }
}
