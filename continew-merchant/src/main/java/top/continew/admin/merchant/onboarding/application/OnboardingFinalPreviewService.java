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

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.merchant.agent.application.AgentPricingRepository;
import top.continew.admin.merchant.agent.domain.AgentPricingBoundaryException;
import top.continew.admin.merchant.agent.domain.AgentPricingVersion;
import top.continew.admin.merchant.master.application.MerchantScopeAuthorizationService;
import top.continew.admin.merchant.master.domain.Merchant;
import top.continew.admin.merchant.master.domain.MerchantAccessDeniedException;
import top.continew.admin.merchant.master.domain.MerchantDomainException;
import top.continew.admin.merchant.master.domain.MerchantType;
import top.continew.starter.extension.tenant.context.TenantContextHolder;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Builds a side-effect-free final confirmation preview from exact saved version references. */
@Service
@RequiredArgsConstructor
public class OnboardingFinalPreviewService {

    private static final List<Integer> ALL_STEPS = List.of(1, 2, 3, 4, 5);

    private final MerchantScopeAuthorizationService merchantScopeAuthorizationService;
    private final OnboardingDraftRepository draftRepository;
    private final OnboardingPreviewRepository previewRepository;
    private final ChannelEligibilityService channelEligibilityService;
    private final OnboardingEvidenceService evidenceService;
    private final OnboardingPricingValidator pricingValidator;
    private final AgentPricingRepository pricingRepository;
    private final OperatingPlatformRepository operatingPlatformRepository;
    private final Clock clock = Clock.systemDefaultZone();

    @Transactional(readOnly = true)
    public OnboardingFinalPreview preview(Long tenantId, Long actorUserId, Long merchantId, Long applicationId) {
        requireTenant(tenantId);
        Merchant merchant = merchantScopeAuthorizationService.requireAccessible(tenantId, actorUserId, merchantId);
        OnboardingDraft draft = draftRepository.findByApplicationId(tenantId, merchantId, applicationId)
            .orElseThrow(MerchantAccessDeniedException::new);
        OnboardingPreviewSnapshot snapshot = previewRepository.findSavedKyc(tenantId, merchantId, applicationId, draft
            .kycVersionId()).orElseThrow(MerchantAccessDeniedException::new);
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        List<OnboardingFinalPreview.PreviewBlocker> blockers = new ArrayList<>();

        EligibleChannel currentChannel = currentChannel(tenantId, actorUserId, merchantId, draft);
        boolean channelEligible = currentChannel != null;
        if (!channelEligible) {
            blockers.add(blocker("CHANNEL_INELIGIBLE", "The saved channel product is no longer eligible", draft
                .channelCode(), draft.productCode()));
        }
        boolean requirementCurrent = currentChannel != null && draft.channelConfigVersion()
            .equals(currentChannel.channelConfigVersion()) && draft.requirementVersion()
                .equals(currentChannel.requirementVersion()) && draft.requirementSummary()
                    .equals(currentChannel.requirements());
        if (channelEligible && !requirementCurrent) {
            blockers
                .add(blocker("REQUIREMENTS_CHANGED", "The saved channel requirement version is no longer current", draft
                    .requirementVersion(), currentChannel.requirementVersion()));
        }

        OnboardingEvidenceSummary evidence = evidenceService.summary(tenantId, actorUserId, merchantId, applicationId);
        if (!evidence.complete()) {
            List<String> incompleteEvidence = evidence.evidenceTypes()
                .stream()
                .filter(type -> type.required() && !type.complete())
                .map(OnboardingEvidenceSummary.EvidenceTypeStatus::evidenceType)
                .toList();
            blockers
                .add(new OnboardingFinalPreview.PreviewBlocker("EVIDENCE_INCOMPLETE", "Required evidence is missing or not clean", incompleteEvidence));
        }

        boolean profileComplete = profileComplete(snapshot, merchant.merchantType());
        if (!profileComplete) {
            blockers.add(blocker("KYC_PROFILE_INCOMPLETE", "The exact saved KYC profile is incomplete", String
                .valueOf(snapshot.kycVersionId())));
        }
        boolean licenseValid = licenseValid(snapshot, today);
        if (profileComplete && !licenseValid) {
            blockers.add(blocker("LICENSE_EXPIRED", "The saved business license is expired or invalid", String
                .valueOf(snapshot.kycVersionId())));
        }

        boolean settlementVerified = SettlementAccountVerificationPort.SettlementVerificationStatus.VERIFIED
            .equals(snapshot.settlementStatus()) && snapshot.settlementMode() != null && snapshot
                .settlementAccountMasked() != null && snapshot.settlementPayloadPresent() && snapshot
                    .settlementVerifiedTime() != null;
        if (!settlementVerified) {
            blockers.add(blocker("SETTLEMENT_NOT_VERIFIED", "The saved settlement account is not verified", snapshot
                .settlementStatus() == null ? "NOT_SAVED" : snapshot.settlementStatus().name()));
        }

        AgentPricingVersion pricing = snapshot.pricingVersionId() == null
            ? null
            : pricingRepository.findById(tenantId, snapshot.pricingVersionId()).orElse(null);
        boolean pricingValid = pricing != null && pricingValid(tenantId, merchant, draft, snapshot
            .pricingVersionId(), now);
        if (!pricingValid) {
            blockers
                .add(blocker("PRICING_INVALID", "The exact saved pricing version no longer satisfies the current boundary", String
                    .valueOf(snapshot.pricingVersionId())));
        }

        List<OnboardingFinalPreview.OperatingPlatformSummary> platforms = operatingPlatformRepository
            .list(tenantId, snapshot.kycVersionId())
            .stream()
            .map(this::platformSummary)
            .toList();
        boolean platformsComplete = !platforms.isEmpty() && platforms.stream()
            .allMatch(OnboardingFinalPreview.OperatingPlatformSummary::complete);
        if (!platformsComplete) {
            List<String> incompletePlatforms = platforms.isEmpty()
                ? List.of("NO_PLATFORM")
                : platforms.stream()
                    .filter(platform -> !platform.complete())
                    .map(platform -> platform.platformCode() + ":" + platform.storeIdentifier())
                    .toList();
            blockers
                .add(new OnboardingFinalPreview.PreviewBlocker("PLATFORM_PROOF_INCOMPLETE", "Operating platform proof is incomplete", incompletePlatforms));
        }

        boolean draftStepsComplete = Integer.valueOf(5).equals(draft.savedStep()) && ALL_STEPS.equals(draft
            .completedSteps());
        if (!draftStepsComplete) {
            blockers.add(blocker("DRAFT_STEPS_INCOMPLETE", "The five saved onboarding steps are not complete", String
                .valueOf(draft.savedStep())));
        }

        return new OnboardingFinalPreview(draft.applicationId(), draft.applicationNo(), snapshot
            .rowVersion(), merchantSummary(merchant), channelSummary(draft, currentChannel, channelEligible, requirementCurrent), new OnboardingFinalPreview.KycSummary(snapshot
                .kycVersionId(), snapshot.kycVersionNo(), snapshot.rowVersion(), snapshot.status(), snapshot
                    .legalName(), snapshot.legalIdentifierMasked(), snapshot.licenseIssueDate(), snapshot
                        .licenseExpiryDate(), snapshot
                            .businessScope(), profileComplete, licenseValid), evidence, new OnboardingFinalPreview.SettlementSummary(snapshot
                                .settlementMode(), snapshot.settlementAccountMasked(), snapshot
                                    .settlementStatus(), snapshot.settlementVerificationReference(), snapshot
                                        .settlementVerifierVersion(), snapshot
                                            .settlementVerifiedTime(), settlementVerified), pricingSummary(pricing, snapshot
                                                .pricingVersionId(), pricingValid), platforms, draftStepsComplete, blockers
                                                    .isEmpty(), blockers, now);
    }

    private EligibleChannel currentChannel(Long tenantId, Long actorUserId, Long merchantId, OnboardingDraft draft) {
        try {
            return channelEligibilityService.list(tenantId, actorUserId, merchantId)
                .stream()
                .filter(channel -> draft.channelCode().equals(channel.channelCode()) && draft.productCode()
                    .equals(channel.productCode()))
                .findFirst()
                .orElse(null);
        } catch (MerchantDomainException ex) {
            return null;
        }
    }

    private boolean profileComplete(OnboardingPreviewSnapshot snapshot, MerchantType merchantType) {
        return text(snapshot.legalName()) && text(snapshot.legalIdentifierMasked()) && snapshot
            .licenseIssueDate() != null && snapshot.licenseExpiryDate() != null && !snapshot.licenseIssueDate()
                .isAfter(snapshot.licenseExpiryDate()) && text(snapshot.businessScope()) && snapshot
                    .addressPresent() && snapshot.personsPresent() && (!MerchantType.ENTERPRISE
                        .equals(merchantType) || snapshot.shareholdersPresent());
    }

    private boolean licenseValid(OnboardingPreviewSnapshot snapshot, LocalDate today) {
        return snapshot.licenseIssueDate() != null && snapshot.licenseExpiryDate() != null && !snapshot
            .licenseIssueDate()
            .isAfter(snapshot.licenseExpiryDate()) && !snapshot.licenseExpiryDate().isBefore(today);
    }

    private boolean pricingValid(Long tenantId,
                                 Merchant merchant,
                                 OnboardingDraft draft,
                                 Long pricingVersionId,
                                 LocalDateTime now) {
        try {
            pricingValidator.requireValid(tenantId, merchant, draft, pricingVersionId, now);
            return true;
        } catch (AgentPricingBoundaryException ex) {
            return false;
        }
    }

    private OnboardingFinalPreview.MerchantSummary merchantSummary(Merchant merchant) {
        return new OnboardingFinalPreview.MerchantSummary(merchant.id(), merchant.merchantNo(), merchant
            .merchantType(), merchant.legalName(), merchant.shortName(), merchant.owningAgentId(), merchant
                .status(), merchant.contactMobile() == null ? null : merchant.contactMobile().maskedValue(), merchant
                    .reviewerMobile() == null ? null : merchant.reviewerMobile().maskedValue(), merchant.rowVersion());
    }

    private OnboardingFinalPreview.ChannelSummary channelSummary(OnboardingDraft draft,
                                                                 EligibleChannel current,
                                                                 boolean eligible,
                                                                 boolean requirementCurrent) {
        return new OnboardingFinalPreview.ChannelSummary(draft.channelCode(), draft.productCode(), draft
            .channelConfigVersion(), draft.requirementVersion(), current == null
                ? null
                : current.channelConfigVersion(), current == null
                    ? null
                    : current.requirementVersion(), eligible, requirementCurrent);
    }

    private OnboardingFinalPreview.PricingSummary pricingSummary(AgentPricingVersion pricing,
                                                                 Long pricingVersionId,
                                                                 boolean valid) {
        return pricing == null
            ? new OnboardingFinalPreview.PricingSummary(pricingVersionId, null, null, null, null, null, null, null, false)
            : new OnboardingFinalPreview.PricingSummary(pricing.id(), pricing.versionNo(), pricing
                .channelCode(), pricing.productCode(), pricing.currency(), pricing.rules(), pricing
                    .effectiveTime(), pricing.expiresTime(), valid);
    }

    private OnboardingFinalPreview.OperatingPlatformSummary platformSummary(OperatingPlatform platform) {
        List<OnboardingFinalPreview.PlatformProofSummary> proofs = platform.proofAttachments()
            .stream()
            .map(proof -> new OnboardingFinalPreview.PlatformProofSummary(proof.attachmentId(), proof
                .evidenceType(), proof.scanStatus(), proof.validationStatus()))
            .toList();
        int cleanProofs = (int)proofs.stream()
            .filter(proof -> "CLEAN".equals(proof.scanStatus()) && "VALID".equals(proof.validationStatus()))
            .count();
        boolean complete = OperatingPlatform.CertificationStatus.CERTIFIED.equals(platform
            .certificationStatus()) && !proofs.isEmpty() && cleanProofs == proofs.size();
        return new OnboardingFinalPreview.OperatingPlatformSummary(platform.id(), platform.platformCode(), platform
            .storeName(), platform.storeIdentifier(), platform.certificationStatus(), proofs
                .size(), cleanProofs, complete, proofs);
    }

    private OnboardingFinalPreview.PreviewBlocker blocker(String code, String message, String... references) {
        return new OnboardingFinalPreview.PreviewBlocker(code, message, List.of(references));
    }

    private boolean text(String value) {
        return value != null && !value.isBlank();
    }

    private void requireTenant(Long tenantId) {
        if (tenantId == null || !tenantId.equals(TenantContextHolder.getTenantId())) {
            throw new MerchantAccessDeniedException();
        }
    }
}
