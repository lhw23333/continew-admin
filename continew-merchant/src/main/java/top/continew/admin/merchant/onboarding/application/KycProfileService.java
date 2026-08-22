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
import top.continew.admin.merchant.master.domain.MerchantType;
import top.continew.admin.merchant.security.audit.application.SecurityAuditWriter;
import top.continew.admin.merchant.security.audit.domain.SecurityAuditRecord;
import top.continew.admin.merchant.security.audit.domain.SecurityAuditResult;
import top.continew.admin.merchant.security.crypto.SensitiveDataCipher;
import top.continew.admin.merchant.security.crypto.SensitiveValueProtector;
import top.continew.admin.merchant.security.value.EncryptedIdentityNumber;
import top.continew.admin.merchant.security.value.EncryptedMobileNumber;
import top.continew.starter.extension.tenant.context.TenantContextHolder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Validates and encrypts versioned legal-subject, person, beneficiary, and shareholder draft data. */
@Service
@RequiredArgsConstructor
public class KycProfileService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final MerchantScopeAuthorizationService merchantScopeAuthorizationService;
    private final OnboardingDraftRepository draftRepository;
    private final KycProfileRepository profileRepository;
    private final SensitiveValueProtector protector;
    private final ObjectMapper objectMapper;
    private final SecurityAuditWriter securityAuditWriter;
    private final Clock clock = Clock.systemDefaultZone();

    @Transactional
    public KycProfileView save(KycProfileSaveCommand command) {
        requireTenant(command.tenantId());
        Merchant merchant = merchantScopeAuthorizationService.requireAccessible(command.tenantId(), command
            .actorUserId(), command.merchantId());
        OnboardingDraft draft = draftRepository.findByApplicationId(command.tenantId(), command.merchantId(), command
            .applicationId()).orElseThrow(MerchantAccessDeniedException::new);
        if (!draft.rowVersion().equals(command.expectedVersion())) {
            throw new OnboardingDraftConflictException();
        }
        validate(command, merchant.merchantType());

        EncryptedIdentityNumber legalIdentifier = EncryptedIdentityNumber.fromPlaintext(command
            .legalIdentifier(), protector);
        List<KycProfileView.PersonView> personViews = new ArrayList<>();
        for (KycProfileSaveCommand.Person person : command.persons()) {
            EncryptedIdentityNumber identity = EncryptedIdentityNumber.fromPlaintext(person
                .identityNumber(), protector);
            EncryptedMobileNumber mobile = EncryptedMobileNumber.fromPlaintext(person.mobile(), protector);
            personViews.add(new KycProfileView.PersonView(person.role(), person.name().trim(), identity
                .maskedValue(), mobile.maskedValue(), person.documentValidFrom(), person.documentValidTo()));
        }
        List<KycProfileView.ShareholderView> shareholderViews = new ArrayList<>();
        for (KycProfileSaveCommand.Shareholder shareholder : command.shareholders()) {
            EncryptedIdentityNumber identifier = EncryptedIdentityNumber.fromPlaintext(shareholder
                .identifier(), protector);
            shareholderViews.add(new KycProfileView.ShareholderView(shareholder.type(), shareholder.name()
                .trim(), identifier.maskedValue(), shareholder.ownershipPercent()));
        }

        SensitiveDataCipher.EncryptedData addressPayload = encryptPayload("KYC_ADDRESS", command.tenantId(), draft
            .kycVersionId(), command.address());
        SensitiveDataCipher.EncryptedData personPayload = encryptPayload("KYC_PERSONS", command.tenantId(), draft
            .kycVersionId(), command.persons());
        SensitiveDataCipher.EncryptedData shareholderPayload = encryptPayload("KYC_SHAREHOLDERS", command
            .tenantId(), draft.kycVersionId(), command.shareholders());
        if (!addressPayload.keyVersion().equals(personPayload.keyVersion()) || !addressPayload.keyVersion()
            .equals(shareholderPayload.keyVersion())) {
            throw new MerchantDomainException("Sensitive payload key changed during KYC save; retry is required");
        }
        KycProfileEncryptedDraft encrypted = new KycProfileEncryptedDraft(command.legalName()
            .trim(), legalIdentifier, command.licenseIssueDate(), command.licenseExpiryDate(), command.businessScope()
                .trim(), addressPayload.ciphertext(), personPayload.ciphertext(), shareholderPayload
                    .ciphertext(), addressPayload.keyVersion());
        LocalDateTime now = LocalDateTime.now(clock);
        if (!profileRepository.update(command.tenantId(), command.merchantId(), command.applicationId(), draft
            .kycVersionId(), encrypted, command.expectedVersion(), now)) {
            throw new OnboardingDraftConflictException();
        }
        securityAuditWriter.append(new SecurityAuditRecord(command.tenantId(), command.actorUserId(), merchant
            .owningAgentId(), "KYC_PROFILE_SAVE", "KYC_VERSION", draft.kycVersionId(), command
                .expectedVersion() + 1, "PROFILE", "persons=%s;shareholders=%s;payloadKeyVersion=%s".formatted(command
                    .persons()
                    .size(), command.shareholders().size(), addressPayload.keyVersion()), command
                        .ipAddress(), SecurityAuditResult.SUCCESS, null, now));
        return new KycProfileView(draft.kycVersionId(), command.expectedVersion() + 1, command.legalName()
            .trim(), legalIdentifier.maskedValue(), command.licenseIssueDate(), command.licenseExpiryDate(), command
                .businessScope()
                .trim(), new KycProfileView.AddressView(command.address().registeredAddress().trim(), command.address()
                    .operatingRegion()
                    .trim(), command.address().operatingAddress().trim()), personViews, shareholderViews);
    }

    private void validate(KycProfileSaveCommand command, MerchantType merchantType) {
        LocalDate today = LocalDate.now(clock);
        required(command.legalName(), 200, "legalName");
        required(command.legalIdentifier(), 64, "legalIdentifier");
        required(command.businessScope(), 2000, "businessScope");
        if (command.licenseIssueDate() == null || command.licenseExpiryDate() == null || command.licenseIssueDate()
            .isAfter(command.licenseExpiryDate()) || command.licenseExpiryDate().isBefore(today)) {
            throw new MerchantDomainException("Business license dates are invalid or expired");
        }
        if (command.address() == null) {
            throw new MerchantDomainException("KYC address is required");
        }
        required(command.address().registeredAddress(), 255, "registeredAddress");
        required(command.address().operatingRegion(), 100, "operatingRegion");
        required(command.address().operatingAddress(), 255, "operatingAddress");
        if (command.persons() == null || command.persons().size() < 2 || command.persons().size() > 50) {
            throw new MerchantDomainException("KYC persons are invalid");
        }
        long legalRepresentatives = command.persons()
            .stream()
            .filter(person -> KycProfileSaveCommand.PersonRole.LEGAL_REPRESENTATIVE.equals(person.role()))
            .count();
        long operators = command.persons()
            .stream()
            .filter(person -> KycProfileSaveCommand.PersonRole.OPERATOR.equals(person.role()))
            .count();
        long beneficiaries = command.persons()
            .stream()
            .filter(person -> KycProfileSaveCommand.PersonRole.BENEFICIAL_OWNER.equals(person.role()))
            .count();
        if (legalRepresentatives != 1 || operators != 1 || MerchantType.ENTERPRISE
            .equals(merchantType) && beneficiaries < 1) {
            throw new MerchantDomainException("Legal representative, operator, or beneficiary roles are invalid");
        }
        for (KycProfileSaveCommand.Person person : command.persons()) {
            if (person == null || person.role() == null) {
                throw new MerchantDomainException("KYC person is invalid");
            }
            required(person.name(), 100, "personName");
            required(person.identityNumber(), 64, "personIdentityNumber");
            required(person.mobile(), 32, "personMobile");
            if (person.documentValidFrom() == null || person.documentValidTo() == null || person.documentValidFrom()
                .isAfter(person.documentValidTo()) || person.documentValidTo().isBefore(today)) {
                throw new MerchantDomainException("KYC person document is invalid or expired");
            }
        }
        if (MerchantType.ENTERPRISE.equals(merchantType)) {
            if (command.shareholders() == null || command.shareholders().isEmpty() || command.shareholders()
                .size() > 100) {
                throw new MerchantDomainException("Enterprise shareholders are required");
            }
            BigDecimal total = BigDecimal.ZERO;
            for (KycProfileSaveCommand.Shareholder shareholder : command.shareholders()) {
                if (shareholder == null || shareholder.type() == null) {
                    throw new MerchantDomainException("KYC shareholder is invalid");
                }
                required(shareholder.name(), 200, "shareholderName");
                required(shareholder.identifier(), 64, "shareholderIdentifier");
                if (shareholder.ownershipPercent() == null || shareholder.ownershipPercent()
                    .compareTo(BigDecimal.ZERO) <= 0 || shareholder.ownershipPercent()
                        .compareTo(HUNDRED) > 0 || shareholder.ownershipPercent().scale() > 4) {
                    throw new MerchantDomainException("Shareholder ownership percent is invalid");
                }
                total = total.add(shareholder.ownershipPercent());
            }
            if (total.compareTo(HUNDRED) != 0) {
                throw new MerchantDomainException("Shareholder ownership percent must total 100");
            }
        } else if (command.shareholders() != null && !command.shareholders().isEmpty()) {
            throw new MerchantDomainException("Individual merchant must not contain shareholder structure");
        }
    }

    private SensitiveDataCipher.EncryptedData encryptPayload(String purpose,
                                                             Long tenantId,
                                                             Long kycVersionId,
                                                             Object value) {
        try {
            return protector.encryptPayload("%s:%s:%s".formatted(purpose, tenantId, kycVersionId), objectMapper
                .writeValueAsString(value));
        } catch (JsonProcessingException ex) {
            throw new MerchantDomainException("KYC sensitive payload serialization failed");
        }
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
