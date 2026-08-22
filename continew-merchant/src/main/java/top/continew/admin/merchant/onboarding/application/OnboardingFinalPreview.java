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

import top.continew.admin.merchant.agent.domain.AgentPricingRules;
import top.continew.admin.merchant.master.domain.MerchantStatus;
import top.continew.admin.merchant.master.domain.MerchantType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Read-only, masked preview of the exact saved onboarding business version. */
public record OnboardingFinalPreview(Long applicationId, String applicationNo, Long businessVersion,
                                     MerchantSummary merchant, ChannelSummary channel, KycSummary kyc,
                                     OnboardingEvidenceSummary evidence, SettlementSummary settlement,
                                     PricingSummary pricing, List<OperatingPlatformSummary> operatingPlatforms,
                                     boolean draftStepsComplete, boolean readyForSubmission,
                                     List<PreviewBlocker> blockers, LocalDateTime previewTime) {

    public OnboardingFinalPreview {
        operatingPlatforms = List.copyOf(operatingPlatforms);
        blockers = List.copyOf(blockers);
    }

    public record MerchantSummary(Long merchantId, String merchantNo, MerchantType merchantType, String legalName,
                                  String shortName, Long owningAgentId, MerchantStatus status,
                                  String contactMobileMasked, String reviewerMobileMasked, Long merchantVersion) {
    }

    public record ChannelSummary(String channelCode, String productCode, String savedChannelConfigVersion,
                                 String savedRequirementVersion, String currentChannelConfigVersion,
                                 String currentRequirementVersion, boolean eligible, boolean requirementCurrent) {
    }

    public record KycSummary(Long kycVersionId, Integer kycVersionNo, Long rowVersion, String status, String legalName,
                             String legalIdentifierMasked, LocalDate licenseIssueDate, LocalDate licenseExpiryDate,
                             String businessScope, boolean profileComplete, boolean licenseValid) {
    }

    public record SettlementSummary(SettlementAccountVerificationPort.SettlementMode mode, String accountNumberMasked,
                                    SettlementAccountVerificationPort.SettlementVerificationStatus verificationStatus,
                                    String verificationReference, String verifierVersion, LocalDateTime verifiedTime,
                                    boolean verified) {
    }

    public record PricingSummary(Long pricingVersionId, Integer versionNo, String channelCode, String productCode,
                                 String currency, AgentPricingRules rules, LocalDateTime effectiveTime,
                                 LocalDateTime expiresTime, boolean valid) {
    }

    public record OperatingPlatformSummary(Long platformId, String platformCode, String storeName,
                                           String storeIdentifier,
                                           OperatingPlatform.CertificationStatus certificationStatus, int proofCount,
                                           int cleanProofCount, boolean complete, List<PlatformProofSummary> proofs) {

        public OperatingPlatformSummary {
            proofs = List.copyOf(proofs);
        }
    }

    public record PlatformProofSummary(Long attachmentId, String evidenceType, String scanStatus,
                                       String validationStatus) {
    }

    public record PreviewBlocker(String code, String message, List<String> references) {

        public PreviewBlocker {
            references = List.copyOf(references);
        }
    }
}
