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

package top.continew.admin;

import org.junit.jupiter.api.Test;
import org.flowable.engine.ProcessEngine;
import org.flowable.eventregistry.impl.EventRegistryEngine;
import org.flowable.idm.engine.IdmEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import top.continew.admin.merchant.agent.application.AgentHierarchyService;
import top.continew.admin.merchant.agent.application.AgentMerchantDefaultCreateCommand;
import top.continew.admin.merchant.agent.application.AgentMerchantDefaultService;
import top.continew.admin.merchant.agent.application.AgentPricingCreateCommand;
import top.continew.admin.merchant.agent.application.AgentPricingService;
import top.continew.admin.merchant.agent.application.AgentPromotionCodeService;
import top.continew.admin.merchant.agent.application.AgentPromotionCodeView;
import top.continew.admin.merchant.agent.application.AgentRepository;
import top.continew.admin.merchant.agent.application.AgentListQuery;
import top.continew.admin.merchant.agent.application.AgentPage;
import top.continew.admin.merchant.agent.application.AgentQueryService;
import top.continew.admin.merchant.agent.application.AgentScopeAuthorizationService;
import top.continew.admin.merchant.agent.application.AgentSummary;
import top.continew.admin.merchant.agent.application.PromotionOwnership;
import top.continew.admin.merchant.agent.domain.Agent;
import top.continew.admin.merchant.agent.domain.AgentAccessDeniedException;
import top.continew.admin.merchant.agent.domain.AgentConcurrentModificationException;
import top.continew.admin.merchant.agent.domain.AgentDomainException;
import top.continew.admin.merchant.agent.domain.AgentMerchantDefaultProduct;
import top.continew.admin.merchant.agent.domain.AgentMerchantDefaultVersion;
import top.continew.admin.merchant.agent.domain.AgentPricingBoundaryException;
import top.continew.admin.merchant.agent.domain.AgentPricingRules;
import top.continew.admin.merchant.agent.domain.AgentPricingVersion;
import top.continew.admin.merchant.agent.domain.AgentPromotionCodeStatus;
import top.continew.admin.merchant.agent.domain.AgentRegistration;
import top.continew.admin.merchant.agent.domain.AgentStatus;
import top.continew.admin.merchant.agent.domain.KycDraftDefaultSnapshot;
import top.continew.admin.merchant.agent.domain.PromotionOwnershipDeniedException;
import top.continew.admin.merchant.master.application.MerchantAction;
import top.continew.admin.merchant.master.application.MerchantActionPermissions;
import top.continew.admin.merchant.master.application.MerchantChannelSummary;
import top.continew.admin.merchant.master.application.MerchantDetail;
import top.continew.admin.merchant.master.application.MerchantListQuery;
import top.continew.admin.merchant.master.application.MerchantMasterService;
import top.continew.admin.merchant.master.application.MerchantOperationPolicyService;
import top.continew.admin.merchant.master.application.MerchantOperationPolicyService.MerchantOperation;
import top.continew.admin.merchant.master.application.MerchantPage;
import top.continew.admin.merchant.master.application.MerchantQueryService;
import top.continew.admin.merchant.master.application.MerchantRepository;
import top.continew.admin.merchant.master.application.MerchantScopeAuthorizationService;
import top.continew.admin.merchant.master.domain.Merchant;
import top.continew.admin.merchant.master.domain.MerchantAccessDeniedException;
import top.continew.admin.merchant.master.domain.MerchantConcurrentModificationException;
import top.continew.admin.merchant.master.domain.MerchantDuplicateLegalSubjectException;
import top.continew.admin.merchant.master.domain.MerchantDomainException;
import top.continew.admin.merchant.master.domain.MerchantRegistration;
import top.continew.admin.merchant.master.domain.MerchantStatus;
import top.continew.admin.merchant.master.domain.MerchantType;
import top.continew.admin.merchant.onboarding.application.ChannelEligibilityService;
import top.continew.admin.merchant.onboarding.application.EligibleChannel;
import top.continew.admin.merchant.onboarding.application.KycReuseField;
import top.continew.admin.merchant.onboarding.application.KycReuseService;
import top.continew.admin.merchant.onboarding.application.KycReuseSourceView;
import top.continew.admin.merchant.onboarding.application.OnboardingDraftConflictException;
import top.continew.admin.merchant.onboarding.application.OnboardingDraftService;
import top.continew.admin.merchant.onboarding.application.OnboardingDraftView;
import top.continew.admin.merchant.onboarding.application.OnboardingEvidenceService;
import top.continew.admin.merchant.onboarding.application.OnboardingEvidenceSummary;
import top.continew.admin.merchant.kyc.attachment.KycAttachment;
import top.continew.admin.merchant.kyc.attachment.KycAttachmentDraft;
import top.continew.admin.merchant.kyc.attachment.KycAttachmentException;
import top.continew.admin.merchant.kyc.attachment.KycAttachmentRepository;
import top.continew.admin.merchant.kyc.attachment.KycAttachmentScanStatus;
import top.continew.admin.merchant.kyc.attachment.KycAttachmentValidationStatus;
import top.continew.admin.merchant.kyc.attachment.KycVersionOwnershipRepository;
import top.continew.admin.merchant.security.value.EncryptedMobileNumber;
import top.continew.admin.merchant.security.audit.application.SecurityAuditWriter;
import top.continew.admin.merchant.security.audit.domain.SecurityAuditRecord;
import top.continew.admin.merchant.security.audit.domain.SecurityAuditResult;
import top.continew.admin.system.config.file.FileStorageConfigLoader;
import top.continew.admin.system.config.sms.SmsConfigLoader;
import top.continew.admin.service.merchant.SubordinateAgentCreateCommand;
import top.continew.admin.service.merchant.AgentAdministrationService;
import top.continew.admin.service.merchant.MerchantCreateCommand;
import top.continew.admin.service.merchant.MerchantAdministrationService;
import top.continew.admin.service.merchant.MerchantProvisioningResult;
import top.continew.admin.service.merchant.MerchantProvisioningService;
import top.continew.admin.service.merchant.MerchantReverificationRoutingService;
import top.continew.admin.service.merchant.MerchantReverificationRoutingService.MerchantReverificationChangeType;
import top.continew.admin.service.merchant.SubordinateAgentProvisioningResult;
import top.continew.admin.service.merchant.SubordinateAgentProvisioningService;
import top.continew.admin.auth.service.OnlineUserService;
import top.continew.starter.extension.tenant.util.TenantUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@SpringBootTest
abstract class AbstractApplicationIT {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ProcessEngine processEngine;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    private AgentHierarchyService agentHierarchyService;

    @Autowired
    private AgentScopeAuthorizationService agentScopeAuthorizationService;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private AgentQueryService agentQueryService;

    @Autowired
    private AgentPromotionCodeService agentPromotionCodeService;

    @Autowired
    private AgentPricingService agentPricingService;

    @Autowired
    private AgentMerchantDefaultService agentMerchantDefaultService;

    @Autowired
    private MerchantMasterService merchantMasterService;

    @Autowired
    private MerchantScopeAuthorizationService merchantScopeAuthorizationService;

    @Autowired
    private MerchantQueryService merchantQueryService;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private SecurityAuditWriter securityAuditWriter;

    @Autowired
    private KycVersionOwnershipRepository kycVersionOwnershipRepository;

    @Autowired
    private KycAttachmentRepository kycAttachmentRepository;

    @Autowired
    private SubordinateAgentProvisioningService subordinateAgentProvisioningService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AgentAdministrationService agentAdministrationService;

    @Autowired
    private MerchantProvisioningService merchantProvisioningService;

    @Autowired
    private MerchantOperationPolicyService merchantOperationPolicyService;

    @Autowired
    private MerchantAdministrationService merchantAdministrationService;

    @Autowired
    private MerchantReverificationRoutingService merchantReverificationRoutingService;

    @Autowired
    private ChannelEligibilityService channelEligibilityService;

    @Autowired
    private OnboardingDraftService onboardingDraftService;

    @Autowired
    private KycReuseService kycReuseService;

    @Autowired
    private OnboardingEvidenceService onboardingEvidenceService;

    @SpyBean
    private OnlineUserService onlineUserService;

    @MockBean
    private FileStorageConfigLoader fileStorageConfigLoader;

    @MockBean
    private SmsConfigLoader smsConfigLoader;

    @Test
    void contextLoads() {
        org.junit.jupiter.api.Assertions.assertNotNull(processEngine);
        org.junit.jupiter.api.Assertions.assertTrue(applicationContext.getBeansOfType(IdmEngine.class).isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(applicationContext.getBeansOfType(EventRegistryEngine.class)
            .isEmpty());
    }

    protected void seedRepresentativeQueryData() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM biz_merchant", Integer.class);
        if (count != null && count > 0) {
            return;
        }
        LocalDateTime baseTime = LocalDateTime.of(2026, 8, 20, 12, 0);
        List<Object[]> merchants = new ArrayList<>(5000);
        List<Object[]> applications = new ArrayList<>(5000);
        List<Object[]> outboxEvents = new ArrayList<>(5000);
        List<Object[]> channelEvents = new ArrayList<>(5000);
        String[] applicationStatuses = {"SUBMITTED", "UNDER_REVIEW", "SUPPLEMENT_REQUIRED", "CHANNEL_PROCESSING",
            "SUCCEEDED"};
        String[] processingStatuses = {"RECEIVED", "PROCESSED", "FAILED"};
        for (long i = 1; i <= 5000; i++) {
            long tenantId = i % 50;
            long agentId = i % 100;
            LocalDateTime eventTime = baseTime.minusSeconds(i);
            merchants.add(new Object[] {i, tenantId, agentId, "M" + i, "ENTERPRISE", "Legal " + i, "Merchant " + i,
                i + 10000, i + 20000, i % 2 == 0 ? "ENABLED" : "DISABLED", eventTime});
            applications.add(new Object[] {i + 10000, tenantId, "A" + i, i, agentId, "CH" + (i % 4), "REQ-1", i + 20000,
                "IDEMP-" + i, applicationStatuses[(int)(i % applicationStatuses.length)], eventTime, eventTime});
            outboxEvents.add(new Object[] {i + 20000, tenantId, "MERCHANT", i, 1, "MERCHANT_CHANGED", "OUTBOX-" + i,
                "{}", i % 3 == 0 ? "PENDING" : "PUBLISHED", eventTime, eventTime, eventTime});
            channelEvents.add(new Object[] {i + 30000, tenantId, "CH" + (i % 4), "CHANNEL-" + i, i + 10000, i,
                "SERIAL-" + i, "STATUS", "MAP-1", String.format("%064d", i), eventTime,
                processingStatuses[(int)(i % processingStatuses.length)], eventTime});
        }
        jdbcTemplate.batchUpdate("""
            INSERT INTO biz_merchant
            (id, tenant_id, owning_agent_id, merchant_no, merchant_type, legal_name, short_name,
             operator_user_id, reviewer_user_id, status, create_time)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, merchants);
        jdbcTemplate.batchUpdate("""
            INSERT INTO biz_onboarding_application
            (id, tenant_id, application_no, merchant_id, owning_agent_id, channel_code, requirement_version,
             kyc_version_id, idempotency_key, status, submitted_time, create_time)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, applications);
        jdbcTemplate.batchUpdate("""
            INSERT INTO biz_outbox_event
            (id, tenant_id, aggregate_type, aggregate_id, aggregate_version, event_type, event_key, payload_json,
             status, next_retry_time, occurred_time, create_time)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, outboxEvents);
        jdbcTemplate.batchUpdate("""
            INSERT INTO biz_channel_event
            (id, tenant_id, channel_code, event_key, application_id, merchant_id, business_serial, event_type,
             mapping_version, payload_hash, received_time, processing_status, create_time)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, channelEvents);
    }

    protected void verifyAgentHierarchyScope() {
        long tenantId = 901L;
        long rootId = 90001L;
        long childId = 90002L;
        long siblingId = 90003L;
        long grandchildId = 90004L;
        long rootUserId = 91001L;
        long childUserId = 91002L;

        TenantUtils.execute(902L, () -> agentHierarchyService
            .register(registration(91001L, 902L, 0L, 92001L, "OTHER-TENANT-ROOT")));
        TenantUtils.execute(tenantId, () -> {
            Agent root = agentHierarchyService.register(registration(rootId, tenantId, 0L, rootUserId, "AG-ROOT"));
            Agent child = agentHierarchyService
                .register(registration(childId, tenantId, rootId, childUserId, "AG-CHILD"));
            agentHierarchyService.register(registration(siblingId, tenantId, rootId, 91003L, "AG-SIBLING"));
            Agent grandchild = agentHierarchyService
                .register(registration(grandchildId, tenantId, childId, 91004L, "AG-GRANDCHILD"));

            org.junit.jupiter.api.Assertions.assertEquals("/90001", root.path());
            org.junit.jupiter.api.Assertions.assertEquals("/90001/90002", child.path());
            org.junit.jupiter.api.Assertions.assertEquals("/90001/90002/90004", grandchild.path());
            org.junit.jupiter.api.Assertions.assertEquals(0, closureDepth(tenantId, grandchildId, grandchildId));
            org.junit.jupiter.api.Assertions.assertEquals(1, closureDepth(tenantId, childId, grandchildId));
            org.junit.jupiter.api.Assertions.assertEquals(2, closureDepth(tenantId, rootId, grandchildId));
            org.junit.jupiter.api.Assertions.assertTrue(agentScopeAuthorizationService
                .canAccess(tenantId, rootUserId, grandchildId));
            org.junit.jupiter.api.Assertions.assertTrue(agentScopeAuthorizationService
                .canAccess(tenantId, childUserId, grandchildId));
            org.junit.jupiter.api.Assertions
                .assertThrows(AgentAccessDeniedException.class, () -> agentScopeAuthorizationService
                    .requireAccessible(tenantId, childUserId, siblingId));
            org.junit.jupiter.api.Assertions
                .assertThrows(AgentAccessDeniedException.class, () -> agentScopeAuthorizationService
                    .requireAccessible(tenantId, rootUserId, 91001L));
            org.junit.jupiter.api.Assertions
                .assertThrows(AgentAccessDeniedException.class, () -> agentScopeAuthorizationService
                    .requireAccessible(902L, rootUserId, 91001L));
            org.junit.jupiter.api.Assertions.assertEquals(List
                .of(rootId, childId, siblingId, grandchildId), agentScopeAuthorizationService
                    .listAuthorizedAgentIds(tenantId, rootUserId));

            Agent disabledGrandchild = agentHierarchyService
                .changeLifecycle(tenantId, rootUserId, grandchildId, AgentStatus.DISABLED, "integration test", 0L);
            org.junit.jupiter.api.Assertions.assertEquals(AgentStatus.DISABLED, disabledGrandchild.status());
            org.junit.jupiter.api.Assertions.assertEquals(1L, disabledGrandchild.rowVersion());
            org.junit.jupiter.api.Assertions.assertEquals(AgentStatus.DISABLED, agentRepository
                .findById(tenantId, grandchildId)
                .orElseThrow()
                .status());
            org.junit.jupiter.api.Assertions
                .assertThrows(AgentConcurrentModificationException.class, () -> agentHierarchyService
                    .changeLifecycle(tenantId, rootUserId, grandchildId, AgentStatus.ENABLED, null, 0L));

            agentHierarchyService
                .changeLifecycle(tenantId, rootUserId, childId, AgentStatus.DISABLED, "integration test", 0L);
            org.junit.jupiter.api.Assertions.assertFalse(agentScopeAuthorizationService
                .canAccess(tenantId, childUserId, grandchildId));
        });
    }

    protected void verifyMerchantScopeUsesAgentOwnership() {
        long tenantId = 903L;
        long rootId = 93001L;
        long childId = 93002L;
        long siblingId = 93003L;
        long rootUserId = 93101L;
        long childUserId = 93102L;
        long siblingUserId = 93103L;
        long merchantId = 94001L;
        long siblingMerchantId = 94002L;
        long operatorUserId = 94101L;
        long reviewerUserId = 94102L;

        TenantUtils.execute(tenantId, () -> {
            agentHierarchyService.register(registration(rootId, tenantId, 0L, rootUserId, "MER-ROOT"));
            agentHierarchyService.register(registration(childId, tenantId, rootId, childUserId, "MER-CHILD"));
            agentHierarchyService.register(registration(siblingId, tenantId, rootId, siblingUserId, "MER-SIBLING"));

            Merchant merchant = merchantMasterService
                .register(rootUserId, merchantRegistration(merchantId, tenantId, childId, operatorUserId, reviewerUserId, "MERCHANT-PRIMARY", "a"
                    .repeat(64)));
            org.junit.jupiter.api.Assertions.assertEquals(childId, merchant.owningAgentId());
            org.junit.jupiter.api.Assertions.assertEquals(MerchantStatus.DRAFT, merchant.status());
            org.junit.jupiter.api.Assertions.assertEquals("138****5678", merchant.contactMobile().maskedValue());

            org.junit.jupiter.api.Assertions
                .assertThrows(MerchantAccessDeniedException.class, () -> merchantMasterService
                    .register(childUserId, merchantRegistration(94003L, tenantId, siblingId, 94103L, 94104L, "MERCHANT-DENIED", "b"
                        .repeat(64))));
            merchantMasterService
                .register(rootUserId, merchantRegistration(siblingMerchantId, tenantId, siblingId, 94105L, 94106L, "MERCHANT-SIBLING", "c"
                    .repeat(64)));

            org.junit.jupiter.api.Assertions.assertTrue(merchantScopeAuthorizationService
                .canAccess(tenantId, rootUserId, merchantId));
            org.junit.jupiter.api.Assertions.assertTrue(merchantScopeAuthorizationService
                .canAccess(tenantId, childUserId, merchantId));
            org.junit.jupiter.api.Assertions.assertTrue(merchantScopeAuthorizationService
                .canAccess(tenantId, operatorUserId, merchantId));
            org.junit.jupiter.api.Assertions.assertTrue(merchantScopeAuthorizationService
                .canAccess(tenantId, reviewerUserId, merchantId));
            org.junit.jupiter.api.Assertions.assertFalse(merchantScopeAuthorizationService
                .canAccess(tenantId, siblingUserId, merchantId));
            org.junit.jupiter.api.Assertions.assertFalse(merchantScopeAuthorizationService
                .canAccess(tenantId, childUserId, siblingMerchantId));
            org.junit.jupiter.api.Assertions
                .assertThrows(MerchantAccessDeniedException.class, () -> merchantScopeAuthorizationService
                    .requireAccessible(tenantId, 94999L, merchantId));
            org.junit.jupiter.api.Assertions
                .assertThrows(MerchantAccessDeniedException.class, () -> merchantScopeAuthorizationService
                    .requireAccessible(tenantId, rootUserId, 94999L));

            Merchant disabled = merchantMasterService
                .changeLifecycle(tenantId, rootUserId, merchantId, MerchantStatus.DISABLED, "integration test", 0L);
            org.junit.jupiter.api.Assertions.assertEquals(MerchantStatus.DISABLED, disabled.status());
            org.junit.jupiter.api.Assertions.assertEquals(1L, disabled.rowVersion());
            org.junit.jupiter.api.Assertions.assertEquals("integration test", disabled.disabledReason());
            org.junit.jupiter.api.Assertions
                .assertThrows(MerchantConcurrentModificationException.class, () -> merchantMasterService
                    .changeLifecycle(tenantId, rootUserId, merchantId, MerchantStatus.ENABLED, null, 0L));

            Merchant stored = merchantRepository.findById(tenantId, merchantId).orElseThrow();
            org.junit.jupiter.api.Assertions.assertEquals(childId, stored.owningAgentId());
            org.junit.jupiter.api.Assertions.assertEquals(operatorUserId, stored.operatorUserId());
            org.junit.jupiter.api.Assertions.assertEquals(reviewerUserId, stored.reviewerUserId());
            org.junit.jupiter.api.Assertions.assertEquals("hash-v1", stored.contactMobile().hashKeyVersion());
            org.junit.jupiter.api.Assertions.assertEquals(MerchantStatus.DISABLED, stored.status());
        });

        TenantUtils.execute(904L, () -> org.junit.jupiter.api.Assertions
            .assertThrows(MerchantAccessDeniedException.class, () -> merchantScopeAuthorizationService
                .requireAccessible(tenantId, operatorUserId, merchantId)));
    }

    protected void verifySecurityAuditIsAppendOnly() {
        long tenantId = 905L;
        TenantUtils.execute(tenantId, () -> {
            Long auditId = securityAuditWriter
                .append(new SecurityAuditRecord(tenantId, 95001L, 95002L, "SENSITIVE_REVEAL", "MERCHANT", 95003L, 1L, "CONTACT_MOBILE", "Synthetic acceptance reason", "127.0.0.1", SecurityAuditResult.SUCCESS, null, LocalDateTime
                    .of(2026, 8, 20, 12, 0)));
            org.junit.jupiter.api.Assertions.assertNotNull(auditId);
            org.junit.jupiter.api.Assertions.assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM biz_security_audit WHERE tenant_id = ? AND id = ?
                """, Integer.class, tenantId, auditId));
            org.junit.jupiter.api.Assertions.assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
                UPDATE biz_security_audit SET reason = 'tampered' WHERE tenant_id = ? AND id = ?
                """, tenantId, auditId));
            org.junit.jupiter.api.Assertions.assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
                DELETE FROM biz_security_audit WHERE tenant_id = ? AND id = ?
                """, tenantId, auditId));
            org.junit.jupiter.api.Assertions.assertEquals("Synthetic acceptance reason", jdbcTemplate.queryForObject("""
                SELECT reason FROM biz_security_audit WHERE tenant_id = ? AND id = ?
                """, String.class, tenantId, auditId));
        });
    }

    protected void verifyKycAttachmentMetadataPersistence() {
        long tenantId = 906L;
        long merchantId = 96001L;
        long kycVersionId = 96002L;
        TenantUtils.execute(tenantId, () -> {
            jdbcTemplate.update("""
                INSERT INTO biz_kyc_version
                (id, tenant_id, merchant_id, version_no, requirement_version, status, saved_step, legal_name,
                 row_version, create_time, deleted)
                VALUES (?, ?, ?, 1, 'REQ-1', 'DRAFT', 1, 'Synthetic Merchant', 0, ?, 0)
                """, kycVersionId, tenantId, merchantId, LocalDateTime.of(2026, 8, 21, 10, 0));

            org.junit.jupiter.api.Assertions.assertEquals(merchantId, kycVersionOwnershipRepository
                .findMerchantId(tenantId, kycVersionId)
                .orElseThrow());
            KycAttachment attachment = kycAttachmentRepository
                .insert(new KycAttachmentDraft(tenantId, kycVersionId, "BUSINESS_LICENSE", "kyc-private|kyc/quarantine/906/96002/|object.png", "license.png", "png", "image/png", "image/png", 3L, "a"
                    .repeat(64), KycAttachmentScanStatus.UNAVAILABLE, KycAttachmentValidationStatus.QUARANTINED, 1, LocalDateTime
                        .of(2026, 8, 21, 10, 1)));

            org.junit.jupiter.api.Assertions.assertNotNull(attachment.id());
            org.junit.jupiter.api.Assertions.assertEquals(1L, kycAttachmentRepository
                .countByKycVersion(tenantId, kycVersionId));
            org.junit.jupiter.api.Assertions.assertEquals(1L, kycAttachmentRepository
                .countByEvidenceType(tenantId, kycVersionId, "BUSINESS_LICENSE"));
            org.junit.jupiter.api.Assertions.assertEquals(KycAttachmentScanStatus.UNAVAILABLE, kycAttachmentRepository
                .findById(tenantId, attachment.id())
                .orElseThrow()
                .scanStatus());
            org.junit.jupiter.api.Assertions.assertTrue(kycAttachmentRepository.findById(907L, attachment.id())
                .isEmpty());
        });
    }

    protected void verifyAgentScopedQueries() {
        long tenantId = 907L;
        long rootId = 97001L;
        long childId = 97002L;
        long siblingId = 97003L;
        long grandchildId = 97004L;
        long rootUserId = 97101L;
        long childUserId = 97102L;
        TenantUtils.execute(tenantId, () -> {
            agentHierarchyService.register(registration(rootId, tenantId, 0L, rootUserId, "QUERY-ROOT"));
            agentHierarchyService.register(registration(childId, tenantId, rootId, childUserId, "QUERY-CHILD"));
            agentHierarchyService.register(registration(siblingId, tenantId, rootId, 97103L, "QUERY-SIBLING"));
            EncryptedMobileNumber mobile = EncryptedMobileNumber.restore(new byte[] {1, 2, 3}, "data-v1", "d"
                .repeat(64), "hash-v1", "138****5678");
            agentHierarchyService
                .register(new AgentRegistration(grandchildId, tenantId, childId, 97104L, "QUERY-GRANDCHILD", "QUERY GRANDCHILD", "Contact", mobile, null));

            AgentPage first = agentQueryService
                .page(tenantId, rootUserId, new AgentListQuery(null, "QUERY", null, 1, 2, "127.0.0.1"));
            AgentPage repeated = agentQueryService
                .page(tenantId, rootUserId, new AgentListQuery(null, "QUERY", null, 1, 2, "127.0.0.1"));
            org.junit.jupiter.api.Assertions.assertEquals(4L, first.total());
            org.junit.jupiter.api.Assertions.assertEquals(first.list()
                .stream()
                .map(item -> item.id())
                .toList(), repeated.list().stream().map(item -> item.id()).toList());

            AgentPage childScope = agentQueryService
                .page(tenantId, childUserId, new AgentListQuery(null, "QUERY", null, 1, 20, "127.0.0.1"));
            org.junit.jupiter.api.Assertions.assertEquals(List.of(grandchildId, childId), childScope.list()
                .stream()
                .map(item -> item.id())
                .toList());
            org.junit.jupiter.api.Assertions.assertEquals("138****5678", agentQueryService
                .get(tenantId, childUserId, grandchildId, "127.0.0.1")
                .contactMobileMasked());

            AgentPage siblingFilter = agentQueryService
                .page(tenantId, childUserId, new AgentListQuery(siblingId, null, AgentStatus.ENABLED, 1, 20, "127.0.0.1"));
            org.junit.jupiter.api.Assertions.assertTrue(siblingFilter.list().isEmpty());
            org.junit.jupiter.api.Assertions.assertEquals(0L, siblingFilter.total());
            org.junit.jupiter.api.Assertions.assertThrows(AgentAccessDeniedException.class, () -> agentQueryService
                .get(tenantId, childUserId, siblingId, "127.0.0.1"));
            org.junit.jupiter.api.Assertions.assertEquals(2, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM biz_security_audit
                WHERE tenant_id = ? AND actor_user_id = ? AND object_id = ? AND action = 'AGENT_READ_DENIED'
                """, Integer.class, tenantId, childUserId, siblingId));
        });
    }

    protected void verifySubordinateAgentProvisioningIsAtomic() {
        long tenantId = 908L;
        long parentDeptId = 98001L;
        long parentUserId = 98101L;
        long rootAgentId = 98201L;
        String contactMobile = "13900001234";
        TenantUtils.execute(tenantId, () -> {
            LocalDateTime now = LocalDateTime.of(2026, 8, 21, 14, 0);
            jdbcTemplate.update("""
                INSERT INTO sys_dept
                (id, name, parent_id, ancestors, sort, status, is_system, create_user, create_time, deleted)
                VALUES (?, 'ROOT-AGENT-DEPT', 0, '0', 1, 1, ?, 1, ?, 0)
                """, parentDeptId, false, now);
            jdbcTemplate.update("""
                INSERT INTO sys_user
                (id, username, nickname, password, gender, status, is_system, pwd_reset_time, dept_id,
                 create_user, create_time, deleted)
                VALUES (?, 'agent_root_908', 'ROOT-AGENT',
                        '{bcrypt}$2a$10$xAsoeMJ.jc/kSxhviLAg7.j2iFrhi6yYAdniNdjLiIUWU/BRZl2Ti',
                        0, 1, ?, ?, ?, 1, ?, 0)
                """, parentUserId, false, now, parentDeptId, now);
            agentHierarchyService
                .register(new AgentRegistration(rootAgentId, tenantId, 0L, parentUserId, parentDeptId, "PROVISION-ROOT", "PROVISION ROOT", "Root Contact", null, null));
            org.junit.jupiter.api.Assertions.assertEquals(parentDeptId, agentRepository.findById(tenantId, rootAgentId)
                .orElseThrow()
                .deptId());
            org.junit.jupiter.api.Assertions.assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sys_dept WHERE id = ? AND deleted = 0
                """, Integer.class, parentDeptId));
            SubordinateAgentProvisioningResult result = subordinateAgentProvisioningService
                .create(new SubordinateAgentCreateCommand(tenantId, parentUserId, "SUB-908", "Subordinate Agent", "Sub Contact", contactMobile, "TempPass908!"));

            Agent child = agentRepository.findById(tenantId, result.agentId()).orElseThrow();
            org.junit.jupiter.api.Assertions.assertEquals(rootAgentId, child.parentId());
            org.junit.jupiter.api.Assertions.assertEquals(result.userId(), child.userId());
            org.junit.jupiter.api.Assertions.assertEquals(result.deptId(), child.deptId());
            org.junit.jupiter.api.Assertions.assertEquals("139****1234", child.contactMobile().maskedValue());
            org.junit.jupiter.api.Assertions.assertNotNull(child.promotionCode());
            org.junit.jupiter.api.Assertions.assertTrue(child.promotionCode()
                .matches("[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{12}"));
            org.junit.jupiter.api.Assertions.assertEquals(AgentPromotionCodeStatus.ACTIVE, child.promotionCodeStatus());
            org.junit.jupiter.api.Assertions
                .assertEquals(SubordinateAgentProvisioningService.PASSWORD_CHANGE_REQUIRED, result.credentialStatus());
            org.junit.jupiter.api.Assertions.assertTrue(result.username().startsWith("ag_sub_908_"));
            org.junit.jupiter.api.Assertions.assertTrue(java.util.Arrays.stream(result.getClass().getRecordComponents())
                .noneMatch(component -> component.getName().toLowerCase().contains("password")));

            org.junit.jupiter.api.Assertions.assertEquals(parentDeptId, jdbcTemplate.queryForObject("""
                SELECT parent_id FROM sys_dept WHERE id = ?
                """, Long.class, result.deptId()));
            org.junit.jupiter.api.Assertions.assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT status FROM sys_user WHERE id = ?
                """, Integer.class, result.userId()));
            org.junit.jupiter.api.Assertions.assertTrue(jdbcTemplate.queryForObject("""
                SELECT must_change_password FROM sys_user WHERE id = ?
                """, Boolean.class, result.userId()));
            String storedPassword = jdbcTemplate
                .queryForObject("SELECT password FROM sys_user WHERE id = ?", String.class, result.userId());
            String storedPhone = jdbcTemplate
                .queryForObject("SELECT phone FROM sys_user WHERE id = ?", String.class, result.userId());
            org.junit.jupiter.api.Assertions.assertNotNull(storedPassword);
            org.junit.jupiter.api.Assertions.assertTrue(storedPassword.startsWith("{bcrypt}") || storedPassword
                .startsWith("$2"));
            org.junit.jupiter.api.Assertions.assertTrue(passwordEncoder.matches("TempPass908!", storedPassword));
            org.junit.jupiter.api.Assertions.assertNull(storedPhone);
            Long roleId = jdbcTemplate
                .queryForObject("SELECT id FROM sys_role WHERE code = 'AGENT_ADMIN' AND deleted = 0", Long.class);
            org.junit.jupiter.api.Assertions.assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sys_user_role WHERE user_id = ? AND role_id = ?
                """, Integer.class, result.userId(), roleId));
            org.junit.jupiter.api.Assertions.assertEquals(11, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sys_role_menu WHERE role_id = ?
                  AND menu_id BETWEEN 690000000000100000 AND 690000000000100109
                """, Integer.class, roleId));

            org.junit.jupiter.api.Assertions.assertThrows(AgentDomainException.class, () -> agentAdministrationService
                .changeLifecycle(tenantId, parentUserId, rootAgentId, AgentStatus.DISABLED, "self disable", 0L, "127.0.0.1"));
            AgentSummary updated = agentAdministrationService.updateProfile(tenantId, parentUserId, result
                .agentId(), "Updated Agent", "Updated Contact", "13700004321", "Updated remarks", 0L, "127.0.0.1");
            org.junit.jupiter.api.Assertions.assertEquals(1L, updated.rowVersion());
            org.junit.jupiter.api.Assertions.assertEquals("137****4321", updated.contactMobileMasked());
            org.junit.jupiter.api.Assertions.assertEquals("Updated remarks", updated.remarks());
            Agent persistedUpdated = agentRepository.findById(tenantId, result.agentId()).orElseThrow();
            org.junit.jupiter.api.Assertions.assertEquals(rootAgentId, persistedUpdated.parentId());
            org.junit.jupiter.api.Assertions.assertEquals(result.deptId(), persistedUpdated.deptId());
            org.junit.jupiter.api.Assertions.assertEquals(result.userId(), persistedUpdated.userId());
            org.junit.jupiter.api.Assertions.assertEquals("Updated-Agent", jdbcTemplate.queryForObject("""
                SELECT nickname FROM sys_user WHERE id = ?
                """, String.class, result.userId()));

            agentAdministrationService.resetTemporaryPassword(tenantId, parentUserId, result
                .agentId(), "ResetPass908!", "authorized reset", "127.0.0.1");
            String resetPassword = jdbcTemplate
                .queryForObject("SELECT password FROM sys_user WHERE id = ?", String.class, result.userId());
            org.junit.jupiter.api.Assertions.assertTrue(passwordEncoder.matches("ResetPass908!", resetPassword));
            org.junit.jupiter.api.Assertions.assertTrue(jdbcTemplate.queryForObject("""
                SELECT must_change_password FROM sys_user WHERE id = ?
                """, Boolean.class, result.userId()));

            AgentSummary disabled = agentAdministrationService.changeLifecycle(tenantId, parentUserId, result
                .agentId(), AgentStatus.DISABLED, "authorized disable", 1L, "127.0.0.1");
            org.junit.jupiter.api.Assertions.assertEquals(AgentStatus.DISABLED, disabled.status());
            org.junit.jupiter.api.Assertions.assertEquals(2, jdbcTemplate.queryForObject("""
                SELECT status FROM sys_user WHERE id = ?
                """, Integer.class, result.userId()));
            AgentSummary enabled = agentAdministrationService.changeLifecycle(tenantId, parentUserId, result
                .agentId(), AgentStatus.ENABLED, "authorized enable", 2L, "127.0.0.1");
            org.junit.jupiter.api.Assertions.assertEquals(3L, enabled.rowVersion());
            org.junit.jupiter.api.Assertions.assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT status FROM sys_user WHERE id = ?
                """, Integer.class, result.userId()));
            org.mockito.Mockito.verify(onlineUserService, org.mockito.Mockito.atLeast(2)).kickOut(result.userId());
            org.junit.jupiter.api.Assertions.assertEquals(4, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM biz_security_audit
                WHERE tenant_id = ? AND object_id = ?
                  AND action IN ('AGENT_PROFILE_UPDATE', 'AGENT_PASSWORD_RESET', 'AGENT_LIFECYCLE_CHANGE')
                """, Integer.class, tenantId, result.agentId()));

            SubordinateAgentProvisioningResult secondResult = subordinateAgentProvisioningService
                .create(new SubordinateAgentCreateCommand(tenantId, parentUserId, "SUB-908-B", "Second Agent", "Second Contact", "13900004321", "TempPass4321!"));
            Agent secondChild = agentRepository.findById(tenantId, secondResult.agentId()).orElseThrow();
            org.junit.jupiter.api.Assertions.assertNotEquals(child.promotionCode(), secondChild.promotionCode());
            org.junit.jupiter.api.Assertions.assertEquals(AgentPromotionCodeStatus.ACTIVE, secondChild
                .promotionCodeStatus());

            Integer userCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sys_user WHERE description = 'Agent account requires first-login password change'
                """, Integer.class);
            Integer deptCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sys_dept WHERE description LIKE 'Agent department for SUB-908%'
                """, Integer.class);
            org.junit.jupiter.api.Assertions
                .assertThrows(AgentDomainException.class, () -> subordinateAgentProvisioningService
                    .create(new SubordinateAgentCreateCommand(tenantId, parentUserId, "SUB-908", "Duplicate Agent", "Duplicate Contact", "13900005678", "TempPass5678!")));
            org.junit.jupiter.api.Assertions.assertEquals(userCount, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sys_user WHERE description = 'Agent account requires first-login password change'
                """, Integer.class));
            org.junit.jupiter.api.Assertions.assertEquals(deptCount, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sys_dept WHERE description LIKE 'Agent department for SUB-908%'
                """, Integer.class));
        });
    }

    protected void verifyPromotionCodeOwnership() {
        long tenantId = 909L;
        long rootAgentId = 99001L;
        long codedAgentId = 99002L;
        long issuedAgentId = 99003L;
        long rootUserId = 99101L;
        String fixedCode = "CHLDPRM23456";
        TenantUtils.execute(tenantId, () -> {
            agentHierarchyService.register(registration(rootAgentId, tenantId, 0L, rootUserId, "PROMO-ROOT"));
            agentHierarchyService
                .register(new AgentRegistration(codedAgentId, tenantId, rootAgentId, 99102L, "PROMO-CODED", "PROMO CODED", "Contact", null, fixedCode));
            agentHierarchyService.register(registration(issuedAgentId, tenantId, rootAgentId, 99103L, "PROMO-ISSUED"));

            PromotionOwnership resolved = agentPromotionCodeService
                .resolveOwnership(tenantId, "  chldprm23456  ", null);
            org.junit.jupiter.api.Assertions.assertEquals(codedAgentId, resolved.agentId());
            org.junit.jupiter.api.Assertions.assertEquals(fixedCode, resolved.promotionCode());
            org.junit.jupiter.api.Assertions
                .assertThrows(PromotionOwnershipDeniedException.class, () -> agentPromotionCodeService
                    .resolveOwnership(tenantId, fixedCode, issuedAgentId));

            AgentPromotionCodeView issued = agentPromotionCodeService
                .issue(tenantId, rootUserId, issuedAgentId, 0L, "127.0.0.1");
            org.junit.jupiter.api.Assertions.assertEquals(AgentPromotionCodeStatus.ACTIVE, issued.status());
            org.junit.jupiter.api.Assertions.assertTrue(issued.promotionCode()
                .matches("[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{12}"));
            org.junit.jupiter.api.Assertions.assertNotEquals(fixedCode, issued.promotionCode());
            AgentPromotionCodeView repeatedIssue = agentPromotionCodeService
                .issue(tenantId, rootUserId, issuedAgentId, 0L, "127.0.0.1");
            org.junit.jupiter.api.Assertions.assertEquals(issued.promotionCode(), repeatedIssue.promotionCode());

            org.junit.jupiter.api.Assertions
                .assertThrows(AgentConcurrentModificationException.class, () -> agentPromotionCodeService
                    .changeStatus(tenantId, rootUserId, issuedAgentId, AgentPromotionCodeStatus.DISABLED, 0L, "127.0.0.1"));
            AgentPromotionCodeView disabledCode = agentPromotionCodeService
                .changeStatus(tenantId, rootUserId, issuedAgentId, AgentPromotionCodeStatus.DISABLED, 1L, "127.0.0.1");
            org.junit.jupiter.api.Assertions.assertEquals(2L, disabledCode.rowVersion());
            org.junit.jupiter.api.Assertions
                .assertThrows(PromotionOwnershipDeniedException.class, () -> agentPromotionCodeService
                    .resolveOwnership(tenantId, issued.promotionCode(), null));
            AgentPromotionCodeView enabledCode = agentPromotionCodeService
                .changeStatus(tenantId, rootUserId, issuedAgentId, AgentPromotionCodeStatus.ACTIVE, 2L, "127.0.0.1");
            org.junit.jupiter.api.Assertions.assertEquals(3L, enabledCode.rowVersion());
            org.junit.jupiter.api.Assertions.assertEquals(issuedAgentId, agentPromotionCodeService
                .resolveOwnership(tenantId, issued.promotionCode(), issuedAgentId)
                .agentId());

            agentHierarchyService
                .changeLifecycle(tenantId, rootUserId, codedAgentId, AgentStatus.DISABLED, "promotion ownership disabled-agent test", 0L);
            org.junit.jupiter.api.Assertions
                .assertThrows(PromotionOwnershipDeniedException.class, () -> agentPromotionCodeService
                    .resolveOwnership(tenantId, fixedCode, null));

            org.junit.jupiter.api.Assertions.assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM biz_security_audit
                WHERE tenant_id = ? AND object_id = ? AND action = 'AGENT_PROMOTION_CODE_ISSUE'
                """, Integer.class, tenantId, issuedAgentId));
            org.junit.jupiter.api.Assertions.assertEquals(2, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM biz_security_audit
                WHERE tenant_id = ? AND object_id = ? AND action = 'AGENT_PROMOTION_CODE_STATUS'
                """, Integer.class, tenantId, issuedAgentId));
        });
    }

    protected void verifyAgentPricingVersions() {
        long tenantId = 910L;
        long rootAgentId = 99501L;
        long childAgentId = 99502L;
        long rootUserId = 99601L;
        LocalDateTime firstEffective = LocalDateTime.of(2026, 8, 22, 9, 0);
        TenantUtils.execute(tenantId, () -> {
            agentHierarchyService.register(registration(rootAgentId, tenantId, 0L, rootUserId, "PRICING-ROOT"));
            agentHierarchyService.register(registration(childAgentId, tenantId, rootAgentId, 99602L, "PRICING-CHILD"));

            AgentPricingVersion rootV1 = agentPricingService
                .create(pricingCommand(tenantId, rootUserId, rootAgentId, "0.0100", "0.50", "0.60", firstEffective, "publish root pricing v1"));
            org.junit.jupiter.api.Assertions.assertEquals(1, rootV1.versionNo());
            org.junit.jupiter.api.Assertions.assertNull(rootV1.parentPricingVersionId());
            org.junit.jupiter.api.Assertions.assertEquals("CHANNEL-A", rootV1.channelCode());
            org.junit.jupiter.api.Assertions.assertEquals("PRODUCT-A", rootV1.productCode());

            org.junit.jupiter.api.Assertions.assertThrows(AgentPricingBoundaryException.class, () -> agentPricingService
                .create(pricingCommand(tenantId, rootUserId, childAgentId, "0.0120", "0.75", "0.40", firstEffective
                    .minusMinutes(1), "parent is not effective yet")));
            AgentPricingVersion childV1 = agentPricingService
                .create(pricingCommand(tenantId, rootUserId, childAgentId, "0.0120", "0.75", "0.40", firstEffective
                    .plusHours(1), "publish child pricing v1"));
            org.junit.jupiter.api.Assertions.assertEquals(rootV1.id(), childV1.parentPricingVersionId());
            org.junit.jupiter.api.Assertions.assertEquals(1, childV1.versionNo());

            org.junit.jupiter.api.Assertions.assertThrows(AgentPricingBoundaryException.class, () -> agentPricingService
                .create(pricingCommand(tenantId, rootUserId, childAgentId, "0.0090", "0.75", "0.40", firstEffective
                    .plusHours(2), "reject below parent cost")));

            AgentPricingVersion rootV2 = agentPricingService
                .create(pricingCommand(tenantId, rootUserId, rootAgentId, "0.0110", "0.60", "0.50", firstEffective
                    .plusDays(1), "publish root pricing v2"));
            AgentPricingVersion childV2 = agentPricingService
                .create(pricingCommand(tenantId, rootUserId, childAgentId, "0.0130", "0.80", "0.45", firstEffective
                    .plusDays(1)
                    .plusHours(1), "publish child pricing v2"));
            org.junit.jupiter.api.Assertions.assertEquals(2, rootV2.versionNo());
            org.junit.jupiter.api.Assertions.assertEquals(2, childV2.versionNo());
            org.junit.jupiter.api.Assertions.assertEquals(rootV2.id(), childV2.parentPricingVersionId());

            List<AgentPricingVersion> childHistory = agentPricingService
                .list(tenantId, rootUserId, childAgentId, "channel-a", "product-a", "cny");
            org.junit.jupiter.api.Assertions.assertEquals(List.of(childV2.id(), childV1.id()), childHistory.stream()
                .map(AgentPricingVersion::id)
                .toList());

            org.junit.jupiter.api.Assertions.assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
                INSERT INTO biz_agent_pricing_version
                (id, tenant_id, agent_id, parent_pricing_version_id, version_no, channel_code, product_code,
                 currency, pricing_rules_json, effective_time, expires_time, status, create_user, create_time, deleted)
                VALUES (?, ?, ?, NULL, 1, 'CHANNEL-A', 'PRODUCT-A', 'CNY', '{}', ?, NULL, 'PUBLISHED', ?, ?, 0)
                """, 99991001L, tenantId, rootAgentId, firstEffective, rootUserId, firstEffective));
            org.junit.jupiter.api.Assertions.assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
                UPDATE biz_agent_pricing_version SET pricing_rules_json = '{}' WHERE id = ?
                """, rootV1.id()));
            org.junit.jupiter.api.Assertions.assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
                DELETE FROM biz_agent_pricing_version WHERE id = ?
                """, childV1.id()));

            org.junit.jupiter.api.Assertions.assertEquals(4, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM biz_security_audit
                WHERE tenant_id = ? AND action = 'AGENT_PRICING_VERSION_CREATE'
                """, Integer.class, tenantId));
            String rootV2Audit = jdbcTemplate.queryForObject("""
                SELECT reason FROM biz_security_audit
                WHERE tenant_id = ? AND object_id = ? AND action = 'AGENT_PRICING_VERSION_CREATE'
                """, String.class, tenantId, rootV2.id());
            org.junit.jupiter.api.Assertions.assertTrue(rootV2Audit.contains(rootV1.id().toString()));
        });
    }

    protected void verifyAgentMerchantDefaults() {
        long tenantId = 911L;
        long rootAgentId = 99701L;
        long childAgentId = 99702L;
        long rootUserId = 99801L;
        long merchantId = 99901L;
        long firstKycVersionId = 99902L;
        long secondKycVersionId = 99903L;
        LocalDateTime pricingV1Effective = LocalDateTime.of(2026, 8, 20, 9, 0);
        LocalDateTime defaultV1Effective = LocalDateTime.of(2026, 8, 20, 10, 0);
        TenantUtils.execute(tenantId, () -> {
            agentHierarchyService.register(registration(rootAgentId, tenantId, 0L, rootUserId, "DEFAULT-ROOT"));
            agentHierarchyService.register(registration(childAgentId, tenantId, rootAgentId, 99802L, "DEFAULT-CHILD"));

            AgentPricingVersion pricingV1 = agentPricingService
                .create(pricingCommand(tenantId, rootUserId, rootAgentId, "0.0100", "0.50", "0.60", pricingV1Effective, "default pricing v1"));
            AgentMerchantDefaultVersion defaultV1 = agentMerchantDefaultService
                .create(defaultCommand(tenantId, rootUserId, rootAgentId, pricingV1, defaultV1Effective, "publish merchant defaults v1"));

            merchantMasterService
                .register(rootUserId, merchantRegistration(merchantId, tenantId, rootAgentId, 99911L, 99912L, "DEFAULT-MERCHANT", "f"
                    .repeat(64)));
            insertKycDraft(firstKycVersionId, tenantId, merchantId, 1, LocalDateTime.of(2026, 8, 21, 10, 0));
            KycDraftDefaultSnapshot firstSnapshot = agentMerchantDefaultService
                .inheritIntoDraft(tenantId, rootUserId, firstKycVersionId, "127.0.0.1")
                .orElseThrow();
            org.junit.jupiter.api.Assertions.assertEquals(defaultV1.id(), firstSnapshot.agentDefaultVersionId());
            org.junit.jupiter.api.Assertions.assertEquals(pricingV1.id(), firstSnapshot.defaults()
                .products()
                .get(0)
                .pricingVersionId());

            AgentPricingVersion pricingV2 = agentPricingService
                .create(pricingCommand(tenantId, rootUserId, rootAgentId, "0.0110", "0.60", "0.50", LocalDateTime
                    .of(2026, 8, 21, 12, 0), "default pricing v2"));
            AgentMerchantDefaultVersion defaultV2 = agentMerchantDefaultService
                .create(defaultCommand(tenantId, rootUserId, rootAgentId, pricingV2, LocalDateTime
                    .of(2026, 8, 21, 13, 0), "publish merchant defaults v2"));

            KycDraftDefaultSnapshot repeatedFirstSnapshot = agentMerchantDefaultService
                .inheritIntoDraft(tenantId, rootUserId, firstKycVersionId, "127.0.0.1")
                .orElseThrow();
            org.junit.jupiter.api.Assertions.assertEquals(firstSnapshot.id(), repeatedFirstSnapshot.id());
            org.junit.jupiter.api.Assertions.assertEquals(defaultV1.id(), repeatedFirstSnapshot
                .agentDefaultVersionId());

            insertKycDraft(secondKycVersionId, tenantId, merchantId, 2, LocalDateTime.of(2026, 8, 21, 14, 0));
            KycDraftDefaultSnapshot secondSnapshot = agentMerchantDefaultService
                .inheritIntoDraft(tenantId, rootUserId, secondKycVersionId, "127.0.0.1")
                .orElseThrow();
            org.junit.jupiter.api.Assertions.assertEquals(defaultV2.id(), secondSnapshot.agentDefaultVersionId());
            org.junit.jupiter.api.Assertions.assertEquals(pricingV2.id(), secondSnapshot.defaults()
                .products()
                .get(0)
                .pricingVersionId());

            AgentPricingVersion childPricing = agentPricingService
                .create(pricingCommand(tenantId, rootUserId, childAgentId, "0.0120", "0.75", "0.40", LocalDateTime
                    .of(2026, 8, 20, 11, 0), "child pricing"));
            org.junit.jupiter.api.Assertions
                .assertThrows(AgentPricingBoundaryException.class, () -> agentMerchantDefaultService
                    .create(defaultCommand(tenantId, rootUserId, rootAgentId, childPricing, LocalDateTime
                        .of(2026, 8, 21, 15, 0), "reject cross-agent pricing")));

            org.junit.jupiter.api.Assertions.assertEquals(List.of(defaultV2.id(), defaultV1
                .id()), agentMerchantDefaultService.list(tenantId, rootUserId, rootAgentId)
                    .stream()
                    .map(AgentMerchantDefaultVersion::id)
                    .toList());
            org.junit.jupiter.api.Assertions.assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
                UPDATE biz_agent_merchant_default_version SET default_payload_json = '{}' WHERE id = ?
                """, defaultV1.id()));
            org.junit.jupiter.api.Assertions.assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
                DELETE FROM biz_kyc_draft_default_snapshot WHERE id = ?
                """, firstSnapshot.id()));

            org.junit.jupiter.api.Assertions.assertEquals(2, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM biz_security_audit
                WHERE tenant_id = ? AND action = 'AGENT_MERCHANT_DEFAULT_VERSION_CREATE'
                """, Integer.class, tenantId));
            org.junit.jupiter.api.Assertions.assertEquals(2, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM biz_security_audit
                WHERE tenant_id = ? AND action = 'KYC_DRAFT_DEFAULTS_INHERIT'
                """, Integer.class, tenantId));
        });
    }

    protected void verifyConcurrentLegalSubjectUniqueness() throws Exception {
        long tenantId = 913L;
        long rootAgentId = 100301L;
        long rootUserId = 100401L;
        String legalSubjectHash = "a".repeat(64);
        TenantUtils.execute(tenantId, () -> agentHierarchyService
            .register(registration(rootAgentId, tenantId, 0L, rootUserId, "MERCHANT-UNIQUE-ROOT")));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<String>> futures = List.of(executor
                .submit(() -> registerConcurrentMerchant(ready, start, tenantId, rootUserId, rootAgentId, 100501L, 100511L, 100512L, "UNIQUE-M-1", legalSubjectHash
                    .toUpperCase())), executor
                        .submit(() -> registerConcurrentMerchant(ready, start, tenantId, rootUserId, rootAgentId, 100502L, 100521L, 100522L, "UNIQUE-M-2", legalSubjectHash)));
            org.junit.jupiter.api.Assertions.assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<String> results = futures.stream().map(future -> {
                try {
                    return future.get(30, TimeUnit.SECONDS);
                } catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
            }).sorted().toList();
            org.junit.jupiter.api.Assertions.assertEquals(List.of("CREATED", "DUPLICATE"), results);
            org.junit.jupiter.api.Assertions.assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM biz_merchant
                WHERE tenant_id = ? AND legal_subject_hash = ? AND deleted = 0
                """, Integer.class, tenantId, legalSubjectHash));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    protected void verifyMerchantProvisioningIsAtomic() {
        long tenantId = 915L;
        long parentDeptId = 101501L;
        long rootUserId = 101502L;
        long rootAgentId = 101503L;
        TenantUtils.execute(tenantId, () -> {
            LocalDateTime now = LocalDateTime.of(2026, 8, 21, 16, 0);
            jdbcTemplate.update("""
                INSERT INTO sys_dept
                (id, name, parent_id, ancestors, sort, status, is_system, create_user, create_time, deleted)
                VALUES (?, 'MERCHANT-ROOT-DEPT', 0, '0', 1, 1, ?, 1, ?, 0)
                """, parentDeptId, false, now);
            jdbcTemplate.update("""
                INSERT INTO sys_user
                (id, username, nickname, password, gender, status, is_system, pwd_reset_time, dept_id,
                 create_user, create_time, deleted)
                VALUES (?, 'merchant_root_915', 'MERCHANT-ROOT',
                        '{bcrypt}$2a$10$xAsoeMJ.jc/kSxhviLAg7.j2iFrhi6yYAdniNdjLiIUWU/BRZl2Ti',
                        0, 1, ?, ?, ?, 1, ?, 0)
                """, rootUserId, false, now, parentDeptId, now);
            agentHierarchyService
                .register(new AgentRegistration(rootAgentId, tenantId, 0L, rootUserId, parentDeptId, "MERCHANT-ROOT-915", "MERCHANT ROOT", "Root Contact", null, null));

            MerchantProvisioningResult result = merchantProvisioningService
                .create(new MerchantCreateCommand(tenantId, rootUserId, rootAgentId, MerchantType.ENTERPRISE, "Synthetic Legal Subject", "Synthetic Merchant", "91350211M000100Y43", "Merchant Contact", "13800001234", "13900005678", "Technology", "Synthetic merchant provisioning", "OperatorPass915!", "ReviewerPass915!", "127.0.0.1"));

            org.junit.jupiter.api.Assertions.assertNotEquals(result.operatorUserId(), result.reviewerUserId());
            org.junit.jupiter.api.Assertions.assertNotEquals(result.operatorUsername(), result.reviewerUsername());
            org.junit.jupiter.api.Assertions.assertTrue(result.operatorUsername().startsWith("mo_"));
            org.junit.jupiter.api.Assertions.assertTrue(result.reviewerUsername().startsWith("mr_"));
            org.junit.jupiter.api.Assertions.assertEquals(MerchantProvisioningService.PASSWORD_CHANGE_REQUIRED, result
                .credentialStatus());
            org.junit.jupiter.api.Assertions.assertTrue(java.util.Arrays.stream(result.getClass().getRecordComponents())
                .noneMatch(component -> component.getName().toLowerCase().contains("password")));

            Merchant merchant = merchantRepository.findById(tenantId, result.merchantId()).orElseThrow();
            org.junit.jupiter.api.Assertions.assertEquals(rootAgentId, merchant.owningAgentId());
            org.junit.jupiter.api.Assertions.assertEquals(result.operatorUserId(), merchant.operatorUserId());
            org.junit.jupiter.api.Assertions.assertEquals(result.reviewerUserId(), merchant.reviewerUserId());
            org.junit.jupiter.api.Assertions.assertEquals("138****1234", merchant.contactMobile().maskedValue());
            org.junit.jupiter.api.Assertions.assertEquals("139****5678", merchant.reviewerMobile().maskedValue());
            org.junit.jupiter.api.Assertions.assertEquals(64, merchant.legalSubjectHash().length());

            String originalLegalName = merchant.legalName();
            String originalLegalHash = merchant.legalSubjectHash();
            var updatedProfile = merchantAdministrationService.updateProfile(tenantId, rootUserId, result
                .merchantId(), "Updated Merchant", "Updated Contact", "", "", "Digital Services", "Updated ordinary profile", 0L, "127.0.0.1");
            org.junit.jupiter.api.Assertions.assertEquals(1L, updatedProfile.rowVersion());
            Merchant persistedProfile = merchantRepository.findById(tenantId, result.merchantId()).orElseThrow();
            org.junit.jupiter.api.Assertions.assertEquals(originalLegalName, persistedProfile.legalName());
            org.junit.jupiter.api.Assertions.assertEquals(originalLegalHash, persistedProfile.legalSubjectHash());
            org.junit.jupiter.api.Assertions.assertEquals(rootAgentId, persistedProfile.owningAgentId());
            org.junit.jupiter.api.Assertions.assertEquals("138****1234", persistedProfile.contactMobile()
                .maskedValue());
            org.junit.jupiter.api.Assertions.assertEquals("139****5678", persistedProfile.reviewerMobile()
                .maskedValue());

            Long targetAgentId = 101504L;
            agentHierarchyService
                .register(registration(targetAgentId, tenantId, rootAgentId, 101505L, "MERCHANT-TARGET-915"));
            var identityRoute = merchantReverificationRoutingService.route(tenantId, rootUserId, result
                .merchantId(), java.util.Set
                    .of(MerchantReverificationChangeType.LEGAL_IDENTITY, MerchantReverificationChangeType.SETTLEMENT_ACCOUNT), null, "Update certified information", "127.0.0.1");
            var ownershipRoute = merchantReverificationRoutingService.route(tenantId, rootUserId, result
                .merchantId(), java.util.Set
                    .of(MerchantReverificationChangeType.OWNERSHIP), targetAgentId, "Transfer ownership after review", "127.0.0.1");
            org.junit.jupiter.api.Assertions
                .assertEquals(MerchantReverificationRoutingService.BUSINESS_TYPE, identityRoute.businessType());
            org.junit.jupiter.api.Assertions
                .assertEquals(MerchantReverificationRoutingService.PROCESS_KEY, ownershipRoute.processDefinitionKey());
            org.junit.jupiter.api.Assertions.assertEquals(2, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM biz_merchant_reverification_request
                WHERE tenant_id = ? AND merchant_id = ? AND status = 'AWAITING_KYC_DRAFT'
                  AND business_type = 'MERCHANT_REVERIFICATION'
                  AND process_definition_key = 'merchant-onboarding-review-v1'
                """, Integer.class, tenantId, result.merchantId()));
            org.junit.jupiter.api.Assertions.assertEquals(targetAgentId, jdbcTemplate.queryForObject("""
                SELECT target_agent_id FROM biz_merchant_reverification_request WHERE id = ?
                """, Long.class, ownershipRoute.requestId()));
            String routeMetadata = jdbcTemplate.queryForObject("""
                SELECT CONCAT(change_types_json, ' ', reason) FROM biz_merchant_reverification_request WHERE id = ?
                """, String.class, identityRoute.requestId());
            org.junit.jupiter.api.Assertions.assertFalse(routeMetadata.contains("91350211M000100Y43"));
            org.junit.jupiter.api.Assertions.assertFalse(routeMetadata.contains("13800001234"));

            assertMerchantUser(result.operatorUserId(), parentDeptId, "OperatorPass915!", "MERCHANT_OPERATOR");
            assertMerchantUser(result.reviewerUserId(), parentDeptId, "ReviewerPass915!", "MERCHANT_REVIEWER");
            org.junit.jupiter.api.Assertions.assertEquals(9, countMerchantManagementMenus("AGENT_ADMIN"));
            org.junit.jupiter.api.Assertions.assertEquals(6, countMerchantManagementMenus("MERCHANT_OPERATOR"));
            org.junit.jupiter.api.Assertions.assertEquals(4, countMerchantManagementMenus("MERCHANT_REVIEWER"));
            org.junit.jupiter.api.Assertions.assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM biz_security_audit
                WHERE tenant_id = ? AND object_id = ? AND action = 'MERCHANT_CREATE'
                """, Integer.class, tenantId, result.merchantId()));
            org.junit.jupiter.api.Assertions.assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM biz_security_audit
                WHERE tenant_id = ? AND object_id = ? AND action = 'MERCHANT_PROFILE_UPDATE'
                """, Integer.class, tenantId, result.merchantId()));
            org.junit.jupiter.api.Assertions.assertEquals(2, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM biz_security_audit
                WHERE tenant_id = ? AND action = 'MERCHANT_REVERIFICATION_ROUTE'
                """, Integer.class, tenantId));

            var disabledMerchant = merchantAdministrationService.changeLifecycle(tenantId, rootUserId, result
                .merchantId(), MerchantStatus.DISABLED, "authorized merchant disable", 1L, "127.0.0.1");
            org.junit.jupiter.api.Assertions.assertEquals(2L, disabledMerchant.rowVersion());
            org.junit.jupiter.api.Assertions.assertEquals(2, jdbcTemplate.queryForObject("""
                SELECT status FROM sys_user WHERE id = ?
                """, Integer.class, result.operatorUserId()));
            org.junit.jupiter.api.Assertions.assertEquals(2, jdbcTemplate.queryForObject("""
                SELECT status FROM sys_user WHERE id = ?
                """, Integer.class, result.reviewerUserId()));
            for (MerchantOperation operation : MerchantOperation.values()) {
                org.junit.jupiter.api.Assertions
                    .assertThrows(MerchantDomainException.class, () -> merchantOperationPolicyService
                        .requireAllowed(tenantId, result.merchantId(), operation));
            }
            org.mockito.Mockito.verify(onlineUserService).kickOut(result.operatorUserId());
            org.mockito.Mockito.verify(onlineUserService).kickOut(result.reviewerUserId());

            var enabledMerchant = merchantAdministrationService.changeLifecycle(tenantId, rootUserId, result
                .merchantId(), MerchantStatus.ENABLED, "authorized merchant enable", 2L, "127.0.0.1");
            org.junit.jupiter.api.Assertions.assertEquals(3L, enabledMerchant.rowVersion());
            merchantOperationPolicyService.requireAllowed(tenantId, result
                .merchantId(), MerchantOperation.NEW_ONBOARDING);
            org.junit.jupiter.api.Assertions.assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT status FROM sys_user WHERE id = ?
                """, Integer.class, result.operatorUserId()));
            org.junit.jupiter.api.Assertions.assertEquals(2, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM biz_security_audit
                WHERE tenant_id = ? AND object_id = ? AND action = 'MERCHANT_LIFECYCLE_CHANGE'
                """, Integer.class, tenantId, result.merchantId()));

            Integer userCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sys_user
                WHERE description LIKE 'Merchant % requires first-login password change'
                """, Integer.class);
            org.junit.jupiter.api.Assertions
                .assertThrows(MerchantDuplicateLegalSubjectException.class, () -> merchantProvisioningService
                    .create(new MerchantCreateCommand(tenantId, rootUserId, rootAgentId, MerchantType.ENTERPRISE, "Duplicate Legal Subject", "Duplicate Merchant", "91350211m000100y43", "Duplicate Contact", "13700001234", "13600005678", "Technology", "Duplicate merchant", "OperatorPass916!", "ReviewerPass916!", "127.0.0.1")));
            org.junit.jupiter.api.Assertions.assertEquals(userCount, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sys_user
                WHERE description LIKE 'Merchant % requires first-login password change'
                """, Integer.class));
            org.junit.jupiter.api.Assertions.assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM biz_merchant WHERE tenant_id = ? AND deleted = 0
                """, Integer.class, tenantId));
        });
    }

    protected void verifyMerchantScopedQueries() {
        long tenantId = 917L;
        long rootAgentId = 91701L;
        long childAgentId = 91702L;
        long siblingAgentId = 91703L;
        long grandchildAgentId = 91704L;
        long rootUserId = 91711L;
        long childUserId = 91712L;
        long rootMerchantId = 917101L;
        long childMerchantId = 917102L;
        long siblingMerchantId = 917103L;
        long grandchildMerchantId = 917104L;
        MerchantActionPermissions permissions = MerchantActionPermissions.all();

        TenantUtils.execute(tenantId, () -> {
            agentHierarchyService.register(registration(rootAgentId, tenantId, 0L, rootUserId, "MER-QUERY-ROOT"));
            agentHierarchyService
                .register(registration(childAgentId, tenantId, rootAgentId, childUserId, "MER-QUERY-CHILD"));
            agentHierarchyService
                .register(registration(siblingAgentId, tenantId, rootAgentId, 91713L, "MER-QUERY-SIBLING"));
            agentHierarchyService
                .register(registration(grandchildAgentId, tenantId, childAgentId, 91714L, "MER-QUERY-GRANDCHILD"));

            merchantMasterService
                .register(rootUserId, merchantRegistration(rootMerchantId, tenantId, rootAgentId, 917201L, 917202L, "MER-ROOT", "1"
                    .repeat(64)));
            merchantMasterService
                .register(rootUserId, merchantRegistration(childMerchantId, tenantId, childAgentId, 917203L, 917204L, "MER-CHILD", "2"
                    .repeat(64)));
            merchantMasterService
                .register(rootUserId, merchantRegistration(siblingMerchantId, tenantId, siblingAgentId, 917205L, 917206L, "MER-SIBLING", "3"
                    .repeat(64)));
            merchantMasterService
                .register(rootUserId, merchantRegistration(grandchildMerchantId, tenantId, grandchildAgentId, 917207L, 917208L, "MER-GRANDCHILD", "4"
                    .repeat(64)));
            merchantMasterService
                .changeLifecycle(tenantId, rootUserId, childMerchantId, MerchantStatus.ENABLED, "query fixture enabled", 0L);
            merchantMasterService
                .changeLifecycle(tenantId, rootUserId, siblingMerchantId, MerchantStatus.ENABLED, "query fixture enabled", 0L);
            merchantMasterService
                .changeLifecycle(tenantId, rootUserId, grandchildMerchantId, MerchantStatus.DISABLED, "query fixture disabled", 0L);

            insertQueryUser(tenantId, 917201L, "merchant_root_operator");
            insertQueryUser(tenantId, 917202L, "merchant_root_reviewer");
            insertQueryUser(tenantId, 917203L, "merchant_child_operator");
            insertQueryUser(tenantId, 917204L, "merchant_child_reviewer");
            insertQueryUser(tenantId, 917205L, "merchant_sibling_operator");
            insertQueryUser(tenantId, 917206L, "merchant_sibling_reviewer");
            insertQueryUser(tenantId, 917207L, "merchant_grandchild_operator");
            insertQueryUser(tenantId, 917208L, "merchant_grandchild_reviewer");
            jdbcTemplate
                .update("UPDATE biz_merchant SET legal_representative_name = ? WHERE tenant_id = ? AND id = ?", "Alice Child", tenantId, childMerchantId);
            jdbcTemplate
                .update("UPDATE biz_merchant SET legal_representative_name = ? WHERE tenant_id = ? AND id = ?", "Bob Sibling", tenantId, siblingMerchantId);

            LocalDateTime baseTime = LocalDateTime.of(2026, 8, 21, 10, 0);
            insertPricingVersion(tenantId, childAgentId, 917501L, 1, "CHANNEL-A", "PRODUCT-A", "0.01000000", baseTime);
            insertPricingVersion(tenantId, childAgentId, 917502L, 2, "CHANNEL-A", "PRODUCT-A", "0.02000000", baseTime
                .plusMinutes(30));
            insertPricingVersion(tenantId, childAgentId, 917503L, 1, "CHANNEL-B", "PRODUCT-B", "0.03000000", baseTime
                .plusMinutes(45));
            insertQueryKycVersion(tenantId, childMerchantId, 917401L, 1, 917501L, baseTime);
            insertQueryKycVersion(tenantId, childMerchantId, 917402L, 2, 917502L, baseTime.plusHours(1));
            insertQueryKycVersion(tenantId, childMerchantId, 917403L, 3, 917503L, baseTime.plusHours(2));
            insertQueryApplication(tenantId, childMerchantId, childAgentId, 917601L, "APP-OLD-A", "CHANNEL-A", 917401L, "FAILED", "FAILED", baseTime);
            insertQueryApplication(tenantId, childMerchantId, childAgentId, 917602L, "APP-LATEST-A", "CHANNEL-A", 917402L, "SUCCEEDED", "SUCCEEDED", baseTime
                .plusHours(1));
            insertQueryApplication(tenantId, childMerchantId, childAgentId, 917603L, "APP-LATEST-B", "CHANNEL-B", 917403L, "UNDER_REVIEW", "PROCESSING", baseTime
                .plusHours(2));
            insertQueryApplication(tenantId, grandchildMerchantId, grandchildAgentId, 917604L, "APP-GRANDCHILD", "CHANNEL-A", null, "SUCCEEDED", "SUCCEEDED", baseTime
                .plusHours(3));

            MerchantListQuery allQuery = new MerchantListQuery(null, "MER-", null, null, null, null, null, null, null, null, null, null, null, 1, 2, "127.0.0.1");
            MerchantPage first = merchantQueryService.page(tenantId, rootUserId, allQuery, permissions);
            MerchantPage repeated = merchantQueryService.page(tenantId, rootUserId, allQuery, permissions);
            org.junit.jupiter.api.Assertions.assertEquals(4L, first.total());
            org.junit.jupiter.api.Assertions.assertEquals(first.list()
                .stream()
                .map(item -> item.id())
                .toList(), repeated.list().stream().map(item -> item.id()).toList());

            MerchantPage childScope = merchantQueryService
                .page(tenantId, childUserId, new MerchantListQuery(null, null, null, null, null, null, null, null, null, null, null, null, null, 1, 20, "127.0.0.1"), permissions);
            org.junit.jupiter.api.Assertions.assertEquals(Set.of(childMerchantId, grandchildMerchantId), childScope
                .list()
                .stream()
                .map(item -> item.id())
                .collect(java.util.stream.Collectors.toSet()));
            org.junit.jupiter.api.Assertions.assertEquals(2L, childScope.total());

            MerchantPage directIdentity = merchantQueryService
                .page(tenantId, 917203L, new MerchantListQuery(null, null, "merchant_child_operator", null, null, null, null, null, null, null, null, null, null, 1, 20, "127.0.0.1"), permissions);
            org.junit.jupiter.api.Assertions.assertEquals(List.of(childMerchantId), directIdentity.list()
                .stream()
                .map(item -> item.id())
                .toList());

            MerchantPage combined = merchantQueryService
                .page(tenantId, rootUserId, new MerchantListQuery(null, null, null, "Legal MER-CHILD", null, "Contact", "Alice", MerchantType.ENTERPRISE, childAgentId, "CHANNEL-A", MerchantStatus.ENABLED, null, null, 1, 20, "127.0.0.1"), permissions);
            org.junit.jupiter.api.Assertions.assertEquals(List.of(childMerchantId), combined.list()
                .stream()
                .map(item -> item.id())
                .toList());

            MerchantDetail childDetail = merchantQueryService
                .get(tenantId, rootUserId, childMerchantId, permissions, "127.0.0.1");
            org.junit.jupiter.api.Assertions.assertEquals(MerchantStatus.ENABLED, childDetail.status());
            org.junit.jupiter.api.Assertions.assertEquals(2, childDetail.channels().size());
            MerchantChannelSummary channelA = childDetail.channels()
                .stream()
                .filter(channel -> "CHANNEL-A".equals(channel.channelCode()))
                .findFirst()
                .orElseThrow();
            org.junit.jupiter.api.Assertions.assertEquals(917602L, channelA.applicationId());
            org.junit.jupiter.api.Assertions.assertEquals(917502L, channelA.pricing().pricingVersionId());
            org.junit.jupiter.api.Assertions.assertEquals(2, channelA.pricing().versionNo());
            org.junit.jupiter.api.Assertions.assertTrue(childDetail.actions().contains(MerchantAction.ADJUST_LIMIT));
            MerchantActionPermissions viewOnly = new MerchantActionPermissions(true, false, false, false, false, false, false);
            org.junit.jupiter.api.Assertions.assertEquals(List.of(MerchantAction.VIEW), merchantQueryService
                .get(tenantId, rootUserId, childMerchantId, viewOnly, "127.0.0.1")
                .actions());

            MerchantDetail disabled = merchantQueryService
                .get(tenantId, rootUserId, grandchildMerchantId, permissions, "127.0.0.1");
            org.junit.jupiter.api.Assertions.assertFalse(disabled.actions().contains(MerchantAction.START_ONBOARDING));
            org.junit.jupiter.api.Assertions.assertFalse(disabled.actions().contains(MerchantAction.ADJUST_LIMIT));
            org.junit.jupiter.api.Assertions.assertTrue(disabled.actions().contains(MerchantAction.VIEW_LIMIT_HISTORY));

            org.junit.jupiter.api.Assertions
                .assertThrows(MerchantAccessDeniedException.class, () -> merchantQueryService
                    .get(tenantId, childUserId, siblingMerchantId, permissions, "127.0.0.1"));
            org.junit.jupiter.api.Assertions.assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM biz_security_audit
                WHERE tenant_id = ? AND actor_user_id = ? AND object_id = ? AND action = 'MERCHANT_READ_DENIED'
                """, Integer.class, tenantId, childUserId, siblingMerchantId));
        });
    }

    protected void verifyChannelEligibility() {
        long tenantId = 918L;
        long rootAgentId = 91801L;
        long merchantAgentId = 91802L;
        long siblingAgentId = 91803L;
        long rootUserId = 91811L;
        long merchantAgentUserId = 91812L;
        long siblingUserId = 91813L;
        long merchantId = 918101L;
        long operatorUserId = 918201L;

        TenantUtils.execute(tenantId, () -> {
            agentHierarchyService.register(registration(rootAgentId, tenantId, 0L, rootUserId, "ELIGIBILITY-ROOT"));
            agentHierarchyService
                .register(registration(merchantAgentId, tenantId, rootAgentId, merchantAgentUserId, "ELIGIBILITY-MERCHANT"));
            agentHierarchyService
                .register(registration(siblingAgentId, tenantId, rootAgentId, siblingUserId, "ELIGIBILITY-SIBLING"));
            merchantMasterService
                .register(rootUserId, merchantRegistration(merchantId, tenantId, merchantAgentId, operatorUserId, 918202L, "ELIGIBLE-MERCHANT", "e"
                    .repeat(64)));

            LocalDateTime baseTime = LocalDateTime.of(2026, 8, 20, 8, 0);
            insertPricingVersion(tenantId, merchantAgentId, 918501L, 1, "CHANNEL-A", "PRODUCT-A", "0.01000000", baseTime);
            insertPricingVersion(tenantId, merchantAgentId, 918502L, 1, "CHANNEL-B", "PRODUCT-B", "0.02000000", baseTime);
            insertPricingVersion(tenantId, merchantAgentId, 918503L, 1, "CHANNEL-C", "PRODUCT-C", "0.03000000", baseTime);
            jdbcTemplate.update("""
                INSERT INTO biz_agent_merchant_default_version
                (id, tenant_id, agent_id, version_no, default_payload_json, effective_time, status,
                 create_user, create_time, deleted)
                VALUES (?, ?, ?, 1, ?, ?, 'PUBLISHED', ?, ?, 0)
                """, 918401L, tenantId, merchantAgentId, """
                {"products":[
                  {"channelCode":"CHANNEL-A","productCode":"PRODUCT-A","pricingVersionId":918501},
                  {"channelCode":"CHANNEL-B","productCode":"PRODUCT-B","pricingVersionId":918502},
                  {"channelCode":"CHANNEL-C","productCode":"PRODUCT-C","pricingVersionId":918503}
                ]}
                """, baseTime, rootUserId, baseTime);

            insertChannelProductVersion(918601L, tenantId, "CHANNEL-A", "PRODUCT-A", "CFG-A-1", "REQ-A-1", "[\"ENTERPRISE\"]", "ENABLED", baseTime);
            insertChannelProductVersion(918602L, tenantId, "CHANNEL-B", "PRODUCT-B", "CFG-B-1", "REQ-B-1", "[\"INDIVIDUAL\"]", "ENABLED", baseTime);
            insertChannelProductVersion(918603L, tenantId, "CHANNEL-C", "PRODUCT-C", "CFG-C-1", "REQ-C-1", "[\"ENTERPRISE\"]", "DISABLED", baseTime);
            insertChannelProductVersion(919601L, 919L, "CHANNEL-A", "PRODUCT-A", "OTHER-TENANT", "REQ-OTHER", "[\"ENTERPRISE\"]", "DISABLED", baseTime
                .plusHours(4));

            List<EligibleChannel> eligible = channelEligibilityService.list(tenantId, merchantAgentUserId, merchantId);
            org.junit.jupiter.api.Assertions.assertEquals(1, eligible.size());
            EligibleChannel channel = eligible.get(0);
            org.junit.jupiter.api.Assertions.assertEquals("CHANNEL-A", channel.channelCode());
            org.junit.jupiter.api.Assertions.assertEquals("PRODUCT-A", channel.productCode());
            org.junit.jupiter.api.Assertions.assertEquals("CFG-A-1", channel.channelConfigVersion());
            org.junit.jupiter.api.Assertions.assertEquals("REQ-A-1", channel.requirementVersion());
            org.junit.jupiter.api.Assertions.assertEquals(918501L, channel.pricingVersionId());
            org.junit.jupiter.api.Assertions.assertEquals(918401L, channel.merchantDefaultVersionId());
            org.junit.jupiter.api.Assertions.assertEquals(List
                .of("BUSINESS_LICENSE", "LEGAL_REPRESENTATIVE_ID_FRONT"), channel.requirements()
                    .requiredEvidenceTypes());
            org.junit.jupiter.api.Assertions.assertEquals("REQ-A-1", channelEligibilityService
                .list(tenantId, operatorUserId, merchantId)
                .get(0)
                .requirementVersion());
            org.junit.jupiter.api.Assertions
                .assertThrows(MerchantAccessDeniedException.class, () -> channelEligibilityService
                    .list(tenantId, siblingUserId, merchantId));

            insertChannelProductVersion(918604L, tenantId, "CHANNEL-A", "PRODUCT-A", "CFG-A-2", "REQ-A-2", "[\"ENTERPRISE\"]", "DISABLED", baseTime
                .plusHours(1));
            org.junit.jupiter.api.Assertions.assertTrue(channelEligibilityService
                .list(tenantId, merchantAgentUserId, merchantId)
                .isEmpty());
            org.junit.jupiter.api.Assertions.assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
                UPDATE biz_channel_product_version SET status = 'ENABLED' WHERE id = ?
                """, 918604L));

            insertChannelProductVersion(918605L, tenantId, "CHANNEL-A", "PRODUCT-A", "CFG-A-3", "REQ-A-3", "[\"ENTERPRISE\"]", "ENABLED", baseTime
                .plusHours(2));
            org.junit.jupiter.api.Assertions.assertEquals("REQ-A-3", channelEligibilityService
                .list(tenantId, merchantAgentUserId, merchantId)
                .get(0)
                .requirementVersion());
            merchantMasterService
                .changeLifecycle(tenantId, rootUserId, merchantId, MerchantStatus.DISABLED, "eligibility disabled", 0L);
            org.junit.jupiter.api.Assertions.assertThrows(MerchantDomainException.class, () -> channelEligibilityService
                .list(tenantId, merchantAgentUserId, merchantId));
        });
    }

    protected void verifyOnboardingDraftPersistence() {
        long tenantId = 920L;
        long rootAgentId = 92001L;
        long merchantAgentId = 92002L;
        long siblingAgentId = 92003L;
        long rootUserId = 92011L;
        long merchantAgentUserId = 92012L;
        long siblingUserId = 92013L;
        long merchantId = 920101L;
        long operatorUserId = 920201L;

        TenantUtils.execute(tenantId, () -> {
            agentHierarchyService.register(registration(rootAgentId, tenantId, 0L, rootUserId, "DRAFT-ROOT"));
            agentHierarchyService
                .register(registration(merchantAgentId, tenantId, rootAgentId, merchantAgentUserId, "DRAFT-MERCHANT"));
            agentHierarchyService
                .register(registration(siblingAgentId, tenantId, rootAgentId, siblingUserId, "DRAFT-SIBLING"));
            merchantMasterService
                .register(rootUserId, merchantRegistration(merchantId, tenantId, merchantAgentId, operatorUserId, 920202L, "DRAFT-MERCHANT", "f"
                    .repeat(64)));

            LocalDateTime baseTime = LocalDateTime.of(2026, 8, 20, 9, 0);
            insertPricingVersion(tenantId, merchantAgentId, 920501L, 1, "CHANNEL-D", "PRODUCT-D", "0.01000000", baseTime);
            jdbcTemplate.update("""
                INSERT INTO biz_agent_merchant_default_version
                (id, tenant_id, agent_id, version_no, default_payload_json, effective_time, status,
                 create_user, create_time, deleted)
                VALUES (?, ?, ?, 1, ?, ?, 'PUBLISHED', ?, ?, 0)
                """, 920401L, tenantId, merchantAgentId, """
                {"products":[
                  {"channelCode":"CHANNEL-D","productCode":"PRODUCT-D","pricingVersionId":920501}
                ]}
                """, baseTime, rootUserId, baseTime);
            insertChannelProductVersion(920601L, tenantId, "CHANNEL-D", "PRODUCT-D", "CFG-D-1", "REQ-D-1", "[\"ENTERPRISE\"]", "ENABLED", baseTime);

            OnboardingDraftView created = onboardingDraftService
                .createOrLoad(tenantId, merchantAgentUserId, merchantId, "CHANNEL-D", "PRODUCT-D", "127.0.0.1");
            org.junit.jupiter.api.Assertions.assertTrue(created.channelEligible());
            org.junit.jupiter.api.Assertions.assertEquals("REQ-D-1", created.currentRequirementVersion());
            org.junit.jupiter.api.Assertions.assertEquals(0L, created.draft().rowVersion());
            org.junit.jupiter.api.Assertions.assertEquals(1, created.draft().savedStep());
            org.junit.jupiter.api.Assertions.assertTrue(created.draft().completedSteps().isEmpty());
            org.junit.jupiter.api.Assertions.assertEquals("PRODUCT-D", jdbcTemplate.queryForObject("""
                SELECT product_code FROM biz_onboarding_application WHERE tenant_id = ? AND id = ?
                """, String.class, tenantId, created.draft().applicationId()));
            org.junit.jupiter.api.Assertions.assertEquals("ACTIVE", jdbcTemplate.queryForObject("""
                SELECT active_draft_guard FROM biz_onboarding_application WHERE tenant_id = ? AND id = ?
                """, String.class, tenantId, created.draft().applicationId()));
            org.junit.jupiter.api.Assertions.assertEquals("[]", jdbcTemplate.queryForObject("""
                SELECT step_completion_json FROM biz_kyc_version WHERE tenant_id = ? AND id = ?
                """, String.class, tenantId, created.draft().kycVersionId()));
            org.junit.jupiter.api.Assertions.assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM biz_kyc_draft_default_snapshot
                WHERE tenant_id = ? AND kyc_version_id = ?
                """, Integer.class, tenantId, created.draft().kycVersionId()));

            OnboardingDraftView repeated = onboardingDraftService
                .createOrLoad(tenantId, operatorUserId, merchantId, "CHANNEL-D", "PRODUCT-D", "127.0.0.1");
            org.junit.jupiter.api.Assertions.assertEquals(created.draft().applicationId(), repeated.draft()
                .applicationId());
            org.junit.jupiter.api.Assertions.assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM biz_onboarding_application
                WHERE tenant_id = ? AND merchant_id = ? AND active_draft_guard = 'ACTIVE'
                """, Integer.class, tenantId, merchantId));

            OnboardingDraftView saved = onboardingDraftService
                .saveProgress(tenantId, merchantAgentUserId, merchantId, created.draft().applicationId(), 3, List
                    .of(1, 2), 0L, "127.0.0.1");
            org.junit.jupiter.api.Assertions.assertEquals(1L, saved.draft().rowVersion());
            org.junit.jupiter.api.Assertions.assertEquals(List.of(1, 2), saved.draft().completedSteps());
            OnboardingDraftView restored = onboardingDraftService.load(tenantId, operatorUserId, merchantId, created
                .draft()
                .applicationId());
            org.junit.jupiter.api.Assertions.assertEquals(3, restored.draft().savedStep());
            org.junit.jupiter.api.Assertions.assertEquals(List.of(1, 2), restored.draft().completedSteps());
            org.junit.jupiter.api.Assertions
                .assertThrows(OnboardingDraftConflictException.class, () -> onboardingDraftService
                    .saveProgress(tenantId, merchantAgentUserId, merchantId, created.draft().applicationId(), 4, List
                        .of(1, 2, 3), 0L, "127.0.0.1"));
            org.junit.jupiter.api.Assertions.assertThrows(MerchantDomainException.class, () -> onboardingDraftService
                .saveProgress(tenantId, merchantAgentUserId, merchantId, created.draft().applicationId(), 3, List
                    .of(1, 3), 1L, "127.0.0.1"));
            org.junit.jupiter.api.Assertions
                .assertThrows(MerchantAccessDeniedException.class, () -> onboardingDraftService
                    .load(tenantId, siblingUserId, merchantId, created.draft().applicationId()));

            insertChannelProductVersion(920602L, tenantId, "CHANNEL-D", "PRODUCT-D", "CFG-D-2", "REQ-D-2", "[\"ENTERPRISE\"]", "DISABLED", baseTime
                .plusHours(1));
            OnboardingDraftView unavailable = onboardingDraftService
                .load(tenantId, merchantAgentUserId, merchantId, created.draft().applicationId());
            org.junit.jupiter.api.Assertions.assertFalse(unavailable.channelEligible());
            org.junit.jupiter.api.Assertions.assertEquals("REQ-D-1", unavailable.draft().requirementVersion());
            merchantMasterService
                .changeLifecycle(tenantId, rootUserId, merchantId, MerchantStatus.DISABLED, "draft merchant disabled", 0L);
            org.junit.jupiter.api.Assertions.assertFalse(onboardingDraftService
                .load(tenantId, merchantAgentUserId, merchantId, created.draft().applicationId())
                .channelEligible());
            org.junit.jupiter.api.Assertions.assertThrows(MerchantDomainException.class, () -> onboardingDraftService
                .saveProgress(tenantId, merchantAgentUserId, merchantId, created.draft().applicationId(), 4, List
                    .of(1, 2, 3), 1L, "127.0.0.1"));
            org.junit.jupiter.api.Assertions.assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM biz_security_audit
                WHERE tenant_id = ? AND object_id = ? AND action = 'ONBOARDING_DRAFT_CREATE'
                """, Integer.class, tenantId, created.draft().applicationId()));
            org.junit.jupiter.api.Assertions.assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM biz_security_audit
                WHERE tenant_id = ? AND object_id = ? AND action = 'ONBOARDING_DRAFT_SAVE'
                """, Integer.class, tenantId, created.draft().applicationId()));
        });
    }

    protected void verifySameMerchantKycReuse() {
        long tenantId = 922L;
        long rootAgentId = 92201L;
        long merchantAgentId = 92202L;
        long siblingAgentId = 92203L;
        long rootUserId = 92211L;
        long merchantAgentUserId = 92212L;
        long siblingUserId = 92213L;
        long merchantId = 922101L;
        long otherMerchantId = 922102L;

        TenantUtils.execute(tenantId, () -> {
            agentHierarchyService.register(registration(rootAgentId, tenantId, 0L, rootUserId, "REUSE-ROOT"));
            agentHierarchyService
                .register(registration(merchantAgentId, tenantId, rootAgentId, merchantAgentUserId, "REUSE-MERCHANT"));
            agentHierarchyService
                .register(registration(siblingAgentId, tenantId, rootAgentId, siblingUserId, "REUSE-SIBLING"));
            merchantMasterService
                .register(rootUserId, merchantRegistration(merchantId, tenantId, merchantAgentId, 922201L, 922202L, "REUSE-TARGET", "a"
                    .repeat(64)));
            merchantMasterService
                .register(rootUserId, merchantRegistration(otherMerchantId, tenantId, merchantAgentId, 922203L, 922204L, "REUSE-OTHER", "b"
                    .repeat(64)));

            LocalDateTime baseTime = LocalDateTime.of(2026, 8, 20, 10, 0);
            insertPricingVersion(tenantId, merchantAgentId, 922501L, 1, "CHANNEL-R", "PRODUCT-R", "0.01000000", baseTime);
            jdbcTemplate.update("""
                INSERT INTO biz_agent_merchant_default_version
                (id, tenant_id, agent_id, version_no, default_payload_json, effective_time, status,
                 create_user, create_time, deleted)
                VALUES (?, ?, ?, 1, ?, ?, 'PUBLISHED', ?, ?, 0)
                """, 922601L, tenantId, merchantAgentId, """
                {"products":[
                  {"channelCode":"CHANNEL-R","productCode":"PRODUCT-R","pricingVersionId":922501}
                ]}
                """, baseTime, rootUserId, baseTime);
            insertChannelProductVersion(922701L, tenantId, "CHANNEL-R", "PRODUCT-R", "CFG-R-1", "REQ-R-1", "[\"ENTERPRISE\"]", "ENABLED", baseTime, "[\"BUSINESS_SCOPE\"]");

            insertReusableKycVersion(tenantId, merchantId, merchantAgentId, 922301L, 922401L, 1, "SOURCE-A", LocalDate
                .of(2025, 1, 1), LocalDate.of(2027, 1, 1), baseTime);
            insertReusableKycVersion(tenantId, merchantId, merchantAgentId, 922302L, 922402L, 2, "SOURCE-EXPIRED", LocalDate
                .of(2024, 1, 1), LocalDate.of(2026, 8, 21), baseTime.plusHours(1));
            insertReusableKycVersion(tenantId, otherMerchantId, merchantAgentId, 922303L, 922403L, 1, "OTHER-MERCHANT", LocalDate
                .of(2025, 1, 1), LocalDate.of(2027, 1, 1), baseTime.plusHours(2));

            OnboardingDraftView target = onboardingDraftService
                .createOrLoad(tenantId, merchantAgentUserId, merchantId, "CHANNEL-R", "PRODUCT-R", "127.0.0.1");
            List<KycReuseSourceView> sources = kycReuseService
                .listSources(tenantId, merchantAgentUserId, merchantId, target.draft().applicationId());
            org.junit.jupiter.api.Assertions.assertEquals(2, sources.size());
            KycReuseSourceView valid = sources.stream()
                .filter(source -> source.kycVersionId().equals(922401L))
                .findFirst()
                .orElseThrow();
            org.junit.jupiter.api.Assertions.assertEquals("SOURCE-A", valid.sourceChannelCode());
            org.junit.jupiter.api.Assertions.assertEquals("91************5678", valid.legalIdentifierMasked());
            org.junit.jupiter.api.Assertions.assertEquals(List
                .of(KycReuseField.LEGAL_NAME, KycReuseField.LEGAL_IDENTIFIER, KycReuseField.LICENSE_DATES), valid
                    .reusableFields());
            org.junit.jupiter.api.Assertions.assertEquals(List.of(KycReuseField.BUSINESS_SCOPE), valid
                .fieldsRequiringReconfirmation());
            KycReuseSourceView expired = sources.stream()
                .filter(source -> source.kycVersionId().equals(922402L))
                .findFirst()
                .orElseThrow();
            org.junit.jupiter.api.Assertions.assertFalse(expired.reusableFields()
                .contains(KycReuseField.LICENSE_DATES));
            org.junit.jupiter.api.Assertions.assertTrue(expired.fieldsRequiringReconfirmation()
                .contains(KycReuseField.LICENSE_DATES));
            org.junit.jupiter.api.Assertions.assertThrows(MerchantAccessDeniedException.class, () -> kycReuseService
                .listSources(tenantId, siblingUserId, merchantId, target.draft().applicationId()));

            var reused = kycReuseService.reuse(tenantId, merchantAgentUserId, merchantId, target.draft()
                .applicationId(), 922401L, Set
                    .of(KycReuseField.LEGAL_NAME, KycReuseField.LEGAL_IDENTIFIER, KycReuseField.LICENSE_DATES), 0L, "127.0.0.1");
            org.junit.jupiter.api.Assertions.assertEquals(1L, reused.rowVersion());
            org.junit.jupiter.api.Assertions.assertEquals(922401L, jdbcTemplate.queryForObject("""
                SELECT source_kyc_version_id FROM biz_kyc_version WHERE tenant_id = ? AND id = ?
                """, Long.class, tenantId, target.draft().kycVersionId()));
            org.junit.jupiter.api.Assertions.assertEquals("Reusable Legal SOURCE-A", jdbcTemplate.queryForObject("""
                SELECT legal_name FROM biz_kyc_version WHERE tenant_id = ? AND id = ?
                """, String.class, tenantId, target.draft().kycVersionId()));
            org.junit.jupiter.api.Assertions.assertEquals("91************5678", jdbcTemplate.queryForObject("""
                SELECT legal_identifier_masked FROM biz_kyc_version WHERE tenant_id = ? AND id = ?
                """, String.class, tenantId, target.draft().kycVersionId()));
            org.junit.jupiter.api.Assertions.assertNull(jdbcTemplate.queryForObject("""
                SELECT business_scope FROM biz_kyc_version WHERE tenant_id = ? AND id = ?
                """, String.class, tenantId, target.draft().kycVersionId()));
            String provenance = jdbcTemplate.queryForObject("""
                SELECT reuse_provenance_json FROM biz_kyc_version WHERE tenant_id = ? AND id = ?
                """, String.class, tenantId, target.draft().kycVersionId());
            org.junit.jupiter.api.Assertions.assertTrue(provenance.contains("SOURCE-A"));
            org.junit.jupiter.api.Assertions.assertTrue(provenance.contains("LEGAL_IDENTIFIER"));
            org.junit.jupiter.api.Assertions.assertFalse(provenance.contains("Reusable Legal"));
            org.junit.jupiter.api.Assertions.assertFalse(provenance.contains("91350211"));

            org.junit.jupiter.api.Assertions.assertThrows(OnboardingDraftConflictException.class, () -> kycReuseService
                .reuse(tenantId, merchantAgentUserId, merchantId, target.draft().applicationId(), 922401L, Set
                    .of(KycReuseField.LEGAL_NAME), 0L, "127.0.0.1"));
            org.junit.jupiter.api.Assertions.assertThrows(MerchantDomainException.class, () -> kycReuseService
                .reuse(tenantId, merchantAgentUserId, merchantId, target.draft().applicationId(), 922401L, Set
                    .of(KycReuseField.BUSINESS_SCOPE), 1L, "127.0.0.1"));
            org.junit.jupiter.api.Assertions.assertThrows(MerchantAccessDeniedException.class, () -> kycReuseService
                .reuse(tenantId, merchantAgentUserId, merchantId, target.draft().applicationId(), 922403L, Set
                    .of(KycReuseField.LEGAL_NAME), 1L, "127.0.0.1"));
            org.junit.jupiter.api.Assertions.assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM biz_security_audit
                WHERE tenant_id = ? AND object_id = ? AND action = 'KYC_DRAFT_REUSE'
                """, Integer.class, tenantId, target.draft().kycVersionId()));
        });
    }

    protected void verifyOnboardingEvidenceCollection() {
        long tenantId = 924L;
        long rootAgentId = 92401L;
        long merchantAgentId = 92402L;
        long siblingAgentId = 92403L;
        long rootUserId = 92411L;
        long merchantAgentUserId = 92412L;
        long siblingUserId = 92413L;
        long merchantId = 924101L;

        TenantUtils.execute(tenantId, () -> {
            agentHierarchyService.register(registration(rootAgentId, tenantId, 0L, rootUserId, "EVIDENCE-ROOT"));
            agentHierarchyService
                .register(registration(merchantAgentId, tenantId, rootAgentId, merchantAgentUserId, "EVIDENCE-MERCHANT"));
            agentHierarchyService
                .register(registration(siblingAgentId, tenantId, rootAgentId, siblingUserId, "EVIDENCE-SIBLING"));
            merchantMasterService
                .register(rootUserId, merchantRegistration(merchantId, tenantId, merchantAgentId, 924201L, 924202L, "EVIDENCE-MERCHANT", "d"
                    .repeat(64)));
            LocalDateTime baseTime = LocalDateTime.of(2026, 8, 20, 12, 0);
            insertPricingVersion(tenantId, merchantAgentId, 924501L, 1, "CHANNEL-E", "PRODUCT-E", "0.01000000", baseTime);
            jdbcTemplate.update("""
                INSERT INTO biz_agent_merchant_default_version
                (id, tenant_id, agent_id, version_no, default_payload_json, effective_time, status,
                 create_user, create_time, deleted)
                VALUES (?, ?, ?, 1, ?, ?, 'PUBLISHED', ?, ?, 0)
                """, 924601L, tenantId, merchantAgentId, """
                {"products":[
                  {"channelCode":"CHANNEL-E","productCode":"PRODUCT-E","pricingVersionId":924501}
                ]}
                """, baseTime, rootUserId, baseTime);
            insertChannelProductVersion(924701L, tenantId, "CHANNEL-E", "PRODUCT-E", "CFG-E-1", "REQ-E-1", "[\"ENTERPRISE\"]", "ENABLED", baseTime, "[]", 1);

            OnboardingDraftView draft = onboardingDraftService
                .createOrLoad(tenantId, merchantAgentUserId, merchantId, "CHANNEL-E", "PRODUCT-E", "127.0.0.1");
            OnboardingEvidenceSummary initial = onboardingEvidenceService
                .summary(tenantId, merchantAgentUserId, merchantId, draft.draft().applicationId());
            org.junit.jupiter.api.Assertions.assertFalse(initial.complete());
            org.junit.jupiter.api.Assertions.assertEquals("REQ-E-1", initial.requirementVersion());
            org.junit.jupiter.api.Assertions.assertEquals(2, initial.evidenceTypes()
                .stream()
                .filter(OnboardingEvidenceSummary.EvidenceTypeStatus::required)
                .count());
            org.junit.jupiter.api.Assertions.assertThrows(KycAttachmentException.class, () -> onboardingEvidenceService
                .requireUploadAllowed(tenantId, merchantAgentUserId, draft.draft().kycVersionId(), "UNKNOWN_TYPE"));
            var optionalRule = onboardingEvidenceService.requireUploadAllowed(tenantId, merchantAgentUserId, draft
                .draft()
                .kycVersionId(), "SUPPLEMENT");
            org.junit.jupiter.api.Assertions.assertFalse(optionalRule.required());
            org.junit.jupiter.api.Assertions.assertEquals(1, optionalRule.maxOptionalAttachments());

            KycAttachment businessLicense = kycAttachmentRepository.insert(new KycAttachmentDraft(tenantId, draft
                .draft()
                .kycVersionId(), "BUSINESS_LICENSE", "private|evidence/business-license", "business.png", "png", "image/png", "image/png", 10L, "1"
                    .repeat(64), KycAttachmentScanStatus.CLEAN, KycAttachmentValidationStatus.VALID, 1, baseTime));
            KycAttachment legalRepresentative = kycAttachmentRepository.insert(new KycAttachmentDraft(tenantId, draft
                .draft()
                .kycVersionId(), "LEGAL_REPRESENTATIVE_ID_FRONT", "private|evidence/legal-front", "legal.png", "png", "image/png", "image/png", 10L, "2"
                    .repeat(64), KycAttachmentScanStatus.UNAVAILABLE, KycAttachmentValidationStatus.QUARANTINED, 2, baseTime));
            kycAttachmentRepository.insert(new KycAttachmentDraft(tenantId, draft.draft()
                .kycVersionId(), "SUPPLEMENT", "private|evidence/supplement", "supplement.pdf", "pdf", "application/pdf", "application/pdf", 10L, "3"
                    .repeat(64), KycAttachmentScanStatus.CLEAN, KycAttachmentValidationStatus.VALID, 3, baseTime));

            OnboardingEvidenceSummary pending = onboardingEvidenceService
                .summary(tenantId, merchantAgentUserId, merchantId, draft.draft().applicationId());
            org.junit.jupiter.api.Assertions.assertFalse(pending.complete());
            var legalStatus = pending.evidenceTypes()
                .stream()
                .filter(item -> "LEGAL_REPRESENTATIVE_ID_FRONT".equals(item.evidenceType()))
                .findFirst()
                .orElseThrow();
            org.junit.jupiter.api.Assertions.assertEquals(1, legalStatus.pendingScanCount());
            org.junit.jupiter.api.Assertions.assertEquals(1, legalStatus.invalidCount());
            org.junit.jupiter.api.Assertions.assertEquals(3, pending.attachments().size());
            org.junit.jupiter.api.Assertions
                .assertThrows(MerchantAccessDeniedException.class, () -> onboardingEvidenceService
                    .summary(tenantId, siblingUserId, merchantId, draft.draft().applicationId()));

            jdbcTemplate.update("""
                UPDATE biz_kyc_attachment SET scan_status = 'CLEAN', validation_status = 'VALID' WHERE id = ?
                """, legalRepresentative.id());
            OnboardingEvidenceSummary complete = onboardingEvidenceService
                .summary(tenantId, merchantAgentUserId, merchantId, draft.draft().applicationId());
            org.junit.jupiter.api.Assertions.assertTrue(complete.complete());
            org.junit.jupiter.api.Assertions.assertEquals(businessLicense.id(), complete.attachments()
                .get(0)
                .attachmentId());

            jdbcTemplate
                .update("""
                    INSERT INTO biz_channel_product_version
                    (id, tenant_id, channel_code, product_code, config_version, requirement_version,
                     supported_merchant_types_json, requirement_summary_json, status, effective_time,
                     create_user, create_time, deleted)
                    VALUES (?, ?, 'CHANNEL-E', 'PRODUCT-E', 'CFG-E-2', 'REQ-E-2', '["ENTERPRISE"]',
                            '{"requiredEvidenceTypes":["NEW_EVIDENCE"],"optionalEvidenceTypes":[],"maxSupplementAttachments":0,"reuseExcludedFields":[]}',
                            'ENABLED', ?, 1, ?, 0)
                    """, 924702L, tenantId, baseTime
                    .plusHours(1), baseTime.plusHours(1));
            OnboardingEvidenceSummary preserved = onboardingEvidenceService
                .summary(tenantId, merchantAgentUserId, merchantId, draft.draft().applicationId());
            org.junit.jupiter.api.Assertions.assertEquals("REQ-E-1", preserved.requirementVersion());
            org.junit.jupiter.api.Assertions.assertTrue(preserved.evidenceTypes()
                .stream()
                .anyMatch(item -> "BUSINESS_LICENSE".equals(item.evidenceType())));
            org.junit.jupiter.api.Assertions.assertFalse(preserved.evidenceTypes()
                .stream()
                .anyMatch(item -> "NEW_EVIDENCE".equals(item.evidenceType())));
        });
    }

    private void insertQueryUser(Long tenantId, Long userId, String username) {
        jdbcTemplate.update("""
            INSERT INTO sys_user
            (id, username, nickname, gender, status, is_system, dept_id, create_user, create_time, deleted, tenant_id)
            VALUES (?, ?, ?, 0, 1, ?, 1, 1, ?, 0, ?)
            """, userId, username, username, false, LocalDateTime.of(2026, 8, 21, 9, 0), tenantId);
    }

    private void insertChannelProductVersion(Long id,
                                             Long tenantId,
                                             String channelCode,
                                             String productCode,
                                             String configVersion,
                                             String requirementVersion,
                                             String merchantTypesJson,
                                             String status,
                                             LocalDateTime effectiveTime) {
        insertChannelProductVersion(id, tenantId, channelCode, productCode, configVersion, requirementVersion, merchantTypesJson, status, effectiveTime, "[]");
    }

    private void insertChannelProductVersion(Long id,
                                             Long tenantId,
                                             String channelCode,
                                             String productCode,
                                             String configVersion,
                                             String requirementVersion,
                                             String merchantTypesJson,
                                             String status,
                                             LocalDateTime effectiveTime,
                                             String reuseExcludedFieldsJson) {
        insertChannelProductVersion(id, tenantId, channelCode, productCode, configVersion, requirementVersion, merchantTypesJson, status, effectiveTime, reuseExcludedFieldsJson, 5);
    }

    private void insertChannelProductVersion(Long id,
                                             Long tenantId,
                                             String channelCode,
                                             String productCode,
                                             String configVersion,
                                             String requirementVersion,
                                             String merchantTypesJson,
                                             String status,
                                             LocalDateTime effectiveTime,
                                             String reuseExcludedFieldsJson,
                                             int maxSupplementAttachments) {
        String requirementSummary = """
            {"requiredEvidenceTypes":["BUSINESS_LICENSE","LEGAL_REPRESENTATIVE_ID_FRONT"],
             "optionalEvidenceTypes":["SUPPLEMENT"],"maxSupplementAttachments":%s,
             "reuseExcludedFields":%s}
            """.formatted(maxSupplementAttachments, reuseExcludedFieldsJson);
        jdbcTemplate
            .update("""
                INSERT INTO biz_channel_product_version
                (id, tenant_id, channel_code, product_code, config_version, requirement_version,
                 supported_merchant_types_json, requirement_summary_json, status, effective_time,
                 create_user, create_time, deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, 0)
                """, id, tenantId, channelCode, productCode, configVersion, requirementVersion, merchantTypesJson, requirementSummary, status, effectiveTime, effectiveTime);
    }

    private void insertReusableKycVersion(Long tenantId,
                                          Long merchantId,
                                          Long owningAgentId,
                                          Long applicationId,
                                          Long kycVersionId,
                                          Integer versionNo,
                                          String sourceChannelCode,
                                          LocalDate licenseIssueDate,
                                          LocalDate licenseExpiryDate,
                                          LocalDateTime frozenTime) {
        jdbcTemplate
            .update("""
                INSERT INTO biz_onboarding_application
                (id, tenant_id, application_no, merchant_id, owning_agent_id, channel_code, product_code,
                 requirement_version, channel_config_version, kyc_version_id, status, row_version,
                 create_time, deleted)
                VALUES (?, ?, ?, ?, ?, ?, 'SOURCE-PRODUCT', 'SOURCE-REQ-1', 'SOURCE-CFG-1', ?, 'SUCCEEDED', 0, ?, 0)
                """, applicationId, tenantId, "APP-" + sourceChannelCode, merchantId, owningAgentId, sourceChannelCode, kycVersionId, frozenTime);
        jdbcTemplate
            .update("""
                INSERT INTO biz_kyc_version
                (id, tenant_id, merchant_id, onboarding_application_id, version_no, requirement_version,
                 status, saved_step, step_completion_json, legal_name, legal_identifier_ciphertext,
                 legal_identifier_hash, legal_identifier_hash_key_version, legal_identifier_masked,
                 legal_identifier_key_version, license_issue_date, license_expiry_date, business_scope,
                 frozen_time, row_version, create_time, deleted)
                VALUES (?, ?, ?, ?, ?, 'SOURCE-REQ-1', 'SUBMITTED', 5, '[1,2,3,4,5]', ?, ?, ?,
                        'hash-v1', '91************5678', 'data-v1', ?, ?, 'Reusable Business Scope', ?, 0, ?, 0)
                """, kycVersionId, tenantId, merchantId, applicationId, versionNo, "Reusable Legal " + sourceChannelCode, new byte[] {
                9, 1, 3, 5, 0, 2, 1, 1}, "c".repeat(64), licenseIssueDate, licenseExpiryDate, frozenTime, frozenTime);
    }

    private void insertPricingVersion(Long tenantId,
                                      Long agentId,
                                      Long pricingVersionId,
                                      Integer versionNo,
                                      String channelCode,
                                      String productCode,
                                      String percentageCost,
                                      LocalDateTime effectiveTime) {
        String rules = "{\"percentageCost\":" + percentageCost + ",\"fixedFee\":1.00,\"profitShareRatio\":0.50000000}";
        jdbcTemplate
            .update("""
                INSERT INTO biz_agent_pricing_version
                (id, tenant_id, agent_id, version_no, channel_code, product_code, currency, pricing_rules_json,
                 effective_time, status, create_user, create_time, deleted)
                VALUES (?, ?, ?, ?, ?, ?, 'CNY', ?, ?, 'PUBLISHED', 1, ?, 0)
                """, pricingVersionId, tenantId, agentId, versionNo, channelCode, productCode, rules, effectiveTime, effectiveTime);
    }

    private void insertQueryKycVersion(Long tenantId,
                                       Long merchantId,
                                       Long kycVersionId,
                                       Integer versionNo,
                                       Long pricingVersionId,
                                       LocalDateTime createTime) {
        jdbcTemplate.update("""
            INSERT INTO biz_kyc_version
            (id, tenant_id, merchant_id, version_no, requirement_version, status, saved_step, legal_name,
             pricing_version_id, row_version, create_time, deleted)
            VALUES (?, ?, ?, ?, 'REQ-QUERY-1', 'SUBMITTED', 5, 'Query Legal Subject', ?, 0, ?, 0)
            """, kycVersionId, tenantId, merchantId, versionNo, pricingVersionId, createTime);
    }

    private void insertQueryApplication(Long tenantId,
                                        Long merchantId,
                                        Long owningAgentId,
                                        Long applicationId,
                                        String applicationNo,
                                        String channelCode,
                                        Long kycVersionId,
                                        String status,
                                        String channelFinalStatus,
                                        LocalDateTime createTime) {
        jdbcTemplate
            .update("""
                INSERT INTO biz_onboarding_application
                (id, tenant_id, application_no, merchant_id, owning_agent_id, channel_code, requirement_version,
                 kyc_version_id, status, reporting_status, agreement_status, card_binding_status,
                 reserve_account_status, channel_final_status, raw_channel_status, submitted_time, row_version,
                 create_time, deleted)
                VALUES (?, ?, ?, ?, ?, ?, 'REQ-QUERY-1', ?, ?, 'SUCCEEDED', 'SUCCEEDED', 'SUCCEEDED',
                        'SUCCEEDED', ?, ?, ?, 0, ?, 0)
                """, applicationId, tenantId, applicationNo, merchantId, owningAgentId, channelCode, kycVersionId, status, channelFinalStatus, "RAW-" + channelFinalStatus, createTime, createTime);
    }

    private void assertMerchantUser(Long userId, Long deptId, String password, String roleCode) {
        org.junit.jupiter.api.Assertions.assertEquals(deptId, jdbcTemplate.queryForObject("""
            SELECT dept_id FROM sys_user WHERE id = ?
            """, Long.class, userId));
        org.junit.jupiter.api.Assertions.assertNull(jdbcTemplate.queryForObject("""
            SELECT phone FROM sys_user WHERE id = ?
            """, String.class, userId));
        org.junit.jupiter.api.Assertions.assertTrue(jdbcTemplate.queryForObject("""
            SELECT must_change_password FROM sys_user WHERE id = ?
            """, Boolean.class, userId));
        String storedPassword = jdbcTemplate
            .queryForObject("SELECT password FROM sys_user WHERE id = ?", String.class, userId);
        org.junit.jupiter.api.Assertions.assertTrue(passwordEncoder.matches(password, storedPassword));
        org.junit.jupiter.api.Assertions.assertEquals(1, jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM sys_user_role ur
            JOIN sys_role r ON r.id = ur.role_id AND r.deleted = 0
            WHERE ur.user_id = ? AND r.code = ?
            """, Integer.class, userId, roleCode));
    }

    private Integer countMerchantManagementMenus(String roleCode) {
        return jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM sys_role_menu role_menu
            JOIN sys_role role_data ON role_data.id = role_menu.role_id AND role_data.deleted = 0
            WHERE role_data.code = ?
              AND role_menu.menu_id IN (
                690000000000100000, 690000000000100200, 690000000000100201, 690000000000100202,
                690000000000100203, 690000000000100204, 690000000000100205, 690000000000100206,
                690000000000100207
              )
            """, Integer.class, roleCode);
    }

    private String registerConcurrentMerchant(CountDownLatch ready,
                                              CountDownLatch start,
                                              Long tenantId,
                                              Long actorUserId,
                                              Long owningAgentId,
                                              Long merchantId,
                                              Long operatorUserId,
                                              Long reviewerUserId,
                                              String merchantNo,
                                              String legalSubjectHash) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            TenantUtils.execute(tenantId, () -> merchantMasterService
                .register(actorUserId, merchantRegistration(merchantId, tenantId, owningAgentId, operatorUserId, reviewerUserId, merchantNo, legalSubjectHash)));
            return "CREATED";
        } catch (MerchantDuplicateLegalSubjectException ex) {
            return "DUPLICATE";
        }
    }

    private AgentMerchantDefaultCreateCommand defaultCommand(Long tenantId,
                                                             Long actorUserId,
                                                             Long agentId,
                                                             AgentPricingVersion pricing,
                                                             LocalDateTime effectiveTime,
                                                             String reason) {
        return new AgentMerchantDefaultCreateCommand(tenantId, actorUserId, agentId, List
            .of(new AgentMerchantDefaultProduct(pricing.channelCode(), pricing.productCode(), pricing
                .id())), effectiveTime, null, reason, "127.0.0.1");
    }

    private void insertKycDraft(Long kycVersionId,
                                Long tenantId,
                                Long merchantId,
                                Integer versionNo,
                                LocalDateTime createTime) {
        jdbcTemplate.update("""
            INSERT INTO biz_kyc_version
            (id, tenant_id, merchant_id, version_no, requirement_version, status, saved_step, legal_name,
             row_version, create_time, deleted)
            VALUES (?, ?, ?, ?, 'REQ-DEFAULTS-1', 'DRAFT', 1, 'Defaults Merchant', 0, ?, 0)
            """, kycVersionId, tenantId, merchantId, versionNo, createTime);
    }

    private AgentPricingCreateCommand pricingCommand(Long tenantId,
                                                     Long actorUserId,
                                                     Long agentId,
                                                     String percentageCost,
                                                     String fixedFee,
                                                     String profitShareRatio,
                                                     LocalDateTime effectiveTime,
                                                     String reason) {
        return new AgentPricingCreateCommand(tenantId, actorUserId, agentId, "CHANNEL-A", "PRODUCT-A", "CNY", new AgentPricingRules(new BigDecimal(percentageCost), new BigDecimal(fixedFee), new BigDecimal(profitShareRatio)), effectiveTime, null, reason, "127.0.0.1");
    }

    private AgentRegistration registration(Long id, Long tenantId, Long parentId, Long userId, String agentNo) {
        return new AgentRegistration(id, tenantId, parentId, userId, agentNo, agentNo, "Contact", null, null);
    }

    private MerchantRegistration merchantRegistration(Long id,
                                                      Long tenantId,
                                                      Long owningAgentId,
                                                      Long operatorUserId,
                                                      Long reviewerUserId,
                                                      String merchantNo,
                                                      String legalSubjectHash) {
        EncryptedMobileNumber contactMobile = EncryptedMobileNumber.restore(new byte[] {1, 2, 3, 4}, "data-v1", "d"
            .repeat(64), "hash-v1", "138****5678");
        return new MerchantRegistration(id, tenantId, owningAgentId, merchantNo, MerchantType.ENTERPRISE, "Legal " + merchantNo, "Short " + merchantNo, legalSubjectHash, operatorUserId, reviewerUserId, "Contact", contactMobile, "Technology", "Integration-test merchant");
    }

    private Integer closureDepth(Long tenantId, Long ancestorId, Long descendantId) {
        return jdbcTemplate.queryForObject("""
            SELECT depth FROM biz_agent_closure
            WHERE tenant_id = ? AND ancestor_id = ? AND descendant_id = ?
            """, Integer.class, tenantId, ancestorId, descendantId);
    }
}
