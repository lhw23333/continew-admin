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
import top.continew.starter.extension.tenant.context.TenantContextHolder;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Applies same-merchant historical KYC through a global allowlist and channel-specific exclusions. */
@Service
@RequiredArgsConstructor
public class KycReuseService {

    private static final String POLICY_VERSION = "KYC-REUSE-V1";
    private static final Set<KycReuseField> GLOBAL_ALLOWLIST = Set.copyOf(EnumSet
        .of(KycReuseField.LEGAL_NAME, KycReuseField.LEGAL_IDENTIFIER, KycReuseField.LICENSE_DATES, KycReuseField.BUSINESS_SCOPE));

    private final MerchantScopeAuthorizationService merchantScopeAuthorizationService;
    private final OnboardingDraftRepository draftRepository;
    private final KycReuseRepository reuseRepository;
    private final ChannelEligibilityService channelEligibilityService;
    private final SecurityAuditWriter securityAuditWriter;
    private final ObjectMapper objectMapper;
    private final Clock clock = Clock.systemDefaultZone();

    public List<KycReuseSourceView> listSources(Long tenantId, Long actorUserId, Long merchantId, Long applicationId) {
        Merchant merchant = requireMerchantAndTenant(tenantId, actorUserId, merchantId);
        OnboardingDraft target = requireDraft(tenantId, merchantId, applicationId);
        Set<KycReuseField> exclusions = channelExclusions(tenantId, actorUserId, merchant, target);
        LocalDate today = LocalDate.now(clock);
        return reuseRepository.listSources(tenantId, merchantId, target.kycVersionId())
            .stream()
            .map(source -> toView(source, analyze(source, exclusions, today)))
            .toList();
    }

    @Transactional
    public KycReuseResult reuse(Long tenantId,
                                Long actorUserId,
                                Long merchantId,
                                Long applicationId,
                                Long sourceKycVersionId,
                                Set<KycReuseField> requestedFields,
                                Long expectedVersion,
                                String ipAddress) {
        Merchant merchant = requireMerchantAndTenant(tenantId, actorUserId, merchantId);
        OnboardingDraft target = requireDraft(tenantId, merchantId, applicationId);
        if (!target.rowVersion().equals(expectedVersion)) {
            throw new OnboardingDraftConflictException();
        }
        if (requestedFields == null || requestedFields.isEmpty()) {
            throw new MerchantDomainException("At least one reusable KYC field is required");
        }
        Set<KycReuseField> normalizedRequested = Set.copyOf(requestedFields);
        KycReusableSnapshot source = reuseRepository.findSource(tenantId, merchantId, sourceKycVersionId)
            .orElseThrow(MerchantAccessDeniedException::new);
        Set<KycReuseField> exclusions = channelExclusions(tenantId, actorUserId, merchant, target);
        ReuseAnalysis analysis = analyze(source, exclusions, LocalDate.now(clock));
        if (!analysis.reusableFields().containsAll(normalizedRequested)) {
            throw new MerchantDomainException("Requested KYC fields are excluded, incomplete, or expired");
        }
        List<KycReuseField> copied = normalizedRequested.stream().sorted().toList();
        String provenance = writeProvenance(source, target, copied, analysis.reconfirmationFields());
        LocalDateTime now = LocalDateTime.now(clock);
        if (!reuseRepository.apply(tenantId, merchantId, applicationId, target
            .kycVersionId(), source, normalizedRequested, provenance, expectedVersion, now)) {
            throw new OnboardingDraftConflictException();
        }
        securityAuditWriter.append(new SecurityAuditRecord(tenantId, actorUserId, merchant
            .owningAgentId(), "KYC_DRAFT_REUSE", "KYC_VERSION", target
                .kycVersionId(), expectedVersion + 1, "SOURCE_KYC_VERSION", "sourceVersionId=%s;copied=%s;reconfirm=%s;policy=%s"
                    .formatted(source.id(), copied, analysis
                        .reconfirmationFields(), POLICY_VERSION), ipAddress, SecurityAuditResult.SUCCESS, null, now));
        return new KycReuseResult(target.kycVersionId(), source.id(), expectedVersion + 1, copied, analysis
            .reconfirmationFields());
    }

    private Merchant requireMerchantAndTenant(Long tenantId, Long actorUserId, Long merchantId) {
        if (tenantId == null || !tenantId.equals(TenantContextHolder.getTenantId())) {
            throw new MerchantAccessDeniedException();
        }
        return merchantScopeAuthorizationService.requireAccessible(tenantId, actorUserId, merchantId);
    }

    private OnboardingDraft requireDraft(Long tenantId, Long merchantId, Long applicationId) {
        return draftRepository.findByApplicationId(tenantId, merchantId, applicationId)
            .orElseThrow(MerchantAccessDeniedException::new);
    }

    private Set<KycReuseField> channelExclusions(Long tenantId,
                                                 Long actorUserId,
                                                 Merchant merchant,
                                                 OnboardingDraft target) {
        EligibleChannel eligible = channelEligibilityService.list(tenantId, actorUserId, merchant.id())
            .stream()
            .filter(item -> target.channelCode().equals(item.channelCode()) && target.productCode()
                .equals(item.productCode()))
            .findFirst()
            .orElseThrow(() -> new MerchantDomainException("Target channel product is not currently eligible for KYC reuse"));
        EnumSet<KycReuseField> exclusions = EnumSet.noneOf(KycReuseField.class);
        for (String field : eligible.requirements().reuseExcludedFields()) {
            try {
                exclusions.add(KycReuseField.valueOf(field));
            } catch (IllegalArgumentException ex) {
                throw new MerchantDomainException("Channel KYC reuse exclusion configuration is invalid");
            }
        }
        return Set.copyOf(exclusions);
    }

    private ReuseAnalysis analyze(KycReusableSnapshot source, Set<KycReuseField> exclusions, LocalDate today) {
        EnumSet<KycReuseField> reusable = EnumSet.noneOf(KycReuseField.class);
        if (source.legalName() != null && !source.legalName().isBlank()) {
            reusable.add(KycReuseField.LEGAL_NAME);
        }
        if (source.legalIdentifierCiphertext() != null && source.legalIdentifierHash() != null && source
            .legalIdentifierHashKeyVersion() != null && source.legalIdentifierMasked() != null && source
                .legalIdentifierKeyVersion() != null) {
            reusable.add(KycReuseField.LEGAL_IDENTIFIER);
        }
        if (source.licenseIssueDate() != null && source.licenseExpiryDate() != null && !source.licenseIssueDate()
            .isAfter(source.licenseExpiryDate()) && !source.licenseIssueDate().isAfter(today) && !source
                .licenseExpiryDate()
                .isBefore(today)) {
            reusable.add(KycReuseField.LICENSE_DATES);
        }
        if (source.businessScope() != null && !source.businessScope().isBlank()) {
            reusable.add(KycReuseField.BUSINESS_SCOPE);
        }
        reusable.retainAll(GLOBAL_ALLOWLIST);
        reusable.removeAll(exclusions);
        EnumSet<KycReuseField> reconfirm = EnumSet.copyOf(GLOBAL_ALLOWLIST);
        reconfirm.removeAll(reusable);
        return new ReuseAnalysis(Set.copyOf(reusable), reconfirm.stream().sorted().toList());
    }

    private KycReuseSourceView toView(KycReusableSnapshot source, ReuseAnalysis analysis) {
        return new KycReuseSourceView(source.id(), source.versionNo(), source.sourceChannelCode(), source
            .requirementVersion(), source.sourceTime(), source.legalIdentifierMasked(), analysis.reusableFields()
                .stream()
                .sorted()
                .toList(), analysis.reconfirmationFields());
    }

    private String writeProvenance(KycReusableSnapshot source,
                                   OnboardingDraft target,
                                   List<KycReuseField> copied,
                                   List<KycReuseField> reconfirm) {
        Map<String, Object> provenance = new LinkedHashMap<>();
        provenance.put("policyVersion", POLICY_VERSION);
        provenance.put("sourceKycVersionId", source.id());
        provenance.put("sourceVersionNo", source.versionNo());
        provenance.put("sourceChannelCode", source.sourceChannelCode());
        provenance.put("sourceRequirementVersion", source.requirementVersion());
        provenance.put("sourceUpdateTime", source.sourceTime());
        provenance.put("targetRequirementVersion", target.requirementVersion());
        provenance.put("copiedFields", copied);
        provenance.put("fieldsRequiringReconfirmation", reconfirm);
        try {
            return objectMapper.writeValueAsString(provenance);
        } catch (JsonProcessingException ex) {
            throw new MerchantDomainException("KYC reuse provenance serialization failed");
        }
    }

    private record ReuseAnalysis(Set<KycReuseField> reusableFields, List<KycReuseField> reconfirmationFields) {
    }
}
