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

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.merchant.kyc.attachment.KycAttachment;
import top.continew.admin.merchant.kyc.attachment.KycAttachmentDraft;
import top.continew.admin.merchant.kyc.attachment.KycAttachmentRepository;
import top.continew.admin.merchant.master.application.MerchantScopeAuthorizationService;
import top.continew.admin.merchant.master.domain.Merchant;
import top.continew.admin.merchant.master.domain.MerchantDomainException;
import top.continew.admin.merchant.review.application.OnboardingReviewContext;
import top.continew.admin.merchant.review.application.OnboardingReviewRepository;
import top.continew.admin.merchant.security.audit.application.SecurityAuditWriter;
import top.continew.admin.merchant.security.audit.domain.SecurityAuditRecord;
import top.continew.admin.merchant.security.audit.domain.SecurityAuditResult;
import top.continew.admin.workflow.api.WorkflowMappingService;
import top.continew.admin.workflow.api.WorkflowService;
import top.continew.admin.workflow.dto.WorkflowInstanceMapping;
import top.continew.admin.workflow.dto.WorkflowTask;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Creates task-linked supplement versions and reports category-only diffs without decrypting sensitive payloads. */
@Service
public class OnboardingSupplementService {

    private final MerchantScopeAuthorizationService merchantScopeAuthorizationService;
    private final WorkflowService workflowService;
    private final WorkflowMappingService mappingService;
    private final OnboardingReviewRepository reviewRepository;
    private final OnboardingSupplementRepository supplementRepository;
    private final OnboardingDraftRepository draftRepository;
    private final OnboardingDraftService draftService;
    private final KycAttachmentRepository attachmentRepository;
    private final OperatingPlatformRepository platformRepository;
    private final IdentifierGenerator identifierGenerator;
    private final SecurityAuditWriter securityAuditWriter;
    private final Clock clock = Clock.systemDefaultZone();

    public OnboardingSupplementService(MerchantScopeAuthorizationService merchantScopeAuthorizationService,
                                       WorkflowService workflowService,
                                       WorkflowMappingService mappingService,
                                       OnboardingReviewRepository reviewRepository,
                                       OnboardingSupplementRepository supplementRepository,
                                       OnboardingDraftRepository draftRepository,
                                       OnboardingDraftService draftService,
                                       KycAttachmentRepository attachmentRepository,
                                       OperatingPlatformRepository platformRepository,
                                       IdentifierGenerator identifierGenerator,
                                       SecurityAuditWriter securityAuditWriter) {
        this.merchantScopeAuthorizationService = merchantScopeAuthorizationService;
        this.workflowService = workflowService;
        this.mappingService = mappingService;
        this.reviewRepository = reviewRepository;
        this.supplementRepository = supplementRepository;
        this.draftRepository = draftRepository;
        this.draftService = draftService;
        this.attachmentRepository = attachmentRepository;
        this.platformRepository = platformRepository;
        this.identifierGenerator = identifierGenerator;
        this.securityAuditWriter = securityAuditWriter;
    }

    @Transactional
    public OnboardingSupplementDraft create(Long tenantId,
                                            Long actorUserId,
                                            Long merchantId,
                                            Long applicationId,
                                            String taskId,
                                            Long previousKycVersionId,
                                            String ipAddress) {
        Merchant merchant = merchantScopeAuthorizationService.requireAccessible(tenantId, actorUserId, merchantId);
        WorkflowTask task = workflowService.task(tenantId, actorUserId, taskId);
        if (!actorUserId.toString().equals(task.assignee()) || !"supplementTask".equals(task.taskDefinitionKey())) {
            throw new MerchantDomainException("A claimed supplement task is required");
        }
        WorkflowInstanceMapping mapping = mappingService.findByProcessInstanceId(tenantId, task.processInstanceId())
            .orElseThrow(() -> new MerchantDomainException("Workflow mapping is unavailable"));
        if (!"MERCHANT_ONBOARDING".equals(mapping.businessType()) || !applicationId.equals(mapping.businessId())) {
            throw new MerchantDomainException("Supplement task business reference is invalid");
        }
        OnboardingReviewContext context = reviewRepository.findContext(tenantId, applicationId)
            .orElseThrow(() -> new MerchantDomainException("Supplement application is unavailable"));
        if (!merchantId.equals(context.merchantId())) {
            throw new MerchantDomainException("Supplement merchant reference is invalid");
        }
        if (!"SUPPLEMENT_REQUIRED".equals(context.applicationStatus())) {
            throw new MerchantDomainException("Supplement application status is invalid");
        }
        Long existingId = supplementRepository.findByTask(tenantId, applicationId, taskId).orElse(null);
        if (existingId != null) {
            return result(tenantId, actorUserId, merchantId, applicationId, previousKycVersionId, existingId, taskId);
        }
        SupplementKycSnapshot source = supplementRepository
            .find(tenantId, merchantId, applicationId, previousKycVersionId)
            .orElseThrow(() -> new MerchantDomainException("Submitted KYC version is unavailable"));
        if (!"SUBMITTED".equals(source.status())) {
            throw new MerchantDomainException("Only a submitted KYC version can be supplemented");
        }
        Long newKycVersionId = identifierGenerator.nextId(new Object()).longValue();
        Integer newVersionNo = draftRepository.nextKycVersionNo(tenantId, merchantId);
        LocalDateTime now = LocalDateTime.now(clock);
        supplementRepository.copyVersion(newKycVersionId, newVersionNo, taskId, actorUserId, now, source);
        copyEvidenceAndPlatforms(tenantId, previousKycVersionId, newKycVersionId, actorUserId, now);
        if (!supplementRepository
            .replaceApplicationKyc(tenantId, applicationId, previousKycVersionId, newKycVersionId, context
                .rowVersion(), actorUserId, now)) {
            throw new MerchantDomainException("Supplement application changed concurrently");
        }
        securityAuditWriter.append(new SecurityAuditRecord(tenantId, actorUserId, merchant
            .owningAgentId(), "KYC_SUPPLEMENT_CREATE", "KYC_VERSION", newKycVersionId, 0L, "PREVIOUS_VERSION", "previousKycVersionId=%s;taskId=%s"
                .formatted(previousKycVersionId, taskId), ipAddress, SecurityAuditResult.SUCCESS, null, now));
        return result(tenantId, actorUserId, merchantId, applicationId, previousKycVersionId, newKycVersionId, taskId);
    }

    public OnboardingSupplementDiff diff(Long tenantId,
                                         Long actorUserId,
                                         Long merchantId,
                                         Long applicationId,
                                         Long currentKycVersionId) {
        merchantScopeAuthorizationService.requireAccessible(tenantId, actorUserId, merchantId);
        SupplementKycSnapshot current = supplementRepository
            .find(tenantId, merchantId, applicationId, currentKycVersionId)
            .orElseThrow(() -> new MerchantDomainException("Supplement KYC version is unavailable"));
        if (current.previousVersionId() == null) {
            throw new MerchantDomainException("KYC version is not a supplement");
        }
        SupplementKycSnapshot previous = supplementRepository.find(tenantId, merchantId, applicationId, current
            .previousVersionId()).orElseThrow(() -> new MerchantDomainException("Previous KYC version is unavailable"));
        return compare(tenantId, previous, current);
    }

    private OnboardingSupplementDraft result(Long tenantId,
                                             Long actorUserId,
                                             Long merchantId,
                                             Long applicationId,
                                             Long previousKycVersionId,
                                             Long currentKycVersionId,
                                             String taskId) {
        return new OnboardingSupplementDraft(draftService
            .load(tenantId, actorUserId, merchantId, applicationId), previousKycVersionId, taskId, diff(tenantId, actorUserId, merchantId, applicationId, currentKycVersionId));
    }

    private void copyEvidenceAndPlatforms(Long tenantId,
                                          Long sourceKycVersionId,
                                          Long targetKycVersionId,
                                          Long actorUserId,
                                          LocalDateTime now) {
        Map<Long, Long> attachmentIds = new HashMap<>();
        for (KycAttachment source : attachmentRepository.listByKycVersion(tenantId, sourceKycVersionId)) {
            KycAttachment copied = attachmentRepository
                .insert(new KycAttachmentDraft(tenantId, targetKycVersionId, source.evidenceType(), source
                    .storageObjectId(), source.originalName(), source.extension(), source.declaredMime(), source
                        .detectedMime(), source.sizeBytes(), source.sha256(), source.scanStatus(), source
                            .validationStatus(), source.sort(), now));
            attachmentIds.put(source.id(), copied.id());
        }
        for (OperatingPlatform source : platformRepository.list(tenantId, sourceKycVersionId)) {
            Long platformId = identifierGenerator.nextId(new Object()).longValue();
            platformRepository.insert(platformId, tenantId, targetKycVersionId, source.platformCode(), source
                .storeName(), source.storeUrl(), source.storeIdentifier(), source
                    .certificationStatus(), actorUserId, now);
            for (OperatingPlatform.ProofAttachment proof : source.proofAttachments()) {
                Long copiedAttachmentId = attachmentIds.get(proof.attachmentId());
                if (copiedAttachmentId != null) {
                    platformRepository.linkProof(identifierGenerator.nextId(new Object())
                        .longValue(), tenantId, targetKycVersionId, platformId, copiedAttachmentId, proof
                            .evidenceType(), actorUserId, now);
                }
            }
        }
    }

    private OnboardingSupplementDiff compare(Long tenantId,
                                             SupplementKycSnapshot previous,
                                             SupplementKycSnapshot current) {
        List<String> fields = new ArrayList<>();
        changed(fields, "LEGAL_NAME", previous.legalName(), current.legalName());
        changed(fields, "LEGAL_IDENTIFIER", previous.legalIdentifierMasked(), current.legalIdentifierMasked());
        changed(fields, "LICENSE_DATES", Arrays.asList(previous.licenseIssueDate(), previous
            .licenseExpiryDate()), Arrays.asList(current.licenseIssueDate(), current.licenseExpiryDate()));
        changed(fields, "BUSINESS_SCOPE", previous.businessScope(), current.businessScope());
        changed(fields, "ADDRESS", previous.addressPayload(), current.addressPayload());
        changed(fields, "PERSONS", previous.personPayload(), current.personPayload());
        changed(fields, "SHAREHOLDERS", previous.shareholderPayload(), current.shareholderPayload());
        changed(fields, "PRICING", previous.pricingVersionId(), current.pricingVersionId());
        changed(fields, "SETTLEMENT", Arrays.asList(previous.settlementAccountMasked(), previous
            .settlementMode(), previous.settlementVerificationStatus()), Arrays.asList(current
                .settlementAccountMasked(), current.settlementMode(), current.settlementVerificationStatus()));
        List<OnboardingSupplementDiff.AttachmentChange> attachments = attachmentChanges(attachmentRepository
            .listByKycVersion(tenantId, previous.id()), attachmentRepository.listByKycVersion(tenantId, current.id()));
        List<OnboardingSupplementDiff.PlatformChange> platforms = platformChanges(platformRepository
            .list(tenantId, previous.id()), platformRepository.list(tenantId, current.id()));
        return new OnboardingSupplementDiff(previous.id(), current.id(), fields, attachments, platforms);
    }

    private void changed(List<String> fields, String field, Object previous, Object current) {
        boolean equal = previous instanceof byte[] left && current instanceof byte[] right
            ? Arrays.equals(left, right)
            : Objects.deepEquals(previous, current);
        if (!equal)
            fields.add(field);
    }

    private List<OnboardingSupplementDiff.AttachmentChange> attachmentChanges(List<KycAttachment> previous,
                                                                              List<KycAttachment> current) {
        Set<String> left = previous.stream().map(this::attachmentKey).collect(Collectors.toSet());
        Set<String> right = current.stream().map(this::attachmentKey).collect(Collectors.toSet());
        List<OnboardingSupplementDiff.AttachmentChange> changes = new ArrayList<>();
        previous.stream()
            .filter(item -> !right.contains(attachmentKey(item)))
            .forEach(item -> changes.add(new OnboardingSupplementDiff.AttachmentChange(item.evidenceType(), item
                .originalName(), "REMOVED")));
        current.stream()
            .filter(item -> !left.contains(attachmentKey(item)))
            .forEach(item -> changes.add(new OnboardingSupplementDiff.AttachmentChange(item.evidenceType(), item
                .originalName(), "ADDED")));
        return changes.stream()
            .sorted(Comparator.comparing(OnboardingSupplementDiff.AttachmentChange::evidenceType)
                .thenComparing(OnboardingSupplementDiff.AttachmentChange::originalName))
            .toList();
    }

    private String attachmentKey(KycAttachment item) {
        return item.evidenceType() + ":" + item.sha256();
    }

    private List<OnboardingSupplementDiff.PlatformChange> platformChanges(List<OperatingPlatform> previous,
                                                                          List<OperatingPlatform> current) {
        Map<String, OperatingPlatform> left = previous.stream()
            .collect(Collectors.toMap(this::platformKey, item -> item));
        Map<String, OperatingPlatform> right = current.stream()
            .collect(Collectors.toMap(this::platformKey, item -> item));
        List<OnboardingSupplementDiff.PlatformChange> changes = new ArrayList<>();
        left.forEach((key, item) -> {
            if (!right.containsKey(key))
                changes.add(new OnboardingSupplementDiff.PlatformChange(item.platformCode(), item
                    .storeIdentifier(), "REMOVED"));
        });
        right.forEach((key, item) -> {
            if (!left.containsKey(key))
                changes.add(new OnboardingSupplementDiff.PlatformChange(item.platformCode(), item
                    .storeIdentifier(), "ADDED"));
        });
        return changes;
    }

    private String platformKey(OperatingPlatform item) {
        return item.platformCode() + ":" + item.storeIdentifier() + ":" + item.storeName() + ":" + item
            .certificationStatus();
    }
}
