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

package top.continew.admin.merchant.onboarding.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.merchant.master.application.MerchantScopeAuthorizationService;
import top.continew.admin.merchant.master.domain.Merchant;
import top.continew.admin.merchant.master.domain.MerchantAccessDeniedException;
import top.continew.admin.merchant.master.domain.MerchantDomainException;
import top.continew.admin.merchant.security.audit.application.SecurityAuditWriter;
import top.continew.admin.merchant.security.audit.domain.SecurityAuditRecord;
import top.continew.admin.merchant.security.audit.domain.SecurityAuditResult;
import top.continew.admin.merchant.security.crypto.SensitiveDataCipher;
import top.continew.admin.merchant.security.crypto.SensitiveValueProtector;
import top.continew.admin.merchant.security.value.EncryptedBankAccount;
import top.continew.starter.extension.tenant.context.TenantContextHolder;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** Validates, verifies, encrypts, and version-saves settlement account drafts. */
@Service
@RequiredArgsConstructor
public class SettlementAccountService {

    private final MerchantScopeAuthorizationService merchantScopeAuthorizationService;
    private final OnboardingDraftRepository draftRepository;
    private final SettlementAccountRepository settlementAccountRepository;
    private final SettlementAccountVerificationPort verificationPort;
    private final SensitiveValueProtector protector;
    private final ObjectMapper objectMapper;
    private final SecurityAuditWriter securityAuditWriter;
    private final Clock clock = Clock.systemDefaultZone();

    @Transactional
    public SettlementAccountView save(SettlementAccountSaveCommand command) {
        requireTenant(command.tenantId());
        Merchant merchant = merchantScopeAuthorizationService.requireAccessible(command.tenantId(), command
            .actorUserId(), command.merchantId());
        OnboardingDraft draft = draftRepository.findByApplicationId(command.tenantId(), command.merchantId(), command
            .applicationId()).orElseThrow(MerchantAccessDeniedException::new);
        if (!draft.rowVersion().equals(command.expectedVersion())) {
            throw new OnboardingDraftConflictException();
        }
        validate(command);
        SettlementAccountVerificationPort.VerificationResult verification = verificationPort
            .verify(new SettlementAccountVerificationPort.VerificationCommand(command.tenantId(), command
                .merchantId(), draft.kycVersionId(), command.mode(), command.accountHolderName().trim(), command
                    .bankCode()
                    .trim(), command.bankBranchName().trim(), command.accountNumber()));
        if (verification == null) {
            throw new MerchantDomainException("Settlement account verifier returned no result");
        }
        if (SettlementAccountVerificationPort.SettlementVerificationStatus.FAILED.equals(verification.status())) {
            throw new MerchantDomainException("Settlement account ownership verification failed");
        }
        EncryptedBankAccount account = EncryptedBankAccount.fromPlaintext(command.accountNumber(), protector);
        SensitiveDataCipher.EncryptedData payload = encryptPayload(command, draft.kycVersionId());
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime verifiedTime = SettlementAccountVerificationPort.SettlementVerificationStatus.VERIFIED
            .equals(verification.status()) ? now : null;
        SettlementAccountEncryptedDraft encrypted = new SettlementAccountEncryptedDraft(command.mode(), account, payload
            .ciphertext(), payload.keyVersion(), verification.status(), normalize(verification
                .reference(), 128), normalize(verification.verifierVersion(), 64), verifiedTime);
        if (!settlementAccountRepository.update(command.tenantId(), command.merchantId(), command.applicationId(), draft
            .kycVersionId(), encrypted, command.expectedVersion(), now)) {
            throw new OnboardingDraftConflictException();
        }
        securityAuditWriter.append(new SecurityAuditRecord(command.tenantId(), command.actorUserId(), merchant
            .owningAgentId(), "SETTLEMENT_ACCOUNT_SAVE", "KYC_VERSION", draft.kycVersionId(), command
                .expectedVersion() + 1, "SETTLEMENT_ACCOUNT", "mode=%s;verification=%s;verifier=%s".formatted(command
                    .mode(), verification.status(), encrypted.verifierVersion()), command
                        .ipAddress(), SecurityAuditResult.SUCCESS, null, now));
        return new SettlementAccountView(draft.kycVersionId(), command.expectedVersion() + 1, command.mode(), command
            .accountHolderName()
            .trim(), command.bankCode().trim(), command.bankBranchName().trim(), account.maskedValue(), verification
                .status(), encrypted.verificationReference(), encrypted.verifierVersion(), verifiedTime);
    }

    private SensitiveDataCipher.EncryptedData encryptPayload(SettlementAccountSaveCommand command, Long kycVersionId) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("accountHolderName", command.accountHolderName().trim());
        payload.put("bankCode", command.bankCode().trim());
        payload.put("bankBranchName", command.bankBranchName().trim());
        try {
            return protector.encryptPayload("KYC_SETTLEMENT:%s:%s".formatted(command
                .tenantId(), kycVersionId), objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException ex) {
            throw new MerchantDomainException("Settlement account payload serialization failed");
        }
    }

    private void validate(SettlementAccountSaveCommand command) {
        if (command.mode() == null || command.expectedVersion() == null || command.expectedVersion() < 0) {
            throw new MerchantDomainException("Settlement account mode or version is invalid");
        }
        required(command.accountHolderName(), 200, "accountHolderName");
        required(command.bankCode(), 64, "bankCode");
        required(command.bankBranchName(), 200, "bankBranchName");
        required(command.accountNumber(), 64, "accountNumber");
    }

    private String normalize(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return normalized.substring(0, Math.min(normalized.length(), maxLength));
    }

    private void required(String value, int maxLength, String name) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new MerchantDomainException(name + " is invalid");
        }
    }

    private void requireTenant(Long tenantId) {
        if (tenantId == null || !tenantId.equals(TenantContextHolder.getTenantId())) {
            throw new MerchantAccessDeniedException();
        }
    }
}
