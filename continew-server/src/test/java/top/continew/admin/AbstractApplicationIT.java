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
import top.continew.admin.channel.api.ChannelCallbackException;
import top.continew.admin.channel.api.ChannelConnectionConfigCatalog;
import top.continew.admin.channel.api.ChannelTransportAuditPort;
import top.continew.admin.channel.service.ChannelConfigurationLoader;
import top.continew.admin.channel.dto.ChannelBusinessType;
import top.continew.admin.channel.dto.ChannelCommandContext;
import top.continew.admin.channel.dto.ChannelConnectionStatus;
import top.continew.admin.channel.dto.ChannelEndpointConfiguration;
import top.continew.admin.channel.dto.ChannelMappedStatus;
import top.continew.admin.channel.dto.ChannelOnboardingState;
import top.continew.admin.channel.dto.ChannelOperation;
import top.continew.admin.channel.dto.ChannelOperationStatus;
import top.continew.admin.channel.dto.ChannelProductKey;
import top.continew.admin.channel.dto.RawChannelCallback;
import top.continew.admin.channel.dto.ChannelStageStatus;
import top.continew.admin.channel.dto.ChannelStatusMapping;
import top.continew.admin.channel.dto.ChannelTimeoutPolicy;
import top.continew.admin.channel.dto.ChannelTransportAuditRecord;
import top.continew.admin.channel.dto.ChannelTransportOutcome;
import top.continew.admin.channel.dto.VerifiedChannelCallback;
import top.continew.admin.channel.service.ChannelCallbackVerifier;
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
import top.continew.admin.merchant.onboarding.application.KycProfileSaveCommand;
import top.continew.admin.merchant.onboarding.application.KycProfileService;
import top.continew.admin.merchant.onboarding.application.KycProfileView;
import top.continew.admin.merchant.onboarding.application.OnboardingDraftConflictException;
import top.continew.admin.merchant.onboarding.application.OnboardingDraftService;
import top.continew.admin.merchant.onboarding.application.OnboardingDraftView;
import top.continew.admin.merchant.onboarding.application.OnboardingEvidenceService;
import top.continew.admin.merchant.onboarding.application.OnboardingEvidenceSummary;
import top.continew.admin.merchant.onboarding.application.OnboardingFinalPreview;
import top.continew.admin.merchant.onboarding.application.OnboardingFinalPreviewService;
import top.continew.admin.merchant.onboarding.application.OnboardingPricingService;
import top.continew.admin.merchant.onboarding.application.OnboardingPricingView;
import top.continew.admin.merchant.onboarding.application.OnboardingSubmissionBlockedException;
import top.continew.admin.merchant.onboarding.application.OnboardingSubmissionCommand;
import top.continew.admin.merchant.onboarding.application.OnboardingSubmissionResult;
import top.continew.admin.merchant.onboarding.application.OnboardingSubmissionService;
import top.continew.admin.merchant.onboarding.application.OnboardingSupplementDraft;
import top.continew.admin.merchant.onboarding.application.OnboardingSupplementService;
import top.continew.admin.merchant.onboarding.application.OnboardingWorkflowStartPayload;
import top.continew.admin.merchant.onboarding.application.OperatingPlatform;
import top.continew.admin.merchant.onboarding.application.OperatingPlatformService;
import top.continew.admin.merchant.onboarding.application.SettlementAccountSaveCommand;
import top.continew.admin.merchant.onboarding.application.SettlementAccountService;
import top.continew.admin.merchant.onboarding.application.SettlementAccountVerificationPort;
import top.continew.admin.merchant.onboarding.application.SettlementAccountView;
import top.continew.admin.merchant.onboarding.outbox.WorkflowOutboxBatchResult;
import top.continew.admin.merchant.onboarding.outbox.WorkflowOutboxPolicy;
import top.continew.admin.merchant.onboarding.outbox.WorkflowOutboxProcessor;
import top.continew.admin.merchant.review.application.OnboardingReviewAction;
import top.continew.admin.merchant.review.application.OnboardingReviewCommand;
import top.continew.admin.merchant.review.application.OnboardingReviewResult;
import top.continew.admin.merchant.review.application.OnboardingReviewService;
import top.continew.admin.merchant.review.application.OnboardingTransferCommand;
import top.continew.admin.merchant.review.application.WorkflowTaskCenterService;
import top.continew.admin.merchant.review.application.WorkflowTaskDetail;
import top.continew.admin.merchant.review.application.WorkflowTaskView;
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
import top.continew.admin.service.workflow.WorkflowNotificationBatchResult;
import top.continew.admin.service.workflow.WorkflowTaskNotificationProcessor;
import top.continew.admin.auth.service.OnlineUserService;
import top.continew.starter.extension.tenant.util.TenantUtils;
import top.continew.admin.workflow.internal.flowable.FlowableEnginePolicyProperties;
import top.continew.admin.workflow.internal.flowable.FlowableJobMonitor;
import top.continew.admin.workflow.internal.flowable.FlowableJobSnapshot;
import top.continew.admin.workflow.api.InvalidWorkflowVariableException;
import top.continew.admin.workflow.api.WorkflowDeploymentService;
import top.continew.admin.workflow.api.WorkflowOperationException;
import top.continew.admin.workflow.api.WorkflowMappingService;
import top.continew.admin.workflow.api.WorkflowService;
import top.continew.admin.workflow.command.ClaimTaskCommand;
import top.continew.admin.workflow.command.CompleteTaskCommand;
import top.continew.admin.workflow.command.DeployWorkflowCommand;
import top.continew.admin.workflow.command.StartWorkflowCommand;
import top.continew.admin.workflow.command.UnclaimTaskCommand;
import top.continew.admin.workflow.definition.MerchantOnboardingReviewWorkflowDefinition;
import top.continew.admin.workflow.dto.WorkflowDefinitionContract;
import top.continew.admin.workflow.dto.WorkflowDeploymentRef;
import top.continew.admin.workflow.dto.WorkflowPage;
import top.continew.admin.workflow.dto.WorkflowInstanceMapping;
import top.continew.admin.workflow.dto.WorkflowNodeContract;
import top.continew.admin.workflow.dto.WorkflowProcessHistory;
import top.continew.admin.workflow.dto.WorkflowRef;
import top.continew.admin.workflow.dto.WorkflowTask;
import top.continew.admin.workflow.query.WorkflowDoneQuery;
import top.continew.admin.workflow.query.WorkflowTaskQuery;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.EnumMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private FlowableEnginePolicyProperties flowableEnginePolicyProperties;

    @Autowired
    private FlowableJobMonitor flowableJobMonitor;

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private WorkflowMappingService workflowMappingService;

    @Autowired
    private WorkflowDeploymentService workflowDeploymentService;

    @Autowired
    private MerchantOnboardingReviewWorkflowDefinition merchantOnboardingReviewWorkflowDefinition;

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
    private ChannelConnectionConfigCatalog channelConnectionConfigCatalog;

    @Autowired
    private ChannelConfigurationLoader channelConfigurationLoader;

    @Autowired
    private ChannelCallbackVerifier channelCallbackVerifier;

    @Autowired
    private ChannelTransportAuditPort channelTransportAuditPort;

    @Autowired
    private OnboardingDraftService onboardingDraftService;

    @Autowired
    private KycReuseService kycReuseService;

    @Autowired
    private OnboardingEvidenceService onboardingEvidenceService;

    @Autowired
    private OnboardingFinalPreviewService onboardingFinalPreviewService;

    @Autowired
    private KycProfileService kycProfileService;

    @Autowired
    private SettlementAccountService settlementAccountService;

    @Autowired
    private OnboardingPricingService onboardingPricingService;

    @Autowired
    private OperatingPlatformService operatingPlatformService;

    @Autowired
    private OnboardingSubmissionService onboardingSubmissionService;

    @Autowired
    private OnboardingSupplementService onboardingSupplementService;

    @Autowired
    private OnboardingReviewService onboardingReviewService;

    @Autowired
    private WorkflowTaskCenterService workflowTaskCenterService;

    @Autowired
    private WorkflowOutboxProcessor workflowOutboxProcessor;

    @Autowired
    private WorkflowOutboxPolicy workflowOutboxPolicy;

    @Autowired
    private WorkflowTaskNotificationProcessor workflowTaskNotificationProcessor;

    @MockBean
    private SettlementAccountVerificationPort settlementAccountVerificationPort;

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

    protected void verifyFlowableEnginePolicy() {
        org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl configuration = (org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl)processEngine
            .getProcessEngineConfiguration();
        org.junit.jupiter.api.Assertions
            .assertEquals(org.flowable.common.engine.impl.history.HistoryLevel.AUDIT, configuration.getHistoryLevel());
        org.junit.jupiter.api.Assertions.assertFalse(configuration.isAsyncHistoryExecutorActivate());
        org.junit.jupiter.api.Assertions.assertTrue(configuration.isAsyncExecutorActivate());
        org.junit.jupiter.api.Assertions.assertTrue(configuration.getAsyncExecutor().isActive());
        org.junit.jupiter.api.Assertions.assertEquals("false", configuration.getDatabaseSchemaUpdate());
        FlowableEnginePolicyProperties.AsyncExecutorProperties async = flowableEnginePolicyProperties
            .getAsyncExecutor();
        org.junit.jupiter.api.Assertions.assertEquals(async.getCorePoolSize(), configuration
            .getAsyncExecutorCorePoolSize());
        org.junit.jupiter.api.Assertions.assertEquals(async.getMaxPoolSize(), configuration
            .getAsyncExecutorMaxPoolSize());
        org.junit.jupiter.api.Assertions.assertEquals(async.getQueueSize(), configuration
            .getAsyncExecutorThreadPoolQueueSize());
        org.junit.jupiter.api.Assertions.assertEquals(async.getRetries(), configuration
            .getAsyncExecutorNumberOfRetries());
        org.junit.jupiter.api.Assertions.assertTrue(processEngine.getManagementService()
            .getTableName(org.flowable.job.api.Job.class)
            .toUpperCase(java.util.Locale.ROOT)
            .startsWith("ACT_"));

        FlowableJobSnapshot snapshot = flowableJobMonitor.snapshot();
        org.junit.jupiter.api.Assertions.assertTrue(snapshot.executableJobs() >= 0);
        org.junit.jupiter.api.Assertions.assertTrue(snapshot.timerJobs() >= 0);
        org.junit.jupiter.api.Assertions.assertTrue(snapshot.suspendedJobs() >= 0);
        org.junit.jupiter.api.Assertions.assertTrue(snapshot.deadLetterJobs() >= 0);
        org.junit.jupiter.api.Assertions.assertTrue(snapshot.historyJobs() >= 0);
        org.springframework.boot.actuate.health.HealthIndicator healthIndicator = applicationContext
            .getBean("flowableJobs", org.springframework.boot.actuate.health.HealthIndicator.class);
        org.junit.jupiter.api.Assertions.assertEquals(org.springframework.boot.actuate.health.Status.UP, healthIndicator
            .health()
            .getStatus());
        io.micrometer.core.instrument.MeterRegistry registry = applicationContext
            .getBean(io.micrometer.core.instrument.MeterRegistry.class);
        org.junit.jupiter.api.Assertions.assertNotNull(registry.find("flowable.jobs.executable").gauge());
        org.junit.jupiter.api.Assertions.assertNotNull(registry.find("flowable.jobs.timer").gauge());
        org.junit.jupiter.api.Assertions.assertNotNull(registry.find("flowable.jobs.suspended").gauge());
        org.junit.jupiter.api.Assertions.assertNotNull(registry.find("flowable.jobs.dead_letter").gauge());
        org.junit.jupiter.api.Assertions.assertNotNull(registry.find("flowable.jobs.history").gauge());
    }

    protected void verifyWorkflowDeploymentVersionPolicy() {
        long tenantId = 938L;
        long actorUserId = 93801L;
        String tenant = String.valueOf(tenantId);
        String processKey = "workflow-deployment-policy-test";
        WorkflowDefinitionContract version1Contract = deploymentPolicyContract(processKey, 1);
        WorkflowDefinitionContract version2Contract = deploymentPolicyContract(processKey, 2);
        try {
            WorkflowDeploymentRef version1 = workflowDeploymentService
                .deploy(new DeployWorkflowCommand(tenantId, actorUserId, "Workflow deployment policy v1", processKey + ".bpmn20.xml", deploymentPolicyBpmn(processKey, "Version 1", "userTask", "reviewTask"), version1Contract));
            org.junit.jupiter.api.Assertions.assertEquals(1, version1.processDefinitionVersion());
            org.junit.jupiter.api.Assertions.assertEquals(1, version1.contractVersion());
            org.junit.jupiter.api.Assertions.assertEquals(64, version1.resourceSha256().length());

            org.flowable.engine.runtime.ProcessInstance inFlightVersion1 = processEngine.getRuntimeService()
                .startProcessInstanceByKeyAndTenantId(processKey, "deployment-policy-v1", Map.of(), tenant);
            org.junit.jupiter.api.Assertions.assertEquals(version1.processDefinitionId(), inFlightVersion1
                .getProcessDefinitionId());

            byte[] version2Resource = deploymentPolicyBpmn(processKey, "Version 2", "userTask", "reviewTask");
            WorkflowDeploymentRef version2 = workflowDeploymentService
                .deploy(new DeployWorkflowCommand(tenantId, actorUserId, "Workflow deployment policy v2", processKey + ".bpmn20.xml", version2Resource, version2Contract));
            org.junit.jupiter.api.Assertions.assertEquals(2, version2.processDefinitionVersion());
            org.junit.jupiter.api.Assertions.assertEquals(2, version2.contractVersion());
            org.junit.jupiter.api.Assertions.assertNotEquals(version1.processDefinitionId(), version2
                .processDefinitionId());

            org.flowable.engine.runtime.ProcessInstance newVersion2 = processEngine.getRuntimeService()
                .startProcessInstanceByKeyAndTenantId(processKey, "deployment-policy-v2", Map.of(), tenant);
            org.junit.jupiter.api.Assertions.assertEquals(version2.processDefinitionId(), newVersion2
                .getProcessDefinitionId());
            org.junit.jupiter.api.Assertions.assertEquals(version1.processDefinitionId(), processEngine
                .getRuntimeService()
                .createProcessInstanceQuery()
                .processInstanceId(inFlightVersion1.getId())
                .singleResult()
                .getProcessDefinitionId());

            WorkflowDeploymentRef duplicate = workflowDeploymentService
                .deploy(new DeployWorkflowCommand(tenantId, actorUserId, "Workflow deployment policy v2 retry", processKey + ".bpmn20.xml", version2Resource, version2Contract));
            org.junit.jupiter.api.Assertions.assertEquals(version2, duplicate);
            assertDeploymentCounts(tenantId, processKey, 2L, 2);

            WorkflowOperationException sameContractDifferentResource = org.junit.jupiter.api.Assertions
                .assertThrows(WorkflowOperationException.class, () -> workflowDeploymentService
                    .deploy(new DeployWorkflowCommand(tenantId, actorUserId, "Workflow deployment policy conflict", processKey + ".bpmn20.xml", deploymentPolicyBpmn(processKey, "Version 2 conflict", "userTask", "reviewTask"), version2Contract)));
            org.junit.jupiter.api.Assertions
                .assertEquals(WorkflowOperationException.Code.DEPLOYMENT_CONFLICT, sameContractDifferentResource
                    .code());
            assertDeploymentCounts(tenantId, processKey, 2L, 2);

            WorkflowOperationException missingStableNode = org.junit.jupiter.api.Assertions
                .assertThrows(WorkflowOperationException.class, () -> workflowDeploymentService
                    .deploy(new DeployWorkflowCommand(tenantId, actorUserId, "Workflow deployment missing node", processKey + ".bpmn20.xml", deploymentPolicyBpmn(processKey, "Missing stable node", "userTask", "replacementTask"), deploymentPolicyContract(processKey, 3))));
            org.junit.jupiter.api.Assertions
                .assertEquals(WorkflowOperationException.Code.DEFINITION_CONTRACT_VIOLATION, missingStableNode.code());
            assertDeploymentCounts(tenantId, processKey, 2L, 2);

            WorkflowOperationException changedStableNodeType = org.junit.jupiter.api.Assertions
                .assertThrows(WorkflowOperationException.class, () -> workflowDeploymentService
                    .deploy(new DeployWorkflowCommand(tenantId, actorUserId, "Workflow deployment changed node type", processKey + ".bpmn20.xml", deploymentPolicyBpmn(processKey, "Changed stable node type", "manualTask", "reviewTask"), deploymentPolicyContract(processKey, 3))));
            org.junit.jupiter.api.Assertions
                .assertEquals(WorkflowOperationException.Code.DEFINITION_CONTRACT_VIOLATION, changedStableNodeType
                    .code());
            assertDeploymentCounts(tenantId, processKey, 2L, 2);

            org.junit.jupiter.api.Assertions.assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
                UPDATE biz_workflow_deployment SET resource_name = ? WHERE tenant_id = ? AND id = ?
                """, "tampered.bpmn20.xml", tenantId, version1.metadataId()));
            org.junit.jupiter.api.Assertions.assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
                DELETE FROM biz_workflow_deployment WHERE tenant_id = ? AND id = ?
                """, tenantId, version1.metadataId()));
            assertDeploymentCounts(tenantId, processKey, 2L, 2);
        } finally {
            Set<String> deploymentIds = processEngine.getRepositoryService()
                .createProcessDefinitionQuery()
                .processDefinitionKey(processKey)
                .processDefinitionTenantId(tenant)
                .list()
                .stream()
                .map(org.flowable.engine.repository.ProcessDefinition::getDeploymentId)
                .collect(java.util.stream.Collectors.toSet());
            deploymentIds.forEach(deploymentId -> processEngine.getRepositoryService()
                .deleteDeployment(deploymentId, true));
        }
    }

    private WorkflowDefinitionContract deploymentPolicyContract(String processKey, int contractVersion) {
        return new WorkflowDefinitionContract(processKey, contractVersion, List
            .of(new WorkflowNodeContract("start", WorkflowNodeContract.NodeType.START_EVENT), new WorkflowNodeContract("reviewTask", WorkflowNodeContract.NodeType.USER_TASK), new WorkflowNodeContract("end", WorkflowNodeContract.NodeType.END_EVENT)));
    }

    private byte[] deploymentPolicyBpmn(String processKey, String processName, String activityType, String activityId) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         targetNamespace="https://continew.top/workflow/test">
              <process id="%s" name="%s" isExecutable="true">
                <startEvent id="start"/>
                <sequenceFlow id="toReview" sourceRef="start" targetRef="%s"/>
                <%s id="%s" name="Review"/>
                <sequenceFlow id="toEnd" sourceRef="%s" targetRef="end"/>
                <endEvent id="end"/>
              </process>
            </definitions>
            """.formatted(processKey, processName, activityId, activityType, activityId, activityId)
            .getBytes(StandardCharsets.UTF_8);
    }

    private void assertDeploymentCounts(long tenantId, String processKey, long definitionCount, int metadataCount) {
        org.junit.jupiter.api.Assertions.assertEquals(definitionCount, processEngine.getRepositoryService()
            .createProcessDefinitionQuery()
            .processDefinitionKey(processKey)
            .processDefinitionTenantId(String.valueOf(tenantId))
            .count());
        org.junit.jupiter.api.Assertions.assertEquals(metadataCount, jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM biz_workflow_deployment
            WHERE tenant_id = ? AND process_definition_key = ?
            """, Integer.class, tenantId, processKey));
    }

    protected void verifyMerchantOnboardingWorkflowDefinitionAndTimer() {
        long tenantId = 939L;
        long actorUserId = 93901L;
        String tenant = String.valueOf(tenantId);
        org.flowable.job.service.impl.asyncexecutor.AsyncExecutor asyncExecutor = ((org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl)processEngine
            .getProcessEngineConfiguration()).getAsyncExecutor();
        boolean restartAsyncExecutor = asyncExecutor.isActive();
        if (restartAsyncExecutor) {
            asyncExecutor.shutdown();
        }
        WorkflowDeploymentRef deployment = workflowDeploymentService.deploy(merchantOnboardingReviewWorkflowDefinition
            .deploymentCommand(tenantId, actorUserId));
        try {
            org.junit.jupiter.api.Assertions
                .assertEquals(MerchantOnboardingReviewWorkflowDefinition.PROCESS_KEY, deployment
                    .processDefinitionKey());
            org.junit.jupiter.api.Assertions
                .assertEquals(MerchantOnboardingReviewWorkflowDefinition.CONTRACT_VERSION, deployment
                    .contractVersion());

            org.flowable.engine.runtime.ProcessInstance aiReviewInstance = processEngine.getRuntimeService()
                .startProcessInstanceByKeyAndTenantId(MerchantOnboardingReviewWorkflowDefinition.PROCESS_KEY, "939:MERCHANT_ONBOARDING:939101:1", onboardingWorkflowVariables(tenantId, 939101L, 939201L, 1L, 939301L, 939401L, "HIGH"), tenant);
            org.junit.jupiter.api.Assertions.assertNotNull(processEngine.getTaskService()
                .createTaskQuery()
                .processInstanceId(aiReviewInstance.getId())
                .taskDefinitionKey("reviewTask")
                .singleResult());
            org.junit.jupiter.api.Assertions.assertTrue(processEngine.getHistoryService()
                .createHistoricActivityInstanceQuery()
                .processInstanceId(aiReviewInstance.getId())
                .activityId("aiReviewTask")
                .finished()
                .count() > 0);
            org.junit.jupiter.api.Assertions.assertNull(processEngine.getRuntimeService()
                .getVariable(aiReviewInstance.getId(), "aiDecision"));

            org.flowable.engine.runtime.ProcessInstance skippedAiInstance = processEngine.getRuntimeService()
                .startProcessInstanceByKeyAndTenantId(MerchantOnboardingReviewWorkflowDefinition.PROCESS_KEY, "939:MERCHANT_ONBOARDING:939102:1", onboardingWorkflowVariables(tenantId, 939102L, 939202L, 1L, 939302L, 939402L, "UNASSESSED"), tenant);
            org.junit.jupiter.api.Assertions.assertEquals(0, processEngine.getHistoryService()
                .createHistoricActivityInstanceQuery()
                .processInstanceId(skippedAiInstance.getId())
                .activityId("aiReviewTask")
                .count());
            org.flowable.job.api.Job reviewTimer = processEngine.getManagementService()
                .createTimerJobQuery()
                .processInstanceId(skippedAiInstance.getId())
                .singleResult();
            org.junit.jupiter.api.Assertions.assertNotNull(reviewTimer);
            long dueHours = java.time.Duration.between(reviewTimer.getCreateTime().toInstant(), reviewTimer.getDuedate()
                .toInstant()).toHours();
            org.junit.jupiter.api.Assertions.assertTrue(dueHours >= 47 && dueHours <= 48);

            org.flowable.job.api.Job executableTimer = processEngine.getManagementService()
                .moveTimerToExecutableJob(reviewTimer.getId());
            processEngine.getManagementService().executeJob(executableTimer.getId());
            org.flowable.task.api.Task escalatedTask = processEngine.getTaskService()
                .createTaskQuery()
                .processInstanceId(skippedAiInstance.getId())
                .singleResult();
            org.junit.jupiter.api.Assertions.assertEquals("escalatedReviewTask", escalatedTask.getTaskDefinitionKey());
            org.junit.jupiter.api.Assertions.assertEquals(1, processEngine.getTaskService()
                .createTaskQuery()
                .taskId(escalatedTask.getId())
                .taskCandidateGroup("RISK_REVIEWER")
                .count());
            org.junit.jupiter.api.Assertions.assertEquals(0, processEngine.getTaskService()
                .createTaskQuery()
                .processInstanceId(skippedAiInstance.getId())
                .taskDefinitionKey("reviewTask")
                .count());
        } finally {
            processEngine.getRepositoryService().deleteDeployment(deployment.deploymentId(), true);
            if (restartAsyncExecutor) {
                asyncExecutor.start();
            }
        }
    }

    private Map<String, Object> onboardingWorkflowVariables(Long tenantId,
                                                            Long merchantId,
                                                            Long applicationId,
                                                            Long kycVersion,
                                                            Long applicantId,
                                                            Long owningAgentId,
                                                            String riskLevel) {
        return Map.ofEntries(Map.entry("tenantId", tenantId), Map.entry("merchantId", merchantId), Map
            .entry("applicationId", applicationId), Map.entry("kycVersion", kycVersion), Map
                .entry("channelCode", "SYNTHETIC"), Map.entry("applicantId", applicantId), Map
                    .entry("owningAgentId", owningAgentId), Map.entry("riskLevel", riskLevel), Map
                        .entry("requiresSupplement", Boolean.FALSE));
    }

    protected void verifyWorkflowAdapterCommandsAndQueries() throws Exception {
        long tenantId = 931L;
        long reviewerUserId = 93111L;
        long siblingUserId = 93112L;
        long operatorUserId = 93113L;
        long rootAgentId = 931001L;
        long merchantAgentId = 931002L;
        long siblingAgentId = 931003L;
        long merchantId = 931101L;
        long applicationId = 93101L;
        long roleId = 931201L;
        String processKey = "workflow-adapter-test-v1";
        String businessKey = "931:MERCHANT_ONBOARDING:93101:1";
        LocalDateTime fixtureTime = LocalDateTime.of(2026, 8, 22, 9, 0);
        TenantUtils.execute(tenantId, () -> {
            agentHierarchyService.register(registration(rootAgentId, tenantId, 0L, 93120L, "WORKFLOW-ROOT"));
            agentHierarchyService
                .register(registration(merchantAgentId, tenantId, rootAgentId, 93121L, "WORKFLOW-MERCHANT"));
            agentHierarchyService
                .register(registration(siblingAgentId, tenantId, rootAgentId, siblingUserId, "WORKFLOW-SIBLING"));
            merchantMasterService
                .register(93120L, merchantRegistration(merchantId, tenantId, merchantAgentId, operatorUserId, reviewerUserId, "WORKFLOW-MERCHANT", "e"
                    .repeat(64)));
            insertQueryUser(tenantId, reviewerUserId, "workflow-reviewer");
            insertQueryUser(tenantId, siblingUserId, "workflow-sibling");
            insertQueryUser(tenantId, operatorUserId, "workflow-operator");
            jdbcTemplate.update("""
                INSERT INTO sys_role
                (id, name, code, data_scope, description, sort, is_system, menu_check_strictly,
                 dept_check_strictly, create_user, create_time, deleted, tenant_id)
                VALUES (?, 'Workflow Reviewer', 'ROLE_REVIEW', 4, NULL, 1, ?, ?, ?, 1, ?, 0, ?)
                """, roleId, false, true, true, fixtureTime, tenantId);
            jdbcTemplate
                .update("INSERT INTO sys_user_role (id, user_id, role_id, tenant_id) VALUES (?, ?, ?, ?)", 931301L, reviewerUserId, roleId, tenantId);
            jdbcTemplate
                .update("INSERT INTO sys_user_role (id, user_id, role_id, tenant_id) VALUES (?, ?, ?, ?)", 931302L, siblingUserId, roleId, tenantId);
            jdbcTemplate.update("""
                INSERT INTO biz_onboarding_application
                (id, tenant_id, application_no, merchant_id, owning_agent_id, channel_code, product_code,
                 requirement_version, channel_config_version, status, row_version, create_time, deleted)
                VALUES (?, ?, 'APP-WORKFLOW-931', ?, ?, 'SYNTHETIC', 'REVIEW', 'REQ-WF-1', 'CFG-WF-1',
                        'SUBMITTED', 0, ?, 0)
                """, applicationId, tenantId, merchantId, merchantAgentId, fixtureTime);
        });
        String resourceName = "workflow-adapter-test-931.bpmn20.xml";
        String bpmn = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xmlns:flowable="http://flowable.org/bpmn"
                         targetNamespace="https://continew.top/workflow/test">
              <process id="workflow-adapter-test-v1" name="Workflow Adapter Test" isExecutable="true">
                <startEvent id="start" />
                <sequenceFlow id="flow-start-review" sourceRef="start" targetRef="reviewTask" />
                <userTask id="reviewTask" name="Synthetic Review" flowable:candidateGroups="ROLE_REVIEW" />
                <sequenceFlow id="flow-review-end" sourceRef="reviewTask" targetRef="end" />
                <endEvent id="end" />
              </process>
            </definitions>
            """;
        org.flowable.engine.repository.Deployment deployment = processEngine.getRepositoryService()
            .createDeployment()
            .tenantId(String.valueOf(tenantId))
            .name("workflow-adapter-test-931")
            .addString(resourceName, bpmn)
            .deploy();
        try {
            Map<String, Object> variables = Map
                .of("tenantId", tenantId, "merchantId", merchantId, "applicationId", applicationId, "kycVersion", 1L, "channelCode", "SYNTHETIC", "applicantId", reviewerUserId, "owningAgentId", merchantAgentId, "riskLevel", "LOW", "requiresSupplement", Boolean.FALSE);
            StartWorkflowCommand startCommand = new StartWorkflowCommand(tenantId, processKey, businessKey, variables);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            List<WorkflowRef> concurrentStarts;
            try {
                List<Future<WorkflowRef>> futures = List.of(executor
                    .submit(() -> startWorkflowConcurrently(ready, start, startCommand)), executor
                        .submit(() -> startWorkflowConcurrently(ready, start, startCommand)));
                org.junit.jupiter.api.Assertions.assertTrue(ready.await(10, TimeUnit.SECONDS));
                start.countDown();
                concurrentStarts = futures.stream().map(future -> {
                    try {
                        return future.get(30, TimeUnit.SECONDS);
                    } catch (Exception ex) {
                        throw new AssertionError(ex);
                    }
                }).toList();
            } finally {
                executor.shutdownNow();
            }
            WorkflowRef started = concurrentStarts.get(0);
            org.junit.jupiter.api.Assertions.assertEquals(started.processInstanceId(), concurrentStarts.get(1)
                .processInstanceId());
            org.junit.jupiter.api.Assertions.assertEquals(started.mappingId(), concurrentStarts.get(1).mappingId());
            org.junit.jupiter.api.Assertions.assertEquals(processKey, started.processDefinitionKey());
            org.junit.jupiter.api.Assertions.assertEquals(1, started.processDefinitionVersion());
            org.junit.jupiter.api.Assertions.assertEquals(String.valueOf(tenantId), started.tenantId());
            WorkflowInstanceMapping mapping = workflowMappingService.findByBusinessKey(tenantId, businessKey)
                .orElseThrow();
            org.junit.jupiter.api.Assertions.assertEquals(started.mappingId(), mapping.mappingId());
            org.junit.jupiter.api.Assertions.assertEquals(started.processInstanceId(), mapping.processInstanceId());
            org.junit.jupiter.api.Assertions.assertEquals(mapping, workflowMappingService
                .findByProcessInstanceId(tenantId, started.processInstanceId())
                .orElseThrow());
            org.junit.jupiter.api.Assertions.assertEquals(1, jdbcTemplate
                .queryForObject("SELECT COUNT(*) FROM biz_workflow_instance WHERE tenant_id = ? AND business_key = ?", Integer.class, tenantId, businessKey));
            org.junit.jupiter.api.Assertions.assertEquals(1, processEngine.getRuntimeService()
                .createProcessInstanceQuery()
                .processInstanceBusinessKey(businessKey)
                .count());

            WorkflowPage<WorkflowTask> todo = workflowService
                .pageTodo(new WorkflowTaskQuery(tenantId, reviewerUserId, processKey, businessKey, "Review", false, 1, 20));
            org.junit.jupiter.api.Assertions.assertEquals(1, todo.total());
            WorkflowTask task = todo.items().get(0);
            org.junit.jupiter.api.Assertions.assertEquals(WorkflowTask.State.TODO, task.state());
            org.junit.jupiter.api.Assertions.assertEquals("reviewTask", task.taskDefinitionKey());
            org.junit.jupiter.api.Assertions.assertTrue(workflowService
                .pageTodo(new WorkflowTaskQuery(tenantId, siblingUserId, processKey, businessKey, null, false, 1, 20))
                .items()
                .isEmpty());
            org.junit.jupiter.api.Assertions.assertTrue(workflowService
                .pageTodo(new WorkflowTaskQuery(tenantId, operatorUserId, processKey, businessKey, null, false, 1, 20))
                .items()
                .isEmpty());
            WorkflowOperationException wrongTenant = org.junit.jupiter.api.Assertions
                .assertThrows(WorkflowOperationException.class, () -> workflowService
                    .pageTodo(new WorkflowTaskQuery(tenantId + 1, reviewerUserId, processKey, businessKey, null, false, 1, 20)));
            org.junit.jupiter.api.Assertions.assertEquals(WorkflowOperationException.Code.NOT_FOUND, wrongTenant
                .code());
            WorkflowOperationException siblingClaim = org.junit.jupiter.api.Assertions
                .assertThrows(WorkflowOperationException.class, () -> workflowService
                    .claim(new ClaimTaskCommand(tenantId, task.taskId(), siblingUserId)));
            org.junit.jupiter.api.Assertions.assertEquals(WorkflowOperationException.Code.NOT_FOUND, siblingClaim
                .code());

            workflowService.claim(new ClaimTaskCommand(tenantId, task.taskId(), reviewerUserId));
            WorkflowPage<WorkflowTask> claimed = workflowService
                .pageTodo(new WorkflowTaskQuery(tenantId, reviewerUserId, processKey, businessKey, null, true, 1, 20));
            org.junit.jupiter.api.Assertions.assertEquals(1, claimed.total());
            org.junit.jupiter.api.Assertions.assertEquals(String.valueOf(reviewerUserId), claimed.items()
                .get(0)
                .assignee());
            WorkflowOperationException wrongUnclaim = org.junit.jupiter.api.Assertions
                .assertThrows(WorkflowOperationException.class, () -> workflowService
                    .unclaim(new UnclaimTaskCommand(tenantId, task.taskId(), operatorUserId)));
            org.junit.jupiter.api.Assertions.assertEquals(WorkflowOperationException.Code.NOT_ASSIGNED, wrongUnclaim
                .code());
            workflowService.unclaim(new UnclaimTaskCommand(tenantId, task.taskId(), reviewerUserId));
            workflowService.claim(new ClaimTaskCommand(tenantId, task.taskId(), reviewerUserId));
            List<RuntimeException> completionFailures = runConcurrentOperations(() -> workflowService
                .complete(new CompleteTaskCommand(tenantId, task.taskId(), reviewerUserId, Map
                    .of("requiresSupplement", Boolean.FALSE))));
            org.junit.jupiter.api.Assertions.assertEquals(1, completionFailures.stream()
                .filter(java.util.Objects::isNull)
                .count());
            RuntimeException completionFailure = completionFailures.stream()
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElseThrow();
            org.junit.jupiter.api.Assertions.assertInstanceOf(WorkflowOperationException.class, completionFailure);

            WorkflowPage<WorkflowTask> done = workflowService
                .pageDone(new WorkflowDoneQuery(tenantId, reviewerUserId, processKey, businessKey, "Review", 1, 20));
            org.junit.jupiter.api.Assertions.assertEquals(1, done.total());
            org.junit.jupiter.api.Assertions.assertEquals(WorkflowTask.State.DONE, done.items().get(0).state());
            WorkflowProcessHistory history = workflowService.history(tenantId, reviewerUserId, started
                .processInstanceId());
            org.junit.jupiter.api.Assertions.assertTrue(history.ended());
            org.junit.jupiter.api.Assertions.assertEquals(processKey, history.processDefinitionKey());
            org.junit.jupiter.api.Assertions.assertTrue(history.activities()
                .stream()
                .anyMatch(activity -> "reviewTask".equals(activity.activityId())));
            org.junit.jupiter.api.Assertions.assertEquals(List.of("reviewTask"), history.tasks()
                .stream()
                .map(WorkflowTask::taskDefinitionKey)
                .toList());
            org.junit.jupiter.api.Assertions.assertNull(processEngine.getRuntimeService()
                .createProcessInstanceQuery()
                .processInstanceId(started.processInstanceId())
                .singleResult());
            Set<String> historicVariableNames = processEngine.getHistoryService()
                .createHistoricVariableInstanceQuery()
                .processInstanceId(started.processInstanceId())
                .list()
                .stream()
                .map(org.flowable.variable.api.history.HistoricVariableInstance::getVariableName)
                .collect(java.util.stream.Collectors.toSet());
            org.junit.jupiter.api.Assertions.assertTrue(Set
                .of("tenantId", "merchantId", "applicationId", "kycVersion", "channelCode", "applicantId", "owningAgentId", "riskLevel", "requiresSupplement")
                .containsAll(historicVariableNames));

            jdbcTemplate
                .update("UPDATE sys_user SET status = 2 WHERE tenant_id = ? AND id = ?", tenantId, reviewerUserId);
            WorkflowOperationException disabledUser = org.junit.jupiter.api.Assertions
                .assertThrows(WorkflowOperationException.class, () -> workflowService
                    .history(tenantId, reviewerUserId, started.processInstanceId()));
            org.junit.jupiter.api.Assertions.assertEquals(WorkflowOperationException.Code.NOT_FOUND, disabledUser
                .code());
            jdbcTemplate
                .update("UPDATE sys_user SET status = 1 WHERE tenant_id = ? AND id = ?", tenantId, reviewerUserId);

            long historicCount = processEngine.getHistoryService()
                .createHistoricProcessInstanceQuery()
                .processDefinitionKey(processKey)
                .count();
            org.junit.jupiter.api.Assertions.assertThrows(InvalidWorkflowVariableException.class, () -> workflowService
                .start(new StartWorkflowCommand(tenantId, processKey, "931:WORKFLOW_ADAPTER:93101:2", Map
                    .of("identityNumber", "110101199001011234"))));
            org.junit.jupiter.api.Assertions.assertEquals(historicCount, processEngine.getHistoryService()
                .createHistoricProcessInstanceQuery()
                .processDefinitionKey(processKey)
                .count());
        } finally {
            processEngine.getRepositoryService().deleteDeployment(deployment.getId(), true);
        }
    }

    private WorkflowRef startWorkflowConcurrently(CountDownLatch ready,
                                                  CountDownLatch start,
                                                  StartWorkflowCommand command) throws InterruptedException {
        ready.countDown();
        start.await();
        return workflowService.start(command);
    }

    private List<RuntimeException> runConcurrentOperations(Runnable operation) {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<RuntimeException>> futures = List.of(executor
                .submit(() -> runConcurrentOperation(ready, start, operation)), executor
                    .submit(() -> runConcurrentOperation(ready, start, operation)));
            org.junit.jupiter.api.Assertions.assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            return futures.stream().map(future -> {
                try {
                    return future.get(30, TimeUnit.SECONDS);
                } catch (Exception ex) {
                    throw new AssertionError(ex);
                }
            }).toList();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError(ex);
        } finally {
            executor.shutdownNow();
        }
    }

    private RuntimeException runConcurrentOperation(CountDownLatch ready, CountDownLatch start, Runnable operation) {
        ready.countDown();
        try {
            start.await();
            operation.run();
            return null;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new IllegalStateException("Concurrent operation was interrupted", ex);
        } catch (RuntimeException ex) {
            return ex;
        }
    }

    protected void verifyOnboardingReviewActionsAndImmutableRecords() {
        long tenantId = 932L;
        long riskUserId = 93211L;
        long reviewerUserId = 93212L;
        long applicantUserId = 93213L;
        long rootAgentId = 932001L;
        long merchantAgentId = 932002L;
        long merchantId = 932101L;
        long riskRoleId = 932201L;
        long reviewerRoleId = 932202L;
        long applicationApproveId = 932301L;
        long applicationRejectId = 932302L;
        long applicationSelfReviewId = 932303L;
        long applicationConcurrentId = 932304L;
        long approveSourceKycVersionId = 1932301L;
        String processKey = MerchantOnboardingReviewWorkflowDefinition.PROCESS_KEY;
        LocalDateTime fixtureTime = LocalDateTime.of(2026, 8, 22, 10, 0);

        TenantUtils.execute(tenantId, () -> {
            agentHierarchyService.register(registration(rootAgentId, tenantId, 0L, riskUserId, "REVIEW-ROOT"));
            agentHierarchyService
                .register(registration(merchantAgentId, tenantId, rootAgentId, 93221L, "REVIEW-MERCHANT"));
            merchantMasterService
                .register(riskUserId, merchantRegistration(merchantId, tenantId, merchantAgentId, applicantUserId, reviewerUserId, "REVIEW-MERCHANT", "f"
                    .repeat(64)));
            insertQueryUser(tenantId, riskUserId, "risk-reviewer");
            insertQueryUser(tenantId, reviewerUserId, "merchant-reviewer");
            insertQueryUser(tenantId, applicantUserId, "review-applicant");
            jdbcTemplate.update("""
                INSERT INTO sys_role
                (id, name, code, data_scope, description, sort, is_system, menu_check_strictly,
                 dept_check_strictly, create_user, create_time, deleted, tenant_id)
                VALUES (?, 'Risk Reviewer', 'RISK_REVIEWER', 4, NULL, 1, ?, ?, ?, 1, ?, 0, ?)
                """, riskRoleId, false, true, true, fixtureTime, tenantId);
            jdbcTemplate.update("""
                INSERT INTO sys_role
                (id, name, code, data_scope, description, sort, is_system, menu_check_strictly,
                 dept_check_strictly, create_user, create_time, deleted, tenant_id)
                VALUES (?, 'Merchant Reviewer', 'MERCHANT_REVIEWER', 4, NULL, 2, ?, ?, ?, 1, ?, 0, ?)
                """, reviewerRoleId, false, true, true, fixtureTime, tenantId);
            jdbcTemplate
                .update("INSERT INTO sys_user_role (id, user_id, role_id, tenant_id) VALUES (?, ?, ?, ?)", 932401L, riskUserId, riskRoleId, tenantId);
            jdbcTemplate
                .update("INSERT INTO sys_user_role (id, user_id, role_id, tenant_id) VALUES (?, ?, ?, ?)", 932402L, reviewerUserId, reviewerRoleId, tenantId);
            jdbcTemplate
                .update("INSERT INTO sys_user_role (id, user_id, role_id, tenant_id) VALUES (?, ?, ?, ?)", 932403L, applicantUserId, reviewerRoleId, tenantId);
            insertReviewApplication(tenantId, applicationApproveId, merchantId, merchantAgentId, applicantUserId, approveSourceKycVersionId, 1, fixtureTime);
            insertReviewApplication(tenantId, applicationRejectId, merchantId, merchantAgentId, applicantUserId, 1932302L, 2, fixtureTime);
            insertReviewApplication(tenantId, applicationSelfReviewId, merchantId, merchantAgentId, applicantUserId, 1932303L, 3, fixtureTime);
            insertReviewApplication(tenantId, applicationConcurrentId, merchantId, merchantAgentId, applicantUserId, 1932304L, 4, fixtureTime);
            kycAttachmentRepository
                .insert(new KycAttachmentDraft(tenantId, approveSourceKycVersionId, "BUSINESS_LICENSE", "private|review/source-license", "source-license.png", "png", "image/png", "image/png", 10L, "8"
                    .repeat(64), KycAttachmentScanStatus.CLEAN, KycAttachmentValidationStatus.VALID, 1, fixtureTime));
        });

        WorkflowDeploymentRef deployment = workflowDeploymentService.deploy(merchantOnboardingReviewWorkflowDefinition
            .deploymentCommand(tenantId, riskUserId));
        try {
            TenantUtils.execute(tenantId, () -> {
                WorkflowRef approveFlow = startReviewWorkflow(tenantId, processKey, applicationApproveId, 1L, merchantId, merchantAgentId, applicantUserId);
                WorkflowTask initialReviewTask = workflowService
                    .pageTodo(new WorkflowTaskQuery(tenantId, riskUserId, processKey, approveFlow
                        .businessKey(), null, false, 1, 20))
                    .items()
                    .get(0);
                processEngine.getTaskService()
                    .setDueDate(initialReviewTask.taskId(), java.util.Date.from(java.time.Instant.now()
                        .minusSeconds(60)));
                WorkflowNotificationBatchResult firstNotifications = workflowTaskNotificationProcessor
                    .process(200, 200);
                org.junit.jupiter.api.Assertions.assertEquals(2, firstNotifications.enqueued());
                org.junit.jupiter.api.Assertions.assertEquals(2, firstNotifications.sent());
                WorkflowNotificationBatchResult duplicateNotifications = workflowTaskNotificationProcessor
                    .process(200, 200);
                org.junit.jupiter.api.Assertions.assertEquals(0, duplicateNotifications.enqueued());
                org.junit.jupiter.api.Assertions.assertEquals(0, duplicateNotifications.sent());
                WorkflowPage<WorkflowTaskView> taskCenterTodo = workflowTaskCenterService
                    .pageTodo(new WorkflowTaskQuery(tenantId, riskUserId, processKey, approveFlow
                        .businessKey(), null, false, 1, 20));
                org.junit.jupiter.api.Assertions.assertEquals(1, taskCenterTodo.total());
                org.junit.jupiter.api.Assertions.assertEquals("APP-REVIEW-" + applicationApproveId, taskCenterTodo
                    .items()
                    .get(0)
                    .business()
                    .applicationNo());
                org.junit.jupiter.api.Assertions.assertEquals("913***********0Y92", taskCenterTodo.items()
                    .get(0)
                    .business()
                    .legalIdentifierMasked());
                workflowService.claim(new ClaimTaskCommand(tenantId, initialReviewTask.taskId(), riskUserId));
                OnboardingReviewResult transferred = onboardingReviewService
                    .transfer(new OnboardingTransferCommand(tenantId, riskUserId, initialReviewTask
                        .taskId(), reviewerUserId, 1L, "Transfer to merchant reviewer", "127.0.0.1"));
                org.junit.jupiter.api.Assertions.assertEquals(reviewerUserId, transferred.targetUserId());
                OnboardingReviewResult supplement = onboardingReviewService
                    .review(new OnboardingReviewCommand(tenantId, reviewerUserId, initialReviewTask
                        .taskId(), 1L, OnboardingReviewAction.REQUEST_SUPPLEMENT, "Storefront evidence is missing", List
                            .of("STORE_QR_MISSING"), "127.0.0.1"));
                org.junit.jupiter.api.Assertions.assertEquals("SUPPLEMENT_REQUIRED", supplement.applicationStatus());
                WorkflowTask supplementTask = workflowService
                    .pageTodo(new WorkflowTaskQuery(tenantId, applicantUserId, processKey, approveFlow
                        .businessKey(), null, true, 1, 20))
                    .items()
                    .get(0);
                OnboardingSupplementDraft supplementDraft = onboardingSupplementService
                    .create(tenantId, applicantUserId, merchantId, applicationApproveId, supplementTask
                        .taskId(), approveSourceKycVersionId, "127.0.0.1");
                Long supplementKycVersionId = supplementDraft.draft().draft().kycVersionId();
                org.junit.jupiter.api.Assertions.assertEquals(approveSourceKycVersionId, supplementDraft
                    .previousKycVersionId());
                org.junit.jupiter.api.Assertions.assertTrue(supplementDraft.diff().changedFields().isEmpty());
                org.junit.jupiter.api.Assertions.assertEquals(supplementKycVersionId, onboardingSupplementService
                    .create(tenantId, applicantUserId, merchantId, applicationApproveId, supplementTask
                        .taskId(), approveSourceKycVersionId, "127.0.0.1")
                    .draft()
                    .draft()
                    .kycVersionId());
                org.junit.jupiter.api.Assertions.assertEquals(1, jdbcTemplate
                    .queryForObject("SELECT COUNT(*) FROM biz_kyc_attachment WHERE tenant_id = ? AND kyc_version_id = ? AND deleted = 0", Integer.class, tenantId, supplementKycVersionId));
                org.junit.jupiter.api.Assertions.assertEquals("SUBMITTED", jdbcTemplate
                    .queryForObject("SELECT status FROM biz_kyc_version WHERE tenant_id = ? AND id = ?", String.class, tenantId, approveSourceKycVersionId));
                jdbcTemplate
                    .update("UPDATE biz_kyc_version SET legal_name = 'Supplemented Legal Name' WHERE tenant_id = ? AND id = ?", tenantId, supplementKycVersionId);
                org.junit.jupiter.api.Assertions.assertTrue(onboardingSupplementService
                    .diff(tenantId, applicantUserId, merchantId, applicationApproveId, supplementKycVersionId)
                    .changedFields()
                    .contains("LEGAL_NAME"));
                WorkflowTaskDetail supplementDetail = workflowTaskCenterService
                    .detail(tenantId, applicantUserId, supplementTask.taskId());
                org.junit.jupiter.api.Assertions.assertNotNull(supplementDetail.supplementDiff());
                org.junit.jupiter.api.Assertions.assertTrue(supplementDetail.supplementDiff()
                    .changedFields()
                    .contains("LEGAL_NAME"));
                onboardingReviewService.review(new OnboardingReviewCommand(tenantId, applicantUserId, supplementTask
                    .taskId(), 1L, OnboardingReviewAction.RESUBMIT, "Supplemented requested evidence", List
                        .of(), "127.0.0.1"));
                org.junit.jupiter.api.Assertions.assertEquals("SUBMITTED", jdbcTemplate
                    .queryForObject("SELECT status FROM biz_kyc_version WHERE tenant_id = ? AND id = ?", String.class, tenantId, supplementKycVersionId));
                WorkflowTask returnedReviewTask = workflowService
                    .pageTodo(new WorkflowTaskQuery(tenantId, reviewerUserId, processKey, approveFlow
                        .businessKey(), null, false, 1, 20))
                    .items()
                    .get(0);
                workflowService.claim(new ClaimTaskCommand(tenantId, returnedReviewTask.taskId(), reviewerUserId));
                OnboardingReviewResult approved = onboardingReviewService
                    .review(new OnboardingReviewCommand(tenantId, reviewerUserId, returnedReviewTask
                        .taskId(), 1L, OnboardingReviewAction.APPROVE, "Review passed", List.of(), "127.0.0.1"));
                org.junit.jupiter.api.Assertions.assertEquals("APPROVED", approved.applicationStatus());
                WorkflowTaskDetail completedDetail = workflowTaskCenterService
                    .detail(tenantId, reviewerUserId, returnedReviewTask.taskId());
                org.junit.jupiter.api.Assertions.assertEquals(WorkflowTask.State.DONE, completedDetail.task().state());
                org.junit.jupiter.api.Assertions.assertEquals(4, completedDetail.reviews().size());
                Set<String> approvedActivities = processEngine.getHistoryService()
                    .createHistoricActivityInstanceQuery()
                    .processInstanceId(approveFlow.processInstanceId())
                    .list()
                    .stream()
                    .map(org.flowable.engine.history.HistoricActivityInstance::getActivityId)
                    .collect(java.util.stream.Collectors.toSet());
                org.junit.jupiter.api.Assertions.assertTrue(approvedActivities.containsAll(Set
                    .of("aiReviewTask", "reviewTask", "supplementTask", "approvedEnd")));

                WorkflowRef rejectFlow = startReviewWorkflow(tenantId, processKey, applicationRejectId, 2L, merchantId, merchantAgentId, applicantUserId);
                WorkflowTask rejectTask = workflowService
                    .pageTodo(new WorkflowTaskQuery(tenantId, reviewerUserId, processKey, rejectFlow
                        .businessKey(), null, false, 1, 20))
                    .items()
                    .get(0);
                workflowService.claim(new ClaimTaskCommand(tenantId, rejectTask.taskId(), reviewerUserId));
                org.junit.jupiter.api.Assertions
                    .assertThrows(MerchantDomainException.class, () -> onboardingReviewService
                        .review(new OnboardingReviewCommand(tenantId, reviewerUserId, rejectTask
                            .taskId(), 2L, OnboardingReviewAction.REJECT, null, List.of(), "127.0.0.1")));
                OnboardingReviewResult rejected = onboardingReviewService
                    .review(new OnboardingReviewCommand(tenantId, reviewerUserId, rejectTask
                        .taskId(), 2L, OnboardingReviewAction.REJECT, "Business evidence is inconsistent", List
                            .of(), "127.0.0.1"));
                org.junit.jupiter.api.Assertions.assertEquals("REJECTED", rejected.applicationStatus());
                org.junit.jupiter.api.Assertions.assertTrue(processEngine.getHistoryService()
                    .createHistoricActivityInstanceQuery()
                    .processInstanceId(rejectFlow.processInstanceId())
                    .activityId("rejectedEnd")
                    .finished()
                    .count() > 0);

                WorkflowRef selfReviewFlow = startReviewWorkflow(tenantId, processKey, applicationSelfReviewId, 3L, merchantId, merchantAgentId, applicantUserId);
                WorkflowTask selfReviewTask = workflowService
                    .pageTodo(new WorkflowTaskQuery(tenantId, applicantUserId, processKey, selfReviewFlow
                        .businessKey(), null, false, 1, 20))
                    .items()
                    .get(0);
                workflowService.claim(new ClaimTaskCommand(tenantId, selfReviewTask.taskId(), applicantUserId));
                org.junit.jupiter.api.Assertions
                    .assertThrows(MerchantDomainException.class, () -> onboardingReviewService
                        .review(new OnboardingReviewCommand(tenantId, applicantUserId, selfReviewTask
                            .taskId(), 3L, OnboardingReviewAction.APPROVE, "Self review", List.of(), "127.0.0.1")));

                WorkflowRef concurrentFlow = startReviewWorkflow(tenantId, processKey, applicationConcurrentId, 4L, merchantId, merchantAgentId, applicantUserId);
                WorkflowTask concurrentTask = workflowService
                    .pageTodo(new WorkflowTaskQuery(tenantId, reviewerUserId, processKey, concurrentFlow
                        .businessKey(), null, false, 1, 20))
                    .items()
                    .get(0);
                workflowService.claim(new ClaimTaskCommand(tenantId, concurrentTask.taskId(), reviewerUserId));
                OnboardingReviewCommand concurrentCommand = new OnboardingReviewCommand(tenantId, reviewerUserId, concurrentTask
                    .taskId(), 4L, OnboardingReviewAction.APPROVE, "Concurrent approval", List.of(), "127.0.0.1");
                List<RuntimeException> reviewFailures = runConcurrentOperations(() -> onboardingReviewService
                    .review(concurrentCommand));
                org.junit.jupiter.api.Assertions.assertEquals(1, reviewFailures.stream()
                    .filter(java.util.Objects::isNull)
                    .count());
                RuntimeException reviewFailure = reviewFailures.stream()
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElseThrow();
                org.junit.jupiter.api.Assertions
                    .assertTrue(reviewFailure instanceof MerchantDomainException || reviewFailure instanceof WorkflowOperationException);

                org.junit.jupiter.api.Assertions
                    .assertEquals("APPROVED", reviewApplicationStatus(tenantId, applicationApproveId));
                org.junit.jupiter.api.Assertions
                    .assertEquals("REJECTED", reviewApplicationStatus(tenantId, applicationRejectId));
                org.junit.jupiter.api.Assertions
                    .assertEquals("SUBMITTED", reviewApplicationStatus(tenantId, applicationSelfReviewId));
                org.junit.jupiter.api.Assertions
                    .assertEquals("APPROVED", reviewApplicationStatus(tenantId, applicationConcurrentId));
                org.junit.jupiter.api.Assertions.assertEquals(4, jdbcTemplate
                    .queryForObject("SELECT COUNT(*) FROM biz_review_record WHERE tenant_id = ? AND business_id = ?", Integer.class, tenantId, applicationApproveId));
                org.junit.jupiter.api.Assertions.assertEquals(1, jdbcTemplate
                    .queryForObject("SELECT COUNT(*) FROM biz_review_record WHERE tenant_id = ? AND business_id = ?", Integer.class, tenantId, applicationRejectId));
                org.junit.jupiter.api.Assertions.assertEquals(0, jdbcTemplate
                    .queryForObject("SELECT COUNT(*) FROM biz_review_record WHERE tenant_id = ? AND business_id = ?", Integer.class, tenantId, applicationSelfReviewId));
                org.junit.jupiter.api.Assertions.assertEquals(1, jdbcTemplate
                    .queryForObject("SELECT COUNT(*) FROM biz_review_record WHERE tenant_id = ? AND business_id = ?", Integer.class, tenantId, applicationConcurrentId));
                Long reviewRecordId = jdbcTemplate
                    .queryForObject("SELECT MIN(id) FROM biz_review_record WHERE tenant_id = ? AND business_id = ?", Long.class, tenantId, applicationApproveId);
                org.junit.jupiter.api.Assertions.assertThrows(DataAccessException.class, () -> jdbcTemplate
                    .update("UPDATE biz_review_record SET opinion = 'tampered' WHERE id = ?", reviewRecordId));
                org.junit.jupiter.api.Assertions.assertEquals(6, jdbcTemplate
                    .queryForObject("SELECT COUNT(*) FROM biz_security_audit WHERE tenant_id = ? AND action LIKE 'WORKFLOW_REVIEW_%'", Integer.class, tenantId));
                WorkflowNotificationBatchResult finalNotifications = workflowTaskNotificationProcessor
                    .process(200, 200);
                org.junit.jupiter.api.Assertions.assertTrue(finalNotifications.sent() >= 1);
                int notificationCount = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM biz_workflow_notification WHERE tenant_id = ?
                    """, Integer.class, tenantId);
                org.junit.jupiter.api.Assertions.assertEquals(notificationCount, jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM sys_message WHERE tenant_id = ? AND deleted = 0
                    """, Integer.class, tenantId));
                org.junit.jupiter.api.Assertions.assertEquals(notificationCount, jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM biz_workflow_notification
                    WHERE tenant_id = ? AND status = 'SENT' AND path LIKE '/merchant/workflow?%'
                    """, Integer.class, tenantId));
                org.junit.jupiter.api.Assertions.assertTrue(jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM biz_workflow_notification
                    WHERE tenant_id = ? AND event_type = 'TASK_OVERDUE'
                    """, Integer.class, tenantId) >= 2);
                org.junit.jupiter.api.Assertions.assertTrue(jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM biz_workflow_notification
                    WHERE tenant_id = ? AND event_type IN ('TASK_TRANSFERRED', 'REVIEW_RESULT')
                    """, Integer.class, tenantId) >= 2);
                WorkflowNotificationBatchResult finalDuplicate = workflowTaskNotificationProcessor.process(200, 200);
                org.junit.jupiter.api.Assertions.assertEquals(0, finalDuplicate.sent());
                org.junit.jupiter.api.Assertions.assertEquals(notificationCount, jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM biz_workflow_notification WHERE tenant_id = ?
                    """, Integer.class, tenantId));
            });
        } finally {
            processEngine.getRepositoryService().deleteDeployment(deployment.deploymentId(), true);
        }
    }

    protected void verifyWorkflowOutboxDeliveryRetryAndRepair() {
        long tenantId = 934L;
        long rootUserId = 93411L;
        long merchantAgentUserId = 93412L;
        long applicantUserId = 93413L;
        long reviewerUserId = 93414L;
        long rootAgentId = 1934001L;
        long merchantAgentId = 1934002L;
        long merchantId = 934101L;
        long successfulApplicationId = 934201L;
        long retryApplicationId = 934202L;
        long successfulEventId = 934301L;
        long retryEventId = 934302L;
        String processKey = MerchantOnboardingReviewWorkflowDefinition.PROCESS_KEY;
        LocalDateTime fixtureTime = LocalDateTime.of(2026, 8, 23, 11, 0);
        TenantUtils.execute(tenantId, () -> {
            agentHierarchyService.register(registration(rootAgentId, tenantId, 0L, rootUserId, "OUTBOX-ROOT"));
            agentHierarchyService
                .register(registration(merchantAgentId, tenantId, rootAgentId, merchantAgentUserId, "OUTBOX-MERCHANT"));
            merchantMasterService
                .register(rootUserId, merchantRegistration(merchantId, tenantId, merchantAgentId, applicantUserId, reviewerUserId, "OUTBOX-MERCHANT", "4"
                    .repeat(64)));
            insertQueryUser(tenantId, applicantUserId, "outbox-applicant");
            insertQueryUser(tenantId, reviewerUserId, "outbox-reviewer");
            insertReviewApplication(tenantId, successfulApplicationId, merchantId, merchantAgentId, applicantUserId, 1934301L, 1, fixtureTime);
            insertReviewApplication(tenantId, retryApplicationId, merchantId, merchantAgentId, applicantUserId, 1934302L, 2, fixtureTime);
        });

        WorkflowDeploymentRef deployment = workflowDeploymentService.deploy(merchantOnboardingReviewWorkflowDefinition
            .deploymentCommand(tenantId, rootUserId));
        int originalMaxRetries = workflowOutboxPolicy.getMaxRetries();
        try {
            workflowOutboxPolicy.setMaxRetries(3);
            insertWorkflowOutboxEvent(successfulEventId, tenantId, successfulApplicationId, 1L, processKey, applicantUserId, merchantId, merchantAgentId, "OUTBOX-SUCCESS-934", fixtureTime);
            insertWorkflowOutboxEvent(retryEventId, tenantId, retryApplicationId, 1L, "missing-onboarding-process-v1", applicantUserId, merchantId, merchantAgentId, "OUTBOX-RETRY-934", fixtureTime);
            org.junit.jupiter.api.Assertions.assertTrue(workflowOutboxPolicy.isEnabled());
            org.junit.jupiter.api.Assertions.assertEquals(2, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM biz_outbox_event WHERE tenant_id = ? AND status = 'PENDING'
                """, Integer.class, tenantId));

            WorkflowOutboxBatchResult first = workflowOutboxProcessor.processTenant(tenantId);
            org.junit.jupiter.api.Assertions.assertEquals(new WorkflowOutboxBatchResult(2, 1, 1, 0), first);
            org.junit.jupiter.api.Assertions.assertEquals("PUBLISHED", outboxStatus(successfulEventId));
            org.junit.jupiter.api.Assertions.assertEquals("RETRY", outboxStatus(retryEventId));
            org.junit.jupiter.api.Assertions.assertEquals(1, outboxRetryCount(retryEventId));
            org.junit.jupiter.api.Assertions.assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM biz_workflow_instance WHERE tenant_id = ? AND business_id = ?
                """, Integer.class, tenantId, successfulApplicationId));
            String headers = jdbcTemplate
                .queryForObject("SELECT headers_json FROM biz_outbox_event WHERE id = ?", String.class, successfulEventId);
            org.junit.jupiter.api.Assertions.assertTrue(headers.contains("processInstanceId"));

            jdbcTemplate.update("""
                UPDATE biz_outbox_event
                SET status = 'RETRY', next_retry_time = ?, published_time = NULL, locked_by = NULL, locked_time = NULL
                WHERE id = ?
                """, LocalDateTime.now().minusSeconds(1), successfulEventId);
            jdbcTemplate.update("UPDATE biz_outbox_event SET next_retry_time = ? WHERE id = ?", LocalDateTime.now()
                .plusHours(1), retryEventId);
            WorkflowOutboxBatchResult replay = workflowOutboxProcessor.processTenant(tenantId);
            org.junit.jupiter.api.Assertions.assertEquals(new WorkflowOutboxBatchResult(1, 1, 0, 0), replay);
            org.junit.jupiter.api.Assertions.assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM biz_workflow_instance WHERE tenant_id = ? AND business_id = ?
                """, Integer.class, tenantId, successfulApplicationId));

            for (int retry = 2; retry <= 3; retry++) {
                jdbcTemplate.update("UPDATE biz_outbox_event SET next_retry_time = ? WHERE id = ?", LocalDateTime.now()
                    .minusSeconds(1), retryEventId);
                WorkflowOutboxBatchResult failed = workflowOutboxProcessor.processTenant(tenantId);
                org.junit.jupiter.api.Assertions.assertEquals(1, failed.claimed());
                org.junit.jupiter.api.Assertions.assertEquals(retry < 3 ? 1 : 0, failed.retried());
                org.junit.jupiter.api.Assertions.assertEquals(retry == 3 ? 1 : 0, failed.repairRequired());
            }
            org.junit.jupiter.api.Assertions.assertEquals("REPAIR_REQUIRED", outboxStatus(retryEventId));
            org.junit.jupiter.api.Assertions.assertEquals(3, outboxRetryCount(retryEventId));
            org.junit.jupiter.api.Assertions.assertNull(jdbcTemplate
                .queryForObject("SELECT next_retry_time FROM biz_outbox_event WHERE id = ?", LocalDateTime.class, retryEventId));
            String safeError = jdbcTemplate
                .queryForObject("SELECT last_error_message FROM biz_outbox_event WHERE id = ?", String.class, retryEventId);
            org.junit.jupiter.api.Assertions.assertFalse(safeError.contains("missing-onboarding-process-v1"));
            org.junit.jupiter.api.Assertions.assertTrue(workflowOutboxProcessor.requeueRepair(tenantId, retryEventId));
            org.junit.jupiter.api.Assertions.assertEquals("PENDING", outboxStatus(retryEventId));
            org.junit.jupiter.api.Assertions.assertEquals(0, outboxRetryCount(retryEventId));
        } finally {
            workflowOutboxPolicy.setMaxRetries(originalMaxRetries);
            processEngine.getRepositoryService().deleteDeployment(deployment.deploymentId(), true);
        }
    }

    private void insertWorkflowOutboxEvent(Long eventId,
                                           Long tenantId,
                                           Long applicationId,
                                           Long businessVersion,
                                           String processKey,
                                           Long applicantUserId,
                                           Long merchantId,
                                           Long owningAgentId,
                                           String eventSuffix,
                                           LocalDateTime occurredTime) {
        try {
            String businessKey = "%s:MERCHANT_ONBOARDING:%s:%s".formatted(tenantId, applicationId, businessVersion);
            String payload = applicationContext.getBean(com.fasterxml.jackson.databind.ObjectMapper.class)
                .writeValueAsString(new OnboardingWorkflowStartPayload(applicationId, merchantId, owningAgentId, applicationId + 1000000, 1, businessVersion, "SYNTHETIC", "ONBOARDING", applicantUserId, processKey, businessKey));
            jdbcTemplate
                .update("""
                    INSERT INTO biz_outbox_event
                    (id, tenant_id, aggregate_type, aggregate_id, aggregate_version, event_type, event_key,
                     payload_json, status, retry_count, occurred_time, trace_id, create_time)
                    VALUES (?, ?, 'ONBOARDING_APPLICATION', ?, ?, ?, ?, ?, 'PENDING', 0, ?, ?, ?)
                    """, eventId, tenantId, applicationId, businessVersion, WorkflowOutboxProcessor.WORKFLOW_START_REQUESTED, "WORKFLOW-OUTBOX:" + eventSuffix, payload, occurredTime, "trace-" + eventSuffix, occurredTime);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new AssertionError(ex);
        }
    }

    private String outboxStatus(Long eventId) {
        return jdbcTemplate.queryForObject("SELECT status FROM biz_outbox_event WHERE id = ?", String.class, eventId);
    }

    private Integer outboxRetryCount(Long eventId) {
        return jdbcTemplate
            .queryForObject("SELECT retry_count FROM biz_outbox_event WHERE id = ?", Integer.class, eventId);
    }

    private void insertReviewApplication(Long tenantId,
                                         Long applicationId,
                                         Long merchantId,
                                         Long owningAgentId,
                                         Long submittedBy,
                                         Long kycVersionId,
                                         Integer kycVersionNo,
                                         LocalDateTime createTime) {
        jdbcTemplate
            .update("""
                INSERT INTO biz_onboarding_application
                (id, tenant_id, application_no, merchant_id, owning_agent_id, channel_code, product_code,
                 requirement_version, requirement_summary_json, channel_config_version, kyc_version_id,
                 status, submitted_by, submitted_time,
                 row_version, create_time, deleted)
                VALUES (?, ?, ?, ?, ?, 'SYNTHETIC', 'REVIEW', 'REQ-REV-1',
                        '{"requiredEvidenceTypes":[],"optionalEvidenceTypes":["BUSINESS_LICENSE"],"maxSupplementAttachments":5,"reuseExcludedFields":[]}',
                        'CFG-REV-1', ?, 'SUBMITTED', ?, ?, 0, ?, 0)
                """, applicationId, tenantId, "APP-REVIEW-" + applicationId, merchantId, owningAgentId, kycVersionId, submittedBy, createTime, createTime);
        jdbcTemplate.update("""
            INSERT INTO biz_kyc_version
            (id, tenant_id, merchant_id, onboarding_application_id, version_no, requirement_version, status,
             saved_step, step_completion_json, legal_name, legal_identifier_masked, license_issue_date,
             license_expiry_date, business_scope, row_version, create_user, create_time, frozen_time, deleted)
            VALUES (?, ?, ?, ?, ?, 'REQ-REV-1', 'SUBMITTED', 5, '[1,2,3,4,5]', 'Review Legal Subject',
                    '913***********0Y92', ?, ?, 'Technology services', 1, ?, ?, ?, 0)
            """, kycVersionId, tenantId, merchantId, applicationId, kycVersionNo, LocalDate.of(2020, 1, 1), LocalDate
            .of(2030, 1, 1), submittedBy, createTime, createTime);
    }

    private WorkflowRef startReviewWorkflow(Long tenantId,
                                            String processKey,
                                            Long applicationId,
                                            Long businessVersion,
                                            Long merchantId,
                                            Long owningAgentId,
                                            Long applicantUserId) {
        return workflowService.start(new StartWorkflowCommand(tenantId, processKey, "%s:MERCHANT_ONBOARDING:%s:%s"
            .formatted(tenantId, applicationId, businessVersion), Map
                .of("tenantId", tenantId, "merchantId", merchantId, "applicationId", applicationId, "kycVersion", businessVersion, "channelCode", "SYNTHETIC", "applicantId", applicantUserId, "owningAgentId", owningAgentId, "riskLevel", "LOW", "requiresSupplement", Boolean.FALSE)));
    }

    private String reviewApplicationStatus(Long tenantId, Long applicationId) {
        return jdbcTemplate
            .queryForObject("SELECT status FROM biz_onboarding_application WHERE tenant_id = ? AND id = ?", String.class, tenantId, applicationId);
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
            insertTenantRole(908201L, tenantId, "Agent Administrator 908", "AGENT_ADMIN", now);
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
                .queryForObject("SELECT id FROM sys_role WHERE tenant_id = ? AND code = 'AGENT_ADMIN' AND deleted = 0", Long.class, tenantId);
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
            insertTenantRole(915200L, tenantId, "Agent Administrator 915", "AGENT_ADMIN", now);
            insertTenantRole(915201L, tenantId, "Merchant Operator 915", "MERCHANT_OPERATOR", now);
            insertTenantRole(915202L, tenantId, "Merchant Reviewer 915", "MERCHANT_REVIEWER", now);
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
            org.junit.jupiter.api.Assertions.assertEquals(9, countMerchantManagementMenus(tenantId, "AGENT_ADMIN"));
            org.junit.jupiter.api.Assertions
                .assertEquals(6, countMerchantManagementMenus(tenantId, "MERCHANT_OPERATOR"));
            org.junit.jupiter.api.Assertions
                .assertEquals(4, countMerchantManagementMenus(tenantId, "MERCHANT_REVIEWER"));
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

    protected void verifyChannelConnectionConfiguration() {
        long tenantId = 941L;
        ChannelProductKey product = new ChannelProductKey("SYNTHETIC", "ONBOARDING");
        LocalDateTime baseTime = LocalDateTime.of(2026, 8, 24, 8, 0);
        EnumMap<ChannelOperation, String> paths = new EnumMap<>(ChannelOperation.class);
        EnumMap<ChannelOperation, Duration> operationTimeouts = new EnumMap<>(ChannelOperation.class);
        for (ChannelOperation operation : ChannelOperation.values()) {
            paths.put(operation, "/api/" + operation.name().toLowerCase(java.util.Locale.ROOT));
            operationTimeouts.put(operation, Duration.ofSeconds(5));
        }
        ChannelEndpointConfiguration endpoints = new ChannelEndpointConfiguration("https://synthetic.invalid", paths);
        ChannelTimeoutPolicy timeouts = new ChannelTimeoutPolicy(Duration.ofSeconds(1), Duration
            .ofSeconds(5), operationTimeouts);
        ChannelOnboardingState processing = new ChannelOnboardingState(ChannelStageStatus.PROCESSING, ChannelStageStatus.NOT_STARTED, ChannelStageStatus.NOT_STARTED, ChannelStageStatus.NOT_STARTED, ChannelStageStatus.PROCESSING);
        ChannelStatusMapping mapping = new ChannelStatusMapping(Map
            .of("PROCESSING", new ChannelMappedStatus(ChannelOperationStatus.PROCESSING, processing, null, 10, false)));
        com.fasterxml.jackson.databind.ObjectMapper objectMapper = applicationContext
            .getBean(com.fasterxml.jackson.databind.ObjectMapper.class);
        try {
            insertChannelConnectionVersion(941601L, tenantId, product, "CFG-1", objectMapper
                .writeValueAsString(endpoints), objectMapper.writeValueAsString(timeouts), "MAP-1", objectMapper
                    .writeValueAsString(mapping), "ENABLED", baseTime);
            insertChannelConnectionVersion(941602L, tenantId, product, "CFG-2", objectMapper
                .writeValueAsString(endpoints), objectMapper.writeValueAsString(timeouts), "MAP-2", objectMapper
                    .writeValueAsString(mapping), "DISABLED", baseTime.plusHours(1));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new AssertionError(ex);
        }

        TenantUtils.execute(tenantId, () -> {
            var exact = channelConnectionConfigCatalog.findVersion(tenantId, product, "CFG-1").orElseThrow();
            org.junit.jupiter.api.Assertions.assertEquals(ChannelConnectionStatus.ENABLED, exact.status());
            org.junit.jupiter.api.Assertions.assertEquals("CFG-1", channelConnectionConfigCatalog
                .findEffective(tenantId, product, baseTime.plusHours(2))
                .orElseThrow()
                .configVersion());
            try (var loaded = channelConfigurationLoader.load(tenantId, product, "CFG-1", baseTime.plusMinutes(1))) {
                org.junit.jupiter.api.Assertions.assertEquals(32, loaded.signingSecret().copyMaterial().length);
                org.junit.jupiter.api.Assertions.assertEquals(32, loaded.encryptionSecret().copyMaterial().length);
                org.junit.jupiter.api.Assertions.assertEquals(32, loaded.callbackVerificationSecret()
                    .copyMaterial().length);
                org.junit.jupiter.api.Assertions.assertFalse(loaded.toString().contains("env://"));
            }
            org.junit.jupiter.api.Assertions.assertEquals("env://channel.test.signing-key", jdbcTemplate
                .queryForObject("""
                    SELECT signing_key_ref FROM biz_channel_connection_version WHERE tenant_id = ? AND id = ?
                    """, String.class, tenantId, 941601L));
            org.junit.jupiter.api.Assertions.assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
                UPDATE biz_channel_connection_version SET status = 'DISABLED' WHERE tenant_id = ? AND id = ?
                """, tenantId, 941601L));
            org.junit.jupiter.api.Assertions.assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
                DELETE FROM biz_channel_connection_version WHERE tenant_id = ? AND id = ?
                """, tenantId, 941601L));
        });
    }

    protected void verifyChannelTransportAuditIsSanitizedAndAppendOnly() {
        long tenantId = 942L;
        LocalDateTime requestTime = LocalDateTime.of(2026, 8, 24, 9, 0);
        ChannelCommandContext context = new ChannelCommandContext(tenantId, new ChannelProductKey("SYNTHETIC", "ONBOARDING"), "CFG-942", ChannelBusinessType.ONBOARDING, 942001L, 2L, "SERIAL-942", "TRACE-942");
        TenantUtils.execute(tenantId, () -> {
            Long auditId = channelTransportAuditPort
                .append(new ChannelTransportAuditRecord(context, ChannelOperation.SUBMIT_ONBOARDING, ChannelTransportOutcome.SUCCEEDED, requestTime, requestTime
                    .plusSeconds(1), 1000L, "nonce-fingerprint-942", "ref-signing-942", "ref-encryption-942", 202, null));

            org.junit.jupiter.api.Assertions.assertNotNull(auditId);
            Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT * FROM biz_channel_transport_audit WHERE tenant_id = ? AND id = ?
                """, tenantId, auditId);
            org.junit.jupiter.api.Assertions.assertEquals("SERIAL-942", jdbcTemplate.queryForObject("""
                SELECT business_serial FROM biz_channel_transport_audit WHERE tenant_id = ? AND id = ?
                """, String.class, tenantId, auditId));
            org.junit.jupiter.api.Assertions.assertEquals("TRACE-942", jdbcTemplate.queryForObject("""
                SELECT trace_id FROM biz_channel_transport_audit WHERE tenant_id = ? AND id = ?
                """, String.class, tenantId, auditId));
            org.junit.jupiter.api.Assertions.assertEquals("nonce-fingerprint-942", jdbcTemplate.queryForObject("""
                SELECT nonce_fingerprint FROM biz_channel_transport_audit WHERE tenant_id = ? AND id = ?
                """, String.class, tenantId, auditId));
            String persistedValues = row.values().toString();
            org.junit.jupiter.api.Assertions.assertFalse(persistedValues.contains("91350211M000100Y43"));
            org.junit.jupiter.api.Assertions.assertFalse(persistedValues.contains("env://"));
            org.junit.jupiter.api.Assertions.assertFalse(persistedValues.contains("synthetic.invalid"));
            org.junit.jupiter.api.Assertions.assertEquals(0, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE LOWER(table_name) = 'biz_channel_transport_audit'
                  AND LOWER(column_name) IN
                      ('payload', 'request_payload', 'response_payload', 'endpoint', 'nonce', 'signature',
                       'signing_key_ref', 'encryption_key_ref')
                """, Integer.class));
            org.junit.jupiter.api.Assertions.assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
                UPDATE biz_channel_transport_audit SET outcome = 'FAILED' WHERE tenant_id = ? AND id = ?
                """, tenantId, auditId));
            org.junit.jupiter.api.Assertions.assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
                DELETE FROM biz_channel_transport_audit WHERE tenant_id = ? AND id = ?
                """, tenantId, auditId));
            org.junit.jupiter.api.Assertions.assertEquals("SUCCEEDED", jdbcTemplate.queryForObject("""
                SELECT outcome FROM biz_channel_transport_audit WHERE tenant_id = ? AND id = ?
                """, String.class, tenantId, auditId));
        });
    }

    protected void verifyChannelCallbackIsAuthenticatedReplaySafeAndAudited() {
        long tenantId = 943L;
        ChannelProductKey product = new ChannelProductKey("SYNTHETIC", "ONBOARDING");
        String configVersion = "CFG-943";
        LocalDateTime baseTime = LocalDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(2);
        insertChannelConnectionVersion(943601L, tenantId, product, configVersion, channelEndpointJson(), channelTimeoutJson(), "MAP-943", channelStatusMappingJson(), "ENABLED", baseTime);
        byte[] payload = "{\"eventId\":\"EVENT-943\",\"eventType\":\"STATUS_CHANGED\",\"businessType\":\"ONBOARDING\",\"businessId\":943001,\"businessVersion\":2,\"businessSerial\":\"SERIAL-943\",\"rawStatusCode\":\"PROCESSING\",\"occurredTime\":\"2026-08-25T00:00:00Z\",\"legalIdentifier\":\"91350211M000100Y43\"}"
            .getBytes(StandardCharsets.UTF_8);
        String keyVersion = callbackKeyVersion("env://channel.test.callback-key");
        long timestamp = System.currentTimeMillis();

        TenantUtils.execute(tenantId, () -> {
            int eventsBefore = jdbcTemplate
                .queryForObject("SELECT COUNT(*) FROM biz_channel_event WHERE tenant_id = ?", Integer.class, tenantId);
            RawChannelCallback invalid = new RawChannelCallback(tenantId, product, configVersion, Long
                .toString(timestamp), "nonce-invalid-94301", keyVersion, "a".repeat(43), payload, "203.0.113.9");
            ChannelCallbackException invalidFailure = org.junit.jupiter.api.Assertions
                .assertThrows(ChannelCallbackException.class, () -> channelCallbackVerifier.verify(invalid));
            org.junit.jupiter.api.Assertions
                .assertEquals(ChannelCallbackException.Code.SIGNATURE_INVALID, invalidFailure.code());
            org.junit.jupiter.api.Assertions.assertEquals(0, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM biz_channel_callback_nonce WHERE tenant_id = ?
                """, Integer.class, tenantId));

            String nonce = "nonce-valid-943001";
            String signature = signChannelCallback(tenantId, product, configVersion, timestamp, nonce, keyVersion, payload);
            RawChannelCallback valid = new RawChannelCallback(tenantId, product, configVersion, Long
                .toString(timestamp), nonce, keyVersion, signature, payload, "203.0.113.9");
            VerifiedChannelCallback verified = channelCallbackVerifier.verify(valid);
            org.junit.jupiter.api.Assertions.assertEquals(configVersion, verified.configVersion());
            org.junit.jupiter.api.Assertions.assertEquals(keyVersion, verified.keyVersion());
            org.junit.jupiter.api.Assertions.assertArrayEquals(payload, verified.payload());
            ChannelCallbackException replayFailure = org.junit.jupiter.api.Assertions
                .assertThrows(ChannelCallbackException.class, () -> channelCallbackVerifier.verify(valid));
            org.junit.jupiter.api.Assertions.assertEquals(ChannelCallbackException.Code.REPLAY_DETECTED, replayFailure
                .code());

            org.junit.jupiter.api.Assertions.assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM biz_channel_callback_nonce WHERE tenant_id = ?
                """, Integer.class, tenantId));
            org.junit.jupiter.api.Assertions.assertEquals(3, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM biz_channel_callback_security_audit WHERE tenant_id = ?
                """, Integer.class, tenantId));
            org.junit.jupiter.api.Assertions.assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM biz_channel_callback_security_audit
                WHERE tenant_id = ? AND outcome = 'ACCEPTED'
                """, Integer.class, tenantId));
            org.junit.jupiter.api.Assertions.assertEquals(2, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM biz_channel_callback_security_audit
                WHERE tenant_id = ? AND outcome = 'REJECTED'
                """, Integer.class, tenantId));
            String nonceHash = jdbcTemplate.queryForObject("""
                SELECT nonce_hash FROM biz_channel_callback_nonce WHERE tenant_id = ?
                """, String.class, tenantId);
            org.junit.jupiter.api.Assertions.assertEquals(sha256(nonce.getBytes(StandardCharsets.UTF_8)), nonceHash);
            org.junit.jupiter.api.Assertions.assertFalse(nonceHash.contains(nonce));
            Map<String, Object> acceptedAudit = jdbcTemplate.queryForMap("""
                SELECT * FROM biz_channel_callback_security_audit
                WHERE tenant_id = ? AND outcome = 'ACCEPTED'
                """, tenantId);
            String persistedValues = acceptedAudit.values().toString();
            org.junit.jupiter.api.Assertions.assertFalse(persistedValues.contains("91350211M000100Y43"));
            org.junit.jupiter.api.Assertions.assertFalse(persistedValues.contains(signature));
            org.junit.jupiter.api.Assertions.assertFalse(persistedValues.contains("203.0.113.9"));
            org.junit.jupiter.api.Assertions.assertEquals(0, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE LOWER(table_name) = 'biz_channel_callback_security_audit'
                  AND LOWER(column_name) IN
                      ('payload', 'request_payload', 'nonce', 'signature', 'source_address', 'callback_key_ref')
                """, Integer.class));
            Long acceptedAuditId = ((Number)acceptedAudit.entrySet()
                .stream()
                .filter(entry -> "id".equalsIgnoreCase(entry.getKey()))
                .findFirst()
                .orElseThrow()
                .getValue()).longValue();
            org.junit.jupiter.api.Assertions.assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
                UPDATE biz_channel_callback_security_audit SET outcome = 'REJECTED'
                WHERE tenant_id = ? AND id = ?
                """, tenantId, acceptedAuditId));
            org.junit.jupiter.api.Assertions.assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
                DELETE FROM biz_channel_callback_security_audit WHERE tenant_id = ? AND id = ?
                """, tenantId, acceptedAuditId));
            org.junit.jupiter.api.Assertions.assertEquals(eventsBefore, jdbcTemplate
                .queryForObject("SELECT COUNT(*) FROM biz_channel_event WHERE tenant_id = ?", Integer.class, tenantId));
        });
    }

    private String channelEndpointJson() {
        EnumMap<ChannelOperation, String> paths = new EnumMap<>(ChannelOperation.class);
        for (ChannelOperation operation : ChannelOperation.values()) {
            paths.put(operation, "/api/" + operation.name().toLowerCase(java.util.Locale.ROOT));
        }
        try {
            return applicationContext.getBean(com.fasterxml.jackson.databind.ObjectMapper.class)
                .writeValueAsString(new ChannelEndpointConfiguration("https://synthetic.invalid", paths));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new AssertionError(ex);
        }
    }

    private String channelTimeoutJson() {
        EnumMap<ChannelOperation, Duration> operationTimeouts = new EnumMap<>(ChannelOperation.class);
        for (ChannelOperation operation : ChannelOperation.values()) {
            operationTimeouts.put(operation, Duration.ofSeconds(5));
        }
        try {
            return applicationContext.getBean(com.fasterxml.jackson.databind.ObjectMapper.class)
                .writeValueAsString(new ChannelTimeoutPolicy(Duration.ofSeconds(1), Duration
                    .ofSeconds(5), operationTimeouts));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new AssertionError(ex);
        }
    }

    private String channelStatusMappingJson() {
        ChannelOnboardingState state = new ChannelOnboardingState(ChannelStageStatus.PROCESSING, ChannelStageStatus.NOT_STARTED, ChannelStageStatus.NOT_STARTED, ChannelStageStatus.NOT_STARTED, ChannelStageStatus.PROCESSING);
        try {
            return applicationContext.getBean(com.fasterxml.jackson.databind.ObjectMapper.class)
                .writeValueAsString(new ChannelStatusMapping(Map
                    .of("PROCESSING", new ChannelMappedStatus(ChannelOperationStatus.PROCESSING, state, null, 10, false))));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new AssertionError(ex);
        }
    }

    private String signChannelCallback(long tenantId,
                                       ChannelProductKey product,
                                       String configVersion,
                                       long timestamp,
                                       String nonce,
                                       String keyVersion,
                                       byte[] payload) {
        try {
            byte[] key = new byte[32];
            java.util.Arrays.fill(key, (byte)2);
            String canonical = String.join("\n", "CALLBACK", Long.toString(tenantId), product.channelCode(), product
                .productCode(), configVersion, Long.toString(timestamp), nonce, keyVersion, sha256(payload));
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(key, "HmacSHA256"));
            return java.util.Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException ex) {
            throw new AssertionError(ex);
        }
    }

    private String callbackKeyVersion(String reference) {
        return "ref-" + sha256(reference.getBytes(StandardCharsets.UTF_8)).substring(0, 16);
    }

    private String sha256(byte[] value) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new AssertionError(ex);
        }
    }

    private void insertChannelConnectionVersion(Long id,
                                                Long tenantId,
                                                ChannelProductKey product,
                                                String configVersion,
                                                String endpointJson,
                                                String timeoutJson,
                                                String statusMappingVersion,
                                                String statusMappingJson,
                                                String status,
                                                LocalDateTime effectiveTime) {
        jdbcTemplate.update("""
            INSERT INTO biz_channel_connection_version
            (id, tenant_id, channel_code, product_code, config_version, endpoint_json, timeout_json,
             status_mapping_version, status_mapping_json, signing_key_ref, encryption_key_ref,
             callback_verification_key_ref, status, effective_time, create_time, deleted)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'env://channel.test.signing-key',
                    'env://channel.test.encryption-key', 'env://channel.test.callback-key', ?, ?, ?, 0)
            """, id, tenantId, product.channelCode(), product
            .productCode(), configVersion, endpointJson, timeoutJson, statusMappingVersion, statusMappingJson, status, effectiveTime, effectiveTime
                .minusMinutes(1));
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

    protected void verifyVersionedKycProfile() {
        long tenantId = 925L;
        long rootAgentId = 92501L;
        long merchantAgentId = 92502L;
        long rootUserId = 92511L;
        long merchantAgentUserId = 92512L;
        long merchantId = 925101L;

        TenantUtils.execute(tenantId, () -> {
            agentHierarchyService.register(registration(rootAgentId, tenantId, 0L, rootUserId, "PROFILE-ROOT"));
            agentHierarchyService
                .register(registration(merchantAgentId, tenantId, rootAgentId, merchantAgentUserId, "PROFILE-MERCHANT"));
            merchantMasterService
                .register(rootUserId, merchantRegistration(merchantId, tenantId, merchantAgentId, 925201L, 925202L, "PROFILE-MERCHANT", "7"
                    .repeat(64)));
            LocalDateTime baseTime = LocalDateTime.of(2026, 8, 20, 14, 0);
            insertPricingVersion(tenantId, merchantAgentId, 925501L, 1, "CHANNEL-P", "PRODUCT-P", "0.01000000", baseTime);
            jdbcTemplate.update("""
                INSERT INTO biz_agent_merchant_default_version
                (id, tenant_id, agent_id, version_no, default_payload_json, effective_time, status,
                 create_user, create_time, deleted)
                VALUES (?, ?, ?, 1, ?, ?, 'PUBLISHED', ?, ?, 0)
                """, 925601L, tenantId, merchantAgentId, """
                {"products":[
                  {"channelCode":"CHANNEL-P","productCode":"PRODUCT-P","pricingVersionId":925501}
                ]}
                """, baseTime, rootUserId, baseTime);
            insertChannelProductVersion(925701L, tenantId, "CHANNEL-P", "PRODUCT-P", "CFG-P-1", "REQ-P-1", "[\"ENTERPRISE\"]", "ENABLED", baseTime);
            OnboardingDraftView draft = onboardingDraftService
                .createOrLoad(tenantId, merchantAgentUserId, merchantId, "CHANNEL-P", "PRODUCT-P", "127.0.0.1");

            KycProfileSaveCommand command = validProfileCommand(tenantId, merchantAgentUserId, merchantId, draft.draft()
                .applicationId(), 0L);
            KycProfileView saved = kycProfileService.save(command);
            org.junit.jupiter.api.Assertions.assertEquals(1L, saved.rowVersion());
            org.junit.jupiter.api.Assertions.assertEquals("913***********0Y92", saved.legalIdentifierMasked());
            org.junit.jupiter.api.Assertions.assertEquals(3, saved.persons().size());
            org.junit.jupiter.api.Assertions.assertTrue(saved.persons()
                .stream()
                .allMatch(person -> person.identityNumberMasked().contains("*")));
            org.junit.jupiter.api.Assertions.assertTrue(saved.persons()
                .stream()
                .allMatch(person -> person.mobileMasked().contains("*")));
            org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("100.00"), saved.shareholders()
                .stream()
                .map(KycProfileView.ShareholderView::ownershipPercent)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

            byte[] legalCiphertext = jdbcTemplate.queryForObject("""
                SELECT legal_identifier_ciphertext FROM biz_kyc_version WHERE tenant_id = ? AND id = ?
                """, byte[].class, tenantId, draft.draft().kycVersionId());
            byte[] personCiphertext = jdbcTemplate.queryForObject("""
                SELECT person_payload_ciphertext FROM biz_kyc_version WHERE tenant_id = ? AND id = ?
                """, byte[].class, tenantId, draft.draft().kycVersionId());
            byte[] shareholderCiphertext = jdbcTemplate.queryForObject("""
                SELECT shareholder_payload_ciphertext FROM biz_kyc_version WHERE tenant_id = ? AND id = ?
                """, byte[].class, tenantId, draft.draft().kycVersionId());
            org.junit.jupiter.api.Assertions.assertNotNull(legalCiphertext);
            org.junit.jupiter.api.Assertions.assertNotNull(personCiphertext);
            org.junit.jupiter.api.Assertions.assertNotNull(shareholderCiphertext);
            org.junit.jupiter.api.Assertions
                .assertFalse(new String(personCiphertext, java.nio.charset.StandardCharsets.UTF_8)
                    .contains("13800000001"));
            org.junit.jupiter.api.Assertions
                .assertFalse(new String(shareholderCiphertext, java.nio.charset.StandardCharsets.UTF_8)
                    .contains("Shareholder One"));
            String payloadKeyVersion = jdbcTemplate.queryForObject("""
                SELECT payload_key_version FROM biz_kyc_version WHERE tenant_id = ? AND id = ?
                """, String.class, tenantId, draft.draft().kycVersionId());
            org.junit.jupiter.api.Assertions.assertNotNull(payloadKeyVersion);
            org.junit.jupiter.api.Assertions.assertTrue(payloadKeyVersion.startsWith("env://"));

            org.junit.jupiter.api.Assertions
                .assertThrows(OnboardingDraftConflictException.class, () -> kycProfileService.save(command));
            KycProfileSaveCommand missingBeneficiary = new KycProfileSaveCommand(command.tenantId(), command
                .actorUserId(), command.merchantId(), command.applicationId(), command.legalName(), command
                    .legalIdentifier(), command.licenseIssueDate(), command.licenseExpiryDate(), command
                        .businessScope(), command.address(), command.persons()
                            .stream()
                            .filter(person -> !KycProfileSaveCommand.PersonRole.BENEFICIAL_OWNER.equals(person.role()))
                            .toList(), command.shareholders(), 1L, command.ipAddress());
            org.junit.jupiter.api.Assertions.assertThrows(MerchantDomainException.class, () -> kycProfileService
                .save(missingBeneficiary));
            List<KycProfileSaveCommand.Person> expiredPersons = new ArrayList<>(command.persons());
            KycProfileSaveCommand.Person operator = expiredPersons.get(1);
            expiredPersons.set(1, new KycProfileSaveCommand.Person(operator.role(), operator.name(), operator
                .identityNumber(), operator.mobile(), operator.documentValidFrom(), LocalDate.of(2026, 8, 21)));
            KycProfileSaveCommand expired = new KycProfileSaveCommand(command.tenantId(), command.actorUserId(), command
                .merchantId(), command.applicationId(), command.legalName(), command.legalIdentifier(), command
                    .licenseIssueDate(), command.licenseExpiryDate(), command.businessScope(), command
                        .address(), expiredPersons, command.shareholders(), 1L, command.ipAddress());
            org.junit.jupiter.api.Assertions.assertThrows(MerchantDomainException.class, () -> kycProfileService
                .save(expired));
            List<KycProfileSaveCommand.Shareholder> invalidShareholders = List
                .of(new KycProfileSaveCommand.Shareholder(KycProfileSaveCommand.ShareholderType.INDIVIDUAL, "Shareholder One", "110101198001011234", new BigDecimal("90.00")));
            KycProfileSaveCommand invalidOwnership = new KycProfileSaveCommand(command.tenantId(), command
                .actorUserId(), command.merchantId(), command.applicationId(), command.legalName(), command
                    .legalIdentifier(), command.licenseIssueDate(), command.licenseExpiryDate(), command
                        .businessScope(), command.address(), command.persons(), invalidShareholders, 1L, command
                            .ipAddress());
            org.junit.jupiter.api.Assertions.assertThrows(MerchantDomainException.class, () -> kycProfileService
                .save(invalidOwnership));
            org.junit.jupiter.api.Assertions.assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM biz_security_audit
                WHERE tenant_id = ? AND object_id = ? AND action = 'KYC_PROFILE_SAVE'
                """, Integer.class, tenantId, draft.draft().kycVersionId()));
        });
    }

    protected void verifySettlementAccountModes() {
        long tenantId = 926L;
        long rootAgentId = 92601L;
        long merchantAgentId = 92602L;
        long rootUserId = 92611L;
        long merchantAgentUserId = 92612L;
        long merchantId = 926101L;

        TenantUtils.execute(tenantId, () -> {
            agentHierarchyService.register(registration(rootAgentId, tenantId, 0L, rootUserId, "SETTLEMENT-ROOT"));
            agentHierarchyService
                .register(registration(merchantAgentId, tenantId, rootAgentId, merchantAgentUserId, "SETTLEMENT-MERCHANT"));
            merchantMasterService
                .register(rootUserId, merchantRegistration(merchantId, tenantId, merchantAgentId, 926201L, 926202L, "SETTLEMENT-MERCHANT", "8"
                    .repeat(64)));
            LocalDateTime baseTime = LocalDateTime.of(2026, 8, 20, 16, 0);
            insertPricingVersion(tenantId, merchantAgentId, 926501L, 1, "CHANNEL-S", "PRODUCT-S", "0.01000000", baseTime);
            jdbcTemplate.update("""
                INSERT INTO biz_agent_merchant_default_version
                (id, tenant_id, agent_id, version_no, default_payload_json, effective_time, status,
                 create_user, create_time, deleted)
                VALUES (?, ?, ?, 1, ?, ?, 'PUBLISHED', ?, ?, 0)
                """, 926601L, tenantId, merchantAgentId, """
                {"products":[
                  {"channelCode":"CHANNEL-S","productCode":"PRODUCT-S","pricingVersionId":926501}
                ]}
                """, baseTime, rootUserId, baseTime);
            insertChannelProductVersion(926701L, tenantId, "CHANNEL-S", "PRODUCT-S", "CFG-S-1", "REQ-S-1", "[\"ENTERPRISE\"]", "ENABLED", baseTime);
            OnboardingDraftView draft = onboardingDraftService
                .createOrLoad(tenantId, merchantAgentUserId, merchantId, "CHANNEL-S", "PRODUCT-S", "127.0.0.1");

            org.mockito.Mockito.when(settlementAccountVerificationPort.verify(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new SettlementAccountVerificationPort.VerificationResult(SettlementAccountVerificationPort.SettlementVerificationStatus.VERIFIED, "VERIFY-926-1", "SYNTHETIC-V1"));
            SettlementAccountSaveCommand ordinary = new SettlementAccountSaveCommand(tenantId, merchantAgentUserId, merchantId, draft
                .draft()
                .applicationId(), SettlementAccountVerificationPort.SettlementMode.ORDINARY, "Profile Legal Subject", "BANK-001", "Shanghai Branch", "6222020200000000", 0L, "127.0.0.1");
            SettlementAccountView verified = settlementAccountService.save(ordinary);
            org.junit.jupiter.api.Assertions.assertEquals(1L, verified.rowVersion());
            org.junit.jupiter.api.Assertions.assertEquals("6222********0000", verified.accountNumberMasked());
            org.junit.jupiter.api.Assertions
                .assertEquals(SettlementAccountVerificationPort.SettlementVerificationStatus.VERIFIED, verified
                    .verificationStatus());
            org.junit.jupiter.api.Assertions.assertNotNull(verified.verifiedTime());
            byte[] accountCiphertext = jdbcTemplate.queryForObject("""
                SELECT settlement_account_ciphertext FROM biz_kyc_version WHERE tenant_id = ? AND id = ?
                """, byte[].class, tenantId, draft.draft().kycVersionId());
            byte[] payloadCiphertext = jdbcTemplate.queryForObject("""
                SELECT settlement_payload_ciphertext FROM biz_kyc_version WHERE tenant_id = ? AND id = ?
                """, byte[].class, tenantId, draft.draft().kycVersionId());
            org.junit.jupiter.api.Assertions.assertNotNull(accountCiphertext);
            org.junit.jupiter.api.Assertions.assertNotNull(payloadCiphertext);
            org.junit.jupiter.api.Assertions
                .assertFalse(new String(accountCiphertext, java.nio.charset.StandardCharsets.UTF_8)
                    .contains("6222020200000000"));
            org.junit.jupiter.api.Assertions
                .assertFalse(new String(payloadCiphertext, java.nio.charset.StandardCharsets.UTF_8)
                    .contains("Profile Legal Subject"));
            org.junit.jupiter.api.Assertions.assertEquals("VERIFIED", jdbcTemplate.queryForObject("""
                SELECT settlement_verification_status FROM biz_kyc_version WHERE tenant_id = ? AND id = ?
                """, String.class, tenantId, draft.draft().kycVersionId()));

            org.mockito.Mockito.when(settlementAccountVerificationPort.verify(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new SettlementAccountVerificationPort.VerificationResult(SettlementAccountVerificationPort.SettlementVerificationStatus.FAILED, "VERIFY-926-FAIL", "SYNTHETIC-V1"));
            SettlementAccountSaveCommand failed = new SettlementAccountSaveCommand(tenantId, merchantAgentUserId, merchantId, draft
                .draft()
                .applicationId(), SettlementAccountVerificationPort.SettlementMode.ACCELERATED, "Wrong Holder", "BANK-001", "Shanghai Branch", "6222020200000001", 1L, "127.0.0.1");
            org.junit.jupiter.api.Assertions.assertThrows(MerchantDomainException.class, () -> settlementAccountService
                .save(failed));
            org.junit.jupiter.api.Assertions.assertEquals(1L, jdbcTemplate.queryForObject("""
                SELECT row_version FROM biz_kyc_version WHERE tenant_id = ? AND id = ?
                """, Long.class, tenantId, draft.draft().kycVersionId()));

            org.mockito.Mockito.when(settlementAccountVerificationPort.verify(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new SettlementAccountVerificationPort.VerificationResult(SettlementAccountVerificationPort.SettlementVerificationStatus.PENDING, "VERIFY-926-PENDING", "ASYNC-V1"));
            SettlementAccountSaveCommand accelerated = new SettlementAccountSaveCommand(tenantId, merchantAgentUserId, merchantId, draft
                .draft()
                .applicationId(), SettlementAccountVerificationPort.SettlementMode.ACCELERATED, "Profile Legal Subject", "BANK-002", "Accelerated Branch", "6222020200000002", 1L, "127.0.0.1");
            SettlementAccountView pending = settlementAccountService.save(accelerated);
            org.junit.jupiter.api.Assertions.assertEquals(2L, pending.rowVersion());
            org.junit.jupiter.api.Assertions
                .assertEquals(SettlementAccountVerificationPort.SettlementVerificationStatus.PENDING, pending
                    .verificationStatus());
            org.junit.jupiter.api.Assertions.assertNull(pending.verifiedTime());
            org.junit.jupiter.api.Assertions
                .assertThrows(OnboardingDraftConflictException.class, () -> settlementAccountService.save(accelerated));
            org.junit.jupiter.api.Assertions.assertEquals(2, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM biz_security_audit
                WHERE tenant_id = ? AND object_id = ? AND action = 'SETTLEMENT_ACCOUNT_SAVE'
                """, Integer.class, tenantId, draft.draft().kycVersionId()));
        });
    }

    protected void verifyOnboardingPricingSelection() {
        long tenantId = 927L;
        long rootAgentId = 92701L;
        long merchantAgentId = 92702L;
        long siblingAgentId = 92703L;
        long rootUserId = 92711L;
        long merchantAgentUserId = 92712L;
        long merchantId = 927101L;

        TenantUtils.execute(tenantId, () -> {
            agentHierarchyService.register(registration(rootAgentId, tenantId, 0L, rootUserId, "PRICING-ROOT"));
            agentHierarchyService
                .register(registration(merchantAgentId, tenantId, rootAgentId, merchantAgentUserId, "PRICING-MERCHANT"));
            agentHierarchyService
                .register(registration(siblingAgentId, tenantId, rootAgentId, 92713L, "PRICING-SIBLING"));
            merchantMasterService
                .register(rootUserId, merchantRegistration(merchantId, tenantId, merchantAgentId, 927201L, 927202L, "PRICING-MERCHANT", "9"
                    .repeat(64)));
            LocalDateTime baseTime = LocalDateTime.of(2026, 8, 20, 18, 0);
            insertPricingVersion(tenantId, rootAgentId, 927501L, 1, "CHANNEL-Q", "PRODUCT-Q", "0.01000000", "1.00", "0.80000000", baseTime);
            insertPricingVersion(tenantId, merchantAgentId, 927502L, 1, "CHANNEL-Q", "PRODUCT-Q", "0.02000000", "2.00", "0.50000000", baseTime);
            insertPricingVersion(tenantId, siblingAgentId, 927503L, 1, "CHANNEL-Q", "PRODUCT-Q", "0.02000000", "2.00", "0.50000000", baseTime);
            jdbcTemplate.update("""
                INSERT INTO biz_agent_merchant_default_version
                (id, tenant_id, agent_id, version_no, default_payload_json, effective_time, status,
                 create_user, create_time, deleted)
                VALUES (?, ?, ?, 1, ?, ?, 'PUBLISHED', ?, ?, 0)
                """, 927601L, tenantId, merchantAgentId, """
                {"products":[
                  {"channelCode":"CHANNEL-Q","productCode":"PRODUCT-Q","pricingVersionId":927502}
                ]}
                """, baseTime, rootUserId, baseTime);
            insertChannelProductVersion(927701L, tenantId, "CHANNEL-Q", "PRODUCT-Q", "CFG-Q-1", "REQ-Q-1", "[\"ENTERPRISE\"]", "ENABLED", baseTime);
            OnboardingDraftView draft = onboardingDraftService
                .createOrLoad(tenantId, merchantAgentUserId, merchantId, "CHANNEL-Q", "PRODUCT-Q", "127.0.0.1");
            org.junit.jupiter.api.Assertions.assertEquals(927502L, draft.draft().pricingVersionId());

            insertPricingVersion(tenantId, rootAgentId, 927504L, 2, "CHANNEL-Q", "PRODUCT-Q", "0.03000000", "3.00", "0.40000000", baseTime
                .plusHours(1));
            org.junit.jupiter.api.Assertions
                .assertThrows(AgentPricingBoundaryException.class, () -> onboardingDraftService
                    .saveProgress(tenantId, merchantAgentUserId, merchantId, draft.draft().applicationId(), 2, List
                        .of(1), 0L, "127.0.0.1"));
            org.junit.jupiter.api.Assertions.assertEquals(0L, jdbcTemplate.queryForObject("""
                SELECT row_version FROM biz_kyc_version WHERE tenant_id = ? AND id = ?
                """, Long.class, tenantId, draft.draft().kycVersionId()));

            insertPricingVersion(tenantId, merchantAgentId, 927505L, 2, "CHANNEL-Q", "PRODUCT-Q", "0.04000000", "4.00", "0.30000000", baseTime
                .plusHours(2));
            OnboardingPricingView selected = onboardingPricingService
                .select(tenantId, merchantAgentUserId, merchantId, draft.draft()
                    .applicationId(), 927505L, 0L, "127.0.0.1");
            org.junit.jupiter.api.Assertions.assertEquals(1L, selected.rowVersion());
            org.junit.jupiter.api.Assertions.assertEquals(927505L, selected.pricingVersionId());
            org.junit.jupiter.api.Assertions.assertEquals(2, selected.versionNo());
            OnboardingDraftView saved = onboardingDraftService
                .saveProgress(tenantId, merchantAgentUserId, merchantId, draft.draft().applicationId(), 2, List
                    .of(1), 1L, "127.0.0.1");
            org.junit.jupiter.api.Assertions.assertEquals(2L, saved.draft().rowVersion());
            org.junit.jupiter.api.Assertions.assertEquals(927505L, saved.draft().pricingVersionId());
            org.junit.jupiter.api.Assertions
                .assertThrows(AgentPricingBoundaryException.class, () -> onboardingPricingService
                    .select(tenantId, merchantAgentUserId, merchantId, draft.draft()
                        .applicationId(), 927503L, 2L, "127.0.0.1"));
            org.junit.jupiter.api.Assertions.assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM biz_security_audit
                WHERE tenant_id = ? AND object_id = ? AND action = 'ONBOARDING_PRICING_SELECT'
                """, Integer.class, tenantId, draft.draft().kycVersionId()));
        });
    }

    protected void verifyOperatingPlatforms() {
        long tenantId = 928L;
        long rootAgentId = 92801L;
        long merchantAgentId = 92802L;
        long rootUserId = 92811L;
        long merchantAgentUserId = 92812L;
        long merchantId = 928101L;

        TenantUtils.execute(tenantId, () -> {
            agentHierarchyService.register(registration(rootAgentId, tenantId, 0L, rootUserId, "PLATFORM-ROOT"));
            agentHierarchyService
                .register(registration(merchantAgentId, tenantId, rootAgentId, merchantAgentUserId, "PLATFORM-MERCHANT"));
            merchantMasterService
                .register(rootUserId, merchantRegistration(merchantId, tenantId, merchantAgentId, 928201L, 928202L, "PLATFORM-MERCHANT", "6"
                    .repeat(64)));
            LocalDateTime baseTime = LocalDateTime.of(2026, 8, 20, 20, 0);
            insertPricingVersion(tenantId, merchantAgentId, 928501L, 1, "CHANNEL-T", "PRODUCT-T", "0.01000000", baseTime);
            jdbcTemplate.update("""
                INSERT INTO biz_agent_merchant_default_version
                (id, tenant_id, agent_id, version_no, default_payload_json, effective_time, status,
                 create_user, create_time, deleted)
                VALUES (?, ?, ?, 1, ?, ?, 'PUBLISHED', ?, ?, 0)
                """, 928601L, tenantId, merchantAgentId, """
                {"products":[
                  {"channelCode":"CHANNEL-T","productCode":"PRODUCT-T","pricingVersionId":928501}
                ]}
                """, baseTime, rootUserId, baseTime);
            jdbcTemplate
                .update("""
                    INSERT INTO biz_channel_product_version
                    (id, tenant_id, channel_code, product_code, config_version, requirement_version,
                     supported_merchant_types_json, requirement_summary_json, status, effective_time,
                     create_user, create_time, deleted)
                    VALUES (?, ?, 'CHANNEL-T', 'PRODUCT-T', 'CFG-T-1', 'REQ-T-1', '["ENTERPRISE"]',
                            '{"requiredEvidenceTypes":["BUSINESS_LICENSE"],"optionalEvidenceTypes":["STORE_QR","PLATFORM_CASH_FLOW","SUPPLEMENT"],"maxSupplementAttachments":5,"reuseExcludedFields":[]}',
                            'ENABLED', ?, 1, ?, 0)
                    """, 928701L, tenantId, baseTime, baseTime);
            OnboardingDraftView draft = onboardingDraftService
                .createOrLoad(tenantId, merchantAgentUserId, merchantId, "CHANNEL-T", "PRODUCT-T", "127.0.0.1");

            OperatingPlatform first = operatingPlatformService.create(tenantId, merchantAgentUserId, merchantId, draft
                .draft()
                .applicationId(), "TAOBAO", "Store A", "https://store-a.example.com", "STORE-A", OperatingPlatform.CertificationStatus.UNVERIFIED, "127.0.0.1");
            OperatingPlatform second = operatingPlatformService.create(tenantId, merchantAgentUserId, merchantId, draft
                .draft()
                .applicationId(), "DOUYIN", "Store B", "https://store-b.example.com", "STORE-B", OperatingPlatform.CertificationStatus.CERTIFIED, "127.0.0.1");
            org.junit.jupiter.api.Assertions.assertNotEquals(first.id(), second.id());
            org.junit.jupiter.api.Assertions.assertEquals(2, operatingPlatformService
                .list(tenantId, merchantAgentUserId, merchantId, draft.draft().applicationId())
                .size());

            OperatingPlatform updatedFirst = operatingPlatformService
                .update(tenantId, merchantAgentUserId, merchantId, draft.draft().applicationId(), first
                    .id(), "Store A Updated", "https://store-a.example.com/new", "STORE-A", OperatingPlatform.CertificationStatus.CERTIFIED, 0L, "127.0.0.1");
            org.junit.jupiter.api.Assertions.assertEquals(1L, updatedFirst.rowVersion());
            OperatingPlatform unchangedSecond = operatingPlatformService
                .list(tenantId, merchantAgentUserId, merchantId, draft.draft().applicationId())
                .stream()
                .filter(item -> item.id().equals(second.id()))
                .findFirst()
                .orElseThrow();
            org.junit.jupiter.api.Assertions.assertEquals("Store B", unchangedSecond.storeName());
            org.junit.jupiter.api.Assertions.assertEquals(0L, unchangedSecond.rowVersion());
            org.junit.jupiter.api.Assertions
                .assertThrows(OnboardingDraftConflictException.class, () -> operatingPlatformService
                    .update(tenantId, merchantAgentUserId, merchantId, draft.draft().applicationId(), first
                        .id(), "Stale", null, "STORE-A", OperatingPlatform.CertificationStatus.UNVERIFIED, 0L, "127.0.0.1"));

            KycAttachment firstProof = kycAttachmentRepository.insert(new KycAttachmentDraft(tenantId, draft.draft()
                .kycVersionId(), "STORE_QR", "private|platform/store-a", "store-a.png", "png", "image/png", "image/png", 10L, "4"
                    .repeat(64), KycAttachmentScanStatus.CLEAN, KycAttachmentValidationStatus.VALID, 10, baseTime));
            KycAttachment secondProof = kycAttachmentRepository.insert(new KycAttachmentDraft(tenantId, draft.draft()
                .kycVersionId(), "PLATFORM_CASH_FLOW", "private|platform/store-b", "store-b.pdf", "pdf", "application/pdf", "application/pdf", 10L, "5"
                    .repeat(64), KycAttachmentScanStatus.UNAVAILABLE, KycAttachmentValidationStatus.QUARANTINED, 11, baseTime));
            OperatingPlatform firstWithProof = operatingPlatformService
                .linkProof(tenantId, merchantAgentUserId, merchantId, draft.draft().applicationId(), first
                    .id(), firstProof.id(), "STORE_QR", "127.0.0.1");
            OperatingPlatform secondWithProof = operatingPlatformService
                .linkProof(tenantId, merchantAgentUserId, merchantId, draft.draft().applicationId(), second
                    .id(), secondProof.id(), "PLATFORM_CASH_FLOW", "127.0.0.1");
            org.junit.jupiter.api.Assertions.assertEquals(List.of(firstProof.id()), firstWithProof.proofAttachments()
                .stream()
                .map(OperatingPlatform.ProofAttachment::attachmentId)
                .toList());
            org.junit.jupiter.api.Assertions.assertEquals(List.of(secondProof.id()), secondWithProof.proofAttachments()
                .stream()
                .map(OperatingPlatform.ProofAttachment::attachmentId)
                .toList());
            org.junit.jupiter.api.Assertions.assertEquals("UNAVAILABLE", secondWithProof.proofAttachments()
                .get(0)
                .scanStatus());
            org.junit.jupiter.api.Assertions.assertThrows(MerchantDomainException.class, () -> operatingPlatformService
                .linkProof(tenantId, merchantAgentUserId, merchantId, draft.draft().applicationId(), second
                    .id(), firstProof.id(), "STORE_QR", "127.0.0.1"));
            org.junit.jupiter.api.Assertions.assertThrows(MerchantDomainException.class, () -> operatingPlatformService
                .linkProof(tenantId, merchantAgentUserId, merchantId, draft.draft().applicationId(), first
                    .id(), secondProof.id(), "STORE_QR", "127.0.0.1"));
            org.junit.jupiter.api.Assertions.assertEquals(2, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM biz_security_audit
                WHERE tenant_id = ? AND action = 'OPERATING_PLATFORM_PROOF_LINK'
                """, Integer.class, tenantId));
        });
    }

    protected void verifyOnboardingFinalPreview() {
        long tenantId = 929L;
        long rootAgentId = 92901L;
        long merchantAgentId = 92902L;
        long siblingAgentId = 92903L;
        long rootUserId = 92911L;
        long merchantAgentUserId = 92912L;
        long siblingAgentUserId = 92913L;
        long merchantId = 929101L;
        long otherMerchantId = 929102L;

        TenantUtils.execute(tenantId, () -> {
            agentHierarchyService.register(registration(rootAgentId, tenantId, 0L, rootUserId, "PREVIEW-ROOT"));
            agentHierarchyService
                .register(registration(merchantAgentId, tenantId, rootAgentId, merchantAgentUserId, "PREVIEW-MERCHANT"));
            agentHierarchyService
                .register(registration(siblingAgentId, tenantId, rootAgentId, siblingAgentUserId, "PREVIEW-SIBLING"));
            merchantMasterService
                .register(rootUserId, merchantRegistration(merchantId, tenantId, merchantAgentId, 929201L, 929202L, "PREVIEW-MERCHANT", "a"
                    .repeat(64)));
            merchantMasterService
                .register(rootUserId, merchantRegistration(otherMerchantId, tenantId, siblingAgentId, 929203L, 929204L, "PREVIEW-OTHER", "b"
                    .repeat(64)));
            LocalDateTime baseTime = LocalDateTime.of(2026, 8, 20, 20, 0);
            insertPricingVersion(tenantId, merchantAgentId, 929501L, 1, "CHANNEL-V", "PRODUCT-V", "0.02000000", "2.00", "0.50000000", baseTime);
            jdbcTemplate.update("""
                INSERT INTO biz_agent_merchant_default_version
                (id, tenant_id, agent_id, version_no, default_payload_json, effective_time, status,
                 create_user, create_time, deleted)
                VALUES (?, ?, ?, 1, ?, ?, 'PUBLISHED', ?, ?, 0)
                """, 929601L, tenantId, merchantAgentId, """
                {"products":[
                  {"channelCode":"CHANNEL-V","productCode":"PRODUCT-V","pricingVersionId":929501}
                ]}
                """, baseTime, rootUserId, baseTime);
            insertChannelProductVersion(929701L, tenantId, "CHANNEL-V", "PRODUCT-V", "CFG-V-1", "REQ-V-1", "[\"ENTERPRISE\"]", "ENABLED", baseTime);
            OnboardingDraftView draft = onboardingDraftService
                .createOrLoad(tenantId, merchantAgentUserId, merchantId, "CHANNEL-V", "PRODUCT-V", "127.0.0.1");
            KycProfileSaveCommand profileCommand = validProfileCommand(tenantId, merchantAgentUserId, merchantId, draft
                .draft()
                .applicationId(), 0L);
            kycProfileService.save(profileCommand);

            org.mockito.Mockito.when(settlementAccountVerificationPort.verify(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new SettlementAccountVerificationPort.VerificationResult(SettlementAccountVerificationPort.SettlementVerificationStatus.VERIFIED, "VERIFY-929-1", "SYNTHETIC-V1"));
            settlementAccountService
                .save(new SettlementAccountSaveCommand(tenantId, merchantAgentUserId, merchantId, draft.draft()
                    .applicationId(), SettlementAccountVerificationPort.SettlementMode.ORDINARY, "Profile Legal Subject", "BANK-929", "Preview Branch", "6222020200000929", 1L, "127.0.0.1"));

            KycAttachment license = kycAttachmentRepository.insert(new KycAttachmentDraft(tenantId, draft.draft()
                .kycVersionId(), "BUSINESS_LICENSE", "private|preview/license", "license.png", "png", "image/png", "image/png", 10L, "1"
                    .repeat(64), KycAttachmentScanStatus.CLEAN, KycAttachmentValidationStatus.VALID, 1, baseTime));
            KycAttachment legalFront = kycAttachmentRepository.insert(new KycAttachmentDraft(tenantId, draft.draft()
                .kycVersionId(), "LEGAL_REPRESENTATIVE_ID_FRONT", "private|preview/legal-front", "legal-front.png", "png", "image/png", "image/png", 10L, "2"
                    .repeat(64), KycAttachmentScanStatus.CLEAN, KycAttachmentValidationStatus.VALID, 2, baseTime));
            KycAttachment firstProof = kycAttachmentRepository.insert(new KycAttachmentDraft(tenantId, draft.draft()
                .kycVersionId(), "SUPPLEMENT", "private|preview/store-a", "store-a.png", "png", "image/png", "image/png", 10L, "3"
                    .repeat(64), KycAttachmentScanStatus.CLEAN, KycAttachmentValidationStatus.VALID, 3, baseTime));
            KycAttachment secondProof = kycAttachmentRepository.insert(new KycAttachmentDraft(tenantId, draft.draft()
                .kycVersionId(), "SUPPLEMENT", "private|preview/store-b", "store-b.png", "png", "image/png", "image/png", 10L, "4"
                    .repeat(64), KycAttachmentScanStatus.CLEAN, KycAttachmentValidationStatus.VALID, 4, baseTime));
            OperatingPlatform first = operatingPlatformService.create(tenantId, merchantAgentUserId, merchantId, draft
                .draft()
                .applicationId(), "TAOBAO", "Preview Store A", "https://preview-a.example.com", "PREVIEW-A", OperatingPlatform.CertificationStatus.CERTIFIED, "127.0.0.1");
            OperatingPlatform second = operatingPlatformService.create(tenantId, merchantAgentUserId, merchantId, draft
                .draft()
                .applicationId(), "DOUYIN", "Preview Store B", "https://preview-b.example.com", "PREVIEW-B", OperatingPlatform.CertificationStatus.CERTIFIED, "127.0.0.1");
            operatingPlatformService.linkProof(tenantId, merchantAgentUserId, merchantId, draft.draft()
                .applicationId(), first.id(), firstProof.id(), "SUPPLEMENT", "127.0.0.1");
            operatingPlatformService.linkProof(tenantId, merchantAgentUserId, merchantId, draft.draft()
                .applicationId(), second.id(), secondProof.id(), "SUPPLEMENT", "127.0.0.1");
            onboardingDraftService.saveProgress(tenantId, merchantAgentUserId, merchantId, draft.draft()
                .applicationId(), 5, List.of(1, 2, 3, 4, 5), 2L, "127.0.0.1");

            int workflowBefore = jdbcTemplate
                .queryForObject("SELECT COUNT(*) FROM biz_workflow_instance WHERE tenant_id = ?", Integer.class, tenantId);
            int outboxBefore = jdbcTemplate
                .queryForObject("SELECT COUNT(*) FROM biz_outbox_event WHERE tenant_id = ?", Integer.class, tenantId);
            int channelBefore = jdbcTemplate
                .queryForObject("SELECT COUNT(*) FROM biz_channel_event WHERE tenant_id = ?", Integer.class, tenantId);
            OnboardingFinalPreview ready = onboardingFinalPreviewService
                .preview(tenantId, merchantAgentUserId, merchantId, draft.draft().applicationId());
            org.junit.jupiter.api.Assertions.assertTrue(ready.readyForSubmission());
            org.junit.jupiter.api.Assertions.assertTrue(ready.blockers().isEmpty());
            org.junit.jupiter.api.Assertions.assertEquals(draft.draft().kycVersionId(), ready.kyc().kycVersionId());
            org.junit.jupiter.api.Assertions.assertEquals(929501L, ready.pricing().pricingVersionId());
            org.junit.jupiter.api.Assertions.assertEquals("913***********0Y92", ready.kyc().legalIdentifierMasked());
            org.junit.jupiter.api.Assertions.assertEquals("6222********0929", ready.settlement().accountNumberMasked());
            org.junit.jupiter.api.Assertions.assertEquals(List.of("TAOBAO", "DOUYIN"), ready.operatingPlatforms()
                .stream()
                .map(OnboardingFinalPreview.OperatingPlatformSummary::platformCode)
                .toList());
            String responseJson = org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> applicationContext
                .getBean(com.fasterxml.jackson.databind.ObjectMapper.class)
                .writeValueAsString(ready));
            org.junit.jupiter.api.Assertions.assertFalse(responseJson.contains("91350211M000100Y92"));
            org.junit.jupiter.api.Assertions.assertFalse(responseJson.contains("6222020200000929"));
            org.junit.jupiter.api.Assertions.assertFalse(responseJson.contains("13800000001"));
            org.junit.jupiter.api.Assertions.assertFalse(responseJson.contains("private|preview"));

            insertPricingVersion(tenantId, merchantAgentId, 929504L, 2, "CHANNEL-V", "PRODUCT-V", "0.02500000", "2.50", "0.45000000", baseTime
                .plusMinutes(30));
            OnboardingFinalPreview exactVersion = onboardingFinalPreviewService
                .preview(tenantId, merchantAgentUserId, merchantId, draft.draft().applicationId());
            org.junit.jupiter.api.Assertions.assertTrue(exactVersion.readyForSubmission());
            org.junit.jupiter.api.Assertions.assertEquals(929501L, exactVersion.pricing().pricingVersionId());
            org.junit.jupiter.api.Assertions.assertEquals(draft.draft().kycVersionId(), exactVersion.kyc()
                .kycVersionId());

            onboardingFinalPreviewService.preview(tenantId, merchantAgentUserId, merchantId, draft.draft()
                .applicationId());
            org.junit.jupiter.api.Assertions.assertEquals(workflowBefore, jdbcTemplate
                .queryForObject("SELECT COUNT(*) FROM biz_workflow_instance WHERE tenant_id = ?", Integer.class, tenantId));
            org.junit.jupiter.api.Assertions.assertEquals(outboxBefore, jdbcTemplate
                .queryForObject("SELECT COUNT(*) FROM biz_outbox_event WHERE tenant_id = ?", Integer.class, tenantId));
            org.junit.jupiter.api.Assertions.assertEquals(channelBefore, jdbcTemplate
                .queryForObject("SELECT COUNT(*) FROM biz_channel_event WHERE tenant_id = ?", Integer.class, tenantId));
            org.junit.jupiter.api.Assertions
                .assertThrows(MerchantAccessDeniedException.class, () -> onboardingFinalPreviewService
                    .preview(tenantId, siblingAgentUserId, merchantId, draft.draft().applicationId()));
            org.junit.jupiter.api.Assertions
                .assertThrows(MerchantAccessDeniedException.class, () -> onboardingFinalPreviewService
                    .preview(tenantId, merchantAgentUserId, otherMerchantId, draft.draft().applicationId()));
            org.junit.jupiter.api.Assertions
                .assertThrows(MerchantAccessDeniedException.class, () -> onboardingFinalPreviewService
                    .preview(tenantId, merchantAgentUserId, merchantId, 929999999L));

            byte[] addressPayload = jdbcTemplate
                .queryForObject("SELECT address_payload_ciphertext FROM biz_kyc_version WHERE tenant_id = ? AND id = ?", byte[].class, tenantId, draft
                    .draft()
                    .kycVersionId());
            jdbcTemplate
                .update("UPDATE biz_kyc_version SET address_payload_ciphertext = NULL WHERE tenant_id = ? AND id = ?", tenantId, draft
                    .draft()
                    .kycVersionId());
            assertPreviewBlocker(tenantId, merchantAgentUserId, merchantId, draft.draft()
                .applicationId(), "KYC_PROFILE_INCOMPLETE");
            jdbcTemplate
                .update("UPDATE biz_kyc_version SET address_payload_ciphertext = ? WHERE tenant_id = ? AND id = ?", addressPayload, tenantId, draft
                    .draft()
                    .kycVersionId());

            jdbcTemplate
                .update("UPDATE biz_kyc_attachment SET scan_status = 'UNAVAILABLE', validation_status = 'QUARANTINED' WHERE tenant_id = ? AND id = ?", tenantId, legalFront
                    .id());
            assertPreviewBlocker(tenantId, merchantAgentUserId, merchantId, draft.draft()
                .applicationId(), "EVIDENCE_INCOMPLETE");
            jdbcTemplate
                .update("UPDATE biz_kyc_attachment SET scan_status = 'CLEAN', validation_status = 'VALID' WHERE tenant_id = ? AND id = ?", tenantId, legalFront
                    .id());

            jdbcTemplate
                .update("UPDATE biz_kyc_version SET settlement_verification_status = 'PENDING', settlement_verified_time = NULL WHERE tenant_id = ? AND id = ?", tenantId, draft
                    .draft()
                    .kycVersionId());
            assertPreviewBlocker(tenantId, merchantAgentUserId, merchantId, draft.draft()
                .applicationId(), "SETTLEMENT_NOT_VERIFIED");
            jdbcTemplate
                .update("UPDATE biz_kyc_version SET settlement_verification_status = 'VERIFIED', settlement_verified_time = ? WHERE tenant_id = ? AND id = ?", baseTime, tenantId, draft
                    .draft()
                    .kycVersionId());

            insertChannelProductVersion(929702L, tenantId, "CHANNEL-V", "PRODUCT-V", "CFG-V-2", "REQ-V-2", "[\"ENTERPRISE\"]", "ENABLED", baseTime
                .plusHours(1));
            assertPreviewBlocker(tenantId, merchantAgentUserId, merchantId, draft.draft()
                .applicationId(), "REQUIREMENTS_CHANGED");

            insertChannelProductVersion(929703L, tenantId, "CHANNEL-V", "PRODUCT-V", "CFG-V-3", "REQ-V-3", "[\"ENTERPRISE\"]", "DISABLED", baseTime
                .plusHours(2));
            assertPreviewBlocker(tenantId, merchantAgentUserId, merchantId, draft.draft()
                .applicationId(), "CHANNEL_INELIGIBLE");

            insertPricingVersion(tenantId, rootAgentId, 929502L, 2, "CHANNEL-V", "PRODUCT-V", "0.03000000", "3.00", "0.40000000", baseTime
                .plusHours(3));
            assertPreviewBlocker(tenantId, merchantAgentUserId, merchantId, draft.draft()
                .applicationId(), "PRICING_INVALID");

            jdbcTemplate
                .update("UPDATE biz_kyc_version SET license_expiry_date = ? WHERE tenant_id = ? AND id = ?", LocalDate
                    .of(2026, 8, 21), tenantId, draft.draft().kycVersionId());
            assertPreviewBlocker(tenantId, merchantAgentUserId, merchantId, draft.draft()
                .applicationId(), "LICENSE_EXPIRED");
            jdbcTemplate
                .update("UPDATE biz_kyc_version SET license_expiry_date = ? WHERE tenant_id = ? AND id = ?", LocalDate
                    .of(2030, 1, 1), tenantId, draft.draft().kycVersionId());

            jdbcTemplate
                .update("UPDATE biz_kyc_attachment SET scan_status = 'UNAVAILABLE', validation_status = 'QUARANTINED' WHERE tenant_id = ? AND id = ?", tenantId, firstProof
                    .id());
            OnboardingFinalPreview incompletePlatform = assertPreviewBlocker(tenantId, merchantAgentUserId, merchantId, draft
                .draft()
                .applicationId(), "PLATFORM_PROOF_INCOMPLETE");
            org.junit.jupiter.api.Assertions.assertTrue(incompletePlatform.operatingPlatforms()
                .stream()
                .filter(platform -> "TAOBAO".equals(platform.platformCode()))
                .noneMatch(OnboardingFinalPreview.OperatingPlatformSummary::complete));
            org.junit.jupiter.api.Assertions.assertTrue(incompletePlatform.operatingPlatforms()
                .stream()
                .filter(platform -> "DOUYIN".equals(platform.platformCode()))
                .allMatch(OnboardingFinalPreview.OperatingPlatformSummary::complete));
            org.junit.jupiter.api.Assertions.assertNotNull(license);
        });
    }

    protected void verifyIdempotentOnboardingSubmission() throws Exception {
        long tenantId = 930L;
        long rootAgentId = 1930001L;
        long merchantAgentId = 1930002L;
        long rootUserId = 93011L;
        long merchantAgentUserId = 93012L;
        long merchantId = 930101L;
        long incompleteMerchantId = 930102L;
        OnboardingSubmissionCommand[] commandRef = new OnboardingSubmissionCommand[1];
        OnboardingDraftView[] draftRef = new OnboardingDraftView[1];

        TenantUtils.execute(tenantId, () -> {
            agentHierarchyService.register(registration(rootAgentId, tenantId, 0L, rootUserId, "SUBMIT-ROOT"));
            agentHierarchyService
                .register(registration(merchantAgentId, tenantId, rootAgentId, merchantAgentUserId, "SUBMIT-MERCHANT"));
            merchantMasterService
                .register(rootUserId, merchantRegistration(merchantId, tenantId, merchantAgentId, 930201L, 930202L, "SUBMIT-MERCHANT", "c"
                    .repeat(64)));
            merchantMasterService
                .register(rootUserId, merchantRegistration(incompleteMerchantId, tenantId, merchantAgentId, 930203L, 930204L, "SUBMIT-INCOMPLETE", "d"
                    .repeat(64)));
            LocalDateTime baseTime = LocalDateTime.of(2026, 8, 20, 21, 0);
            insertPricingVersion(tenantId, merchantAgentId, 930501L, 1, "CHANNEL-U", "PRODUCT-U", "0.02000000", "2.00", "0.50000000", baseTime);
            jdbcTemplate.update("""
                INSERT INTO biz_agent_merchant_default_version
                (id, tenant_id, agent_id, version_no, default_payload_json, effective_time, status,
                 create_user, create_time, deleted)
                VALUES (?, ?, ?, 1, ?, ?, 'PUBLISHED', ?, ?, 0)
                """, 930601L, tenantId, merchantAgentId, """
                {"products":[
                  {"channelCode":"CHANNEL-U","productCode":"PRODUCT-U","pricingVersionId":930501}
                ]}
                """, baseTime, rootUserId, baseTime);
            insertChannelProductVersion(930701L, tenantId, "CHANNEL-U", "PRODUCT-U", "CFG-U-1", "REQ-U-1", "[\"ENTERPRISE\"]", "ENABLED", baseTime);
            OnboardingDraftView draft = onboardingDraftService
                .createOrLoad(tenantId, merchantAgentUserId, merchantId, "CHANNEL-U", "PRODUCT-U", "127.0.0.1");
            kycProfileService.save(validProfileCommand(tenantId, merchantAgentUserId, merchantId, draft.draft()
                .applicationId(), 0L));
            org.mockito.Mockito.when(settlementAccountVerificationPort.verify(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new SettlementAccountVerificationPort.VerificationResult(SettlementAccountVerificationPort.SettlementVerificationStatus.VERIFIED, "VERIFY-930-1", "SYNTHETIC-V1"));
            settlementAccountService
                .save(new SettlementAccountSaveCommand(tenantId, merchantAgentUserId, merchantId, draft.draft()
                    .applicationId(), SettlementAccountVerificationPort.SettlementMode.ORDINARY, "Profile Legal Subject", "BANK-930", "Submission Branch", "6222020200000930", 1L, "127.0.0.1"));
            kycAttachmentRepository.insert(new KycAttachmentDraft(tenantId, draft.draft()
                .kycVersionId(), "BUSINESS_LICENSE", "private|submit/license", "license.png", "png", "image/png", "image/png", 10L, "5"
                    .repeat(64), KycAttachmentScanStatus.CLEAN, KycAttachmentValidationStatus.VALID, 1, baseTime));
            kycAttachmentRepository.insert(new KycAttachmentDraft(tenantId, draft.draft()
                .kycVersionId(), "LEGAL_REPRESENTATIVE_ID_FRONT", "private|submit/legal-front", "legal-front.png", "png", "image/png", "image/png", 10L, "6"
                    .repeat(64), KycAttachmentScanStatus.CLEAN, KycAttachmentValidationStatus.VALID, 2, baseTime));
            KycAttachment platformProof = kycAttachmentRepository.insert(new KycAttachmentDraft(tenantId, draft.draft()
                .kycVersionId(), "SUPPLEMENT", "private|submit/platform", "platform.png", "png", "image/png", "image/png", 10L, "7"
                    .repeat(64), KycAttachmentScanStatus.CLEAN, KycAttachmentValidationStatus.VALID, 3, baseTime));
            OperatingPlatform platform = operatingPlatformService
                .create(tenantId, merchantAgentUserId, merchantId, draft.draft()
                    .applicationId(), "TAOBAO", "Submission Store", "https://submit.example.com", "SUBMIT-STORE", OperatingPlatform.CertificationStatus.CERTIFIED, "127.0.0.1");
            operatingPlatformService.linkProof(tenantId, merchantAgentUserId, merchantId, draft.draft()
                .applicationId(), platform.id(), platformProof.id(), "SUPPLEMENT", "127.0.0.1");
            onboardingDraftService.saveProgress(tenantId, merchantAgentUserId, merchantId, draft.draft()
                .applicationId(), 5, List.of(1, 2, 3, 4, 5), 2L, "127.0.0.1");
            draftRef[0] = draft;
            commandRef[0] = new OnboardingSubmissionCommand(tenantId, merchantAgentUserId, merchantId, draft.draft()
                .applicationId(), 3L, "submit-930-idempotency", "trace-930", "127.0.0.1");

            OnboardingDraftView incompleteDraft = onboardingDraftService
                .createOrLoad(tenantId, merchantAgentUserId, incompleteMerchantId, "CHANNEL-U", "PRODUCT-U", "127.0.0.1");
            org.junit.jupiter.api.Assertions
                .assertThrows(OnboardingSubmissionBlockedException.class, () -> onboardingSubmissionService
                    .submit(new OnboardingSubmissionCommand(tenantId, merchantAgentUserId, incompleteMerchantId, incompleteDraft
                        .draft()
                        .applicationId(), 0L, "submit-930-incomplete", "trace-930-incomplete", "127.0.0.1")));
            org.junit.jupiter.api.Assertions.assertEquals(0, jdbcTemplate
                .queryForObject("SELECT COUNT(*) FROM biz_outbox_event WHERE tenant_id = ?", Integer.class, tenantId));
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<OnboardingSubmissionResult> concurrent;
        try {
            List<Future<OnboardingSubmissionResult>> futures = List.of(executor
                .submit(() -> submitConcurrentOnboarding(ready, start, commandRef[0])), executor
                    .submit(() -> submitConcurrentOnboarding(ready, start, commandRef[0])));
            org.junit.jupiter.api.Assertions.assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            concurrent = futures.stream().map(future -> {
                try {
                    return future.get(30, TimeUnit.SECONDS);
                } catch (Exception ex) {
                    throw new AssertionError(ex);
                }
            }).toList();
        } finally {
            executor.shutdownNow();
        }
        org.junit.jupiter.api.Assertions.assertEquals(concurrent.get(0).applicationId(), concurrent.get(1)
            .applicationId());
        org.junit.jupiter.api.Assertions.assertEquals(concurrent.get(0).workflowRequest().eventId(), concurrent.get(1)
            .workflowRequest()
            .eventId());
        org.junit.jupiter.api.Assertions.assertEquals(4L, concurrent.get(0).businessVersion());

        TenantUtils.execute(tenantId, () -> {
            OnboardingSubmissionResult repeated = onboardingSubmissionService.submit(commandRef[0]);
            org.junit.jupiter.api.Assertions.assertEquals(concurrent.get(0).workflowRequest().eventId(), repeated
                .workflowRequest()
                .eventId());
            long submittedTimeDifferenceNanos = Math.abs(java.time.Duration.between(concurrent.get(0)
                .submittedTime(), repeated.submittedTime()).toNanos());
            org.junit.jupiter.api.Assertions.assertTrue(submittedTimeDifferenceNanos <= TimeUnit.MILLISECONDS
                .toNanos(1));
            org.junit.jupiter.api.Assertions
                .assertThrows(MerchantDomainException.class, () -> onboardingSubmissionService
                    .submit(new OnboardingSubmissionCommand(tenantId, merchantAgentUserId, merchantId, draftRef[0]
                        .draft()
                        .applicationId(), 3L, "submit-930-different", "trace-930-other", "127.0.0.1")));
            org.junit.jupiter.api.Assertions.assertEquals("SUBMITTED", jdbcTemplate
                .queryForObject("SELECT status FROM biz_onboarding_application WHERE tenant_id = ? AND id = ?", String.class, tenantId, draftRef[0]
                    .draft()
                    .applicationId()));
            org.junit.jupiter.api.Assertions.assertEquals("submit-930-idempotency", jdbcTemplate
                .queryForObject("SELECT idempotency_key FROM biz_onboarding_application WHERE tenant_id = ? AND id = ?", String.class, tenantId, draftRef[0]
                    .draft()
                    .applicationId()));
            org.junit.jupiter.api.Assertions.assertEquals("SUBMITTED", jdbcTemplate
                .queryForObject("SELECT status FROM biz_kyc_version WHERE tenant_id = ? AND id = ?", String.class, tenantId, draftRef[0]
                    .draft()
                    .kycVersionId()));
            org.junit.jupiter.api.Assertions.assertEquals(4L, jdbcTemplate
                .queryForObject("SELECT row_version FROM biz_kyc_version WHERE tenant_id = ? AND id = ?", Long.class, tenantId, draftRef[0]
                    .draft()
                    .kycVersionId()));
            org.junit.jupiter.api.Assertions.assertNotNull(jdbcTemplate
                .queryForObject("SELECT frozen_time FROM biz_kyc_version WHERE tenant_id = ? AND id = ?", LocalDateTime.class, tenantId, draftRef[0]
                    .draft()
                    .kycVersionId()));
            org.junit.jupiter.api.Assertions.assertEquals(1, jdbcTemplate
                .queryForObject("SELECT COUNT(*) FROM biz_outbox_event WHERE tenant_id = ? AND aggregate_id = ?", Integer.class, tenantId, draftRef[0]
                    .draft()
                    .applicationId()));
            org.junit.jupiter.api.Assertions.assertEquals(1, jdbcTemplate
                .queryForObject("SELECT COUNT(*) FROM biz_security_audit WHERE tenant_id = ? AND action = 'ONBOARDING_SUBMIT' AND object_id = ?", Integer.class, tenantId, draftRef[0]
                    .draft()
                    .applicationId()));
            String payload = jdbcTemplate
                .queryForObject("SELECT payload_json FROM biz_outbox_event WHERE tenant_id = ? AND aggregate_id = ?", String.class, tenantId, draftRef[0]
                    .draft()
                    .applicationId());
            org.junit.jupiter.api.Assertions.assertNotNull(payload);
            org.junit.jupiter.api.Assertions.assertTrue(payload.contains("merchant-onboarding-review-v1"));
            org.junit.jupiter.api.Assertions.assertTrue(payload.contains(String.valueOf(draftRef[0].draft()
                .kycVersionId())));
            org.junit.jupiter.api.Assertions.assertFalse(payload.contains("91350211M000100Y92"));
            org.junit.jupiter.api.Assertions.assertFalse(payload.contains("6222020200000930"));
            org.junit.jupiter.api.Assertions.assertFalse(payload.contains("13800000001"));
            org.junit.jupiter.api.Assertions.assertFalse(payload.contains("private|submit"));
            org.junit.jupiter.api.Assertions.assertEquals(0, jdbcTemplate
                .queryForObject("SELECT COUNT(*) FROM biz_workflow_instance WHERE tenant_id = ?", Integer.class, tenantId));
            org.junit.jupiter.api.Assertions.assertThrows(MerchantAccessDeniedException.class, () -> kycProfileService
                .save(validProfileCommand(tenantId, merchantAgentUserId, merchantId, draftRef[0].draft()
                    .applicationId(), 4L)));
            org.junit.jupiter.api.Assertions
                .assertThrows(MerchantAccessDeniedException.class, () -> onboardingDraftService
                    .saveProgress(tenantId, merchantAgentUserId, merchantId, draftRef[0].draft()
                        .applicationId(), 5, List.of(1, 2, 3, 4, 5), 4L, "127.0.0.1"));
        });
    }

    private OnboardingSubmissionResult submitConcurrentOnboarding(CountDownLatch ready,
                                                                  CountDownLatch start,
                                                                  OnboardingSubmissionCommand command) throws InterruptedException {
        ready.countDown();
        start.await();
        OnboardingSubmissionResult[] result = new OnboardingSubmissionResult[1];
        TenantUtils.execute(command.tenantId(), () -> result[0] = onboardingSubmissionService.submit(command));
        return result[0];
    }

    private OnboardingFinalPreview assertPreviewBlocker(Long tenantId,
                                                        Long actorUserId,
                                                        Long merchantId,
                                                        Long applicationId,
                                                        String blockerCode) {
        OnboardingFinalPreview preview = onboardingFinalPreviewService
            .preview(tenantId, actorUserId, merchantId, applicationId);
        org.junit.jupiter.api.Assertions.assertFalse(preview.readyForSubmission());
        org.junit.jupiter.api.Assertions.assertTrue(preview.blockers()
            .stream()
            .anyMatch(blocker -> blockerCode.equals(blocker
                .code())), () -> "Missing blocker " + blockerCode + ": " + preview.blockers());
        return preview;
    }

    private KycProfileSaveCommand validProfileCommand(Long tenantId,
                                                      Long actorUserId,
                                                      Long merchantId,
                                                      Long applicationId,
                                                      Long expectedVersion) {
        List<KycProfileSaveCommand.Person> persons = List
            .of(new KycProfileSaveCommand.Person(KycProfileSaveCommand.PersonRole.LEGAL_REPRESENTATIVE, "Legal Representative", "110101199001011234", "13800000001", LocalDate
                .of(2020, 1, 1), LocalDate
                    .of(2030, 1, 1)), new KycProfileSaveCommand.Person(KycProfileSaveCommand.PersonRole.OPERATOR, "KYC Operator", "110101199002021235", "13800000002", LocalDate
                        .of(2020, 1, 1), LocalDate
                            .of(2030, 1, 1)), new KycProfileSaveCommand.Person(KycProfileSaveCommand.PersonRole.BENEFICIAL_OWNER, "Beneficial Owner", "110101199003031236", "13800000003", LocalDate
                                .of(2020, 1, 1), LocalDate.of(2030, 1, 1)));
        List<KycProfileSaveCommand.Shareholder> shareholders = List
            .of(new KycProfileSaveCommand.Shareholder(KycProfileSaveCommand.ShareholderType.INDIVIDUAL, "Shareholder One", "110101198001011234", new BigDecimal("60.00")), new KycProfileSaveCommand.Shareholder(KycProfileSaveCommand.ShareholderType.CORPORATE, "Corporate Shareholder", "91350211M000100Y43", new BigDecimal("40.00")));
        return new KycProfileSaveCommand(tenantId, actorUserId, merchantId, applicationId, "Profile Legal Subject", "91350211M000100Y92", LocalDate
            .of(2020, 1, 1), LocalDate
                .of(2030, 1, 1), "Technology services", new KycProfileSaveCommand.Address("Registered Address", "Shanghai", "Operating Address"), persons, shareholders, expectedVersion, "127.0.0.1");
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
        insertPricingVersion(tenantId, agentId, pricingVersionId, versionNo, channelCode, productCode, percentageCost, "1.00", "0.50000000", effectiveTime);
    }

    private void insertPricingVersion(Long tenantId,
                                      Long agentId,
                                      Long pricingVersionId,
                                      Integer versionNo,
                                      String channelCode,
                                      String productCode,
                                      String percentageCost,
                                      String fixedFee,
                                      String profitShareRatio,
                                      LocalDateTime effectiveTime) {
        Long parentId = jdbcTemplate.queryForObject("""
            SELECT parent_id FROM biz_agent WHERE tenant_id = ? AND id = ? AND deleted = 0
            """, Long.class, tenantId, agentId);
        if (parentId != null && parentId > 0) {
            Integer parentCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM biz_agent_pricing_version
                WHERE tenant_id = ? AND agent_id = ? AND channel_code = ? AND product_code = ?
                  AND currency = 'CNY' AND deleted = 0
                """, Integer.class, tenantId, parentId, channelCode, productCode);
            if (parentCount != null && parentCount == 0) {
                jdbcTemplate
                    .update("""
                        INSERT INTO biz_agent_pricing_version
                        (id, tenant_id, agent_id, version_no, channel_code, product_code, currency,
                         pricing_rules_json, effective_time, status, create_user, create_time, deleted)
                        VALUES (?, ?, ?, 1, ?, ?, 'CNY',
                                '{"percentageCost":0.00000000,"fixedFee":0.00,"profitShareRatio":1.00000000}',
                                ?, 'PUBLISHED', 1, ?, 0)
                        """, pricingVersionId + 600000000000000000L, tenantId, parentId, channelCode, productCode, effectiveTime, effectiveTime);
            }
        }
        String rules = "{\"percentageCost\":" + percentageCost + ",\"fixedFee\":" + fixedFee + ",\"profitShareRatio\":" + profitShareRatio + "}";
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

    private void insertTenantRole(Long roleId, Long tenantId, String name, String code, LocalDateTime createTime) {
        jdbcTemplate.update("""
            INSERT INTO sys_role
            (id, name, code, data_scope, description, sort, is_system, menu_check_strictly,
             dept_check_strictly, create_user, create_time, deleted, tenant_id)
            VALUES (?, ?, ?, 4, NULL, 1, ?, ?, ?, 1, ?, 0, ?)
            """, roleId, name, code, false, true, true, createTime, tenantId);
        jdbcTemplate.update("""
            INSERT INTO sys_role_menu (role_id, menu_id, tenant_id)
            SELECT ?, role_menu.menu_id, ?
            FROM sys_role_menu role_menu
            JOIN sys_role source_role ON source_role.id = role_menu.role_id
            WHERE source_role.tenant_id = 0 AND source_role.code = ? AND source_role.deleted = 0
            """, roleId, tenantId, code);
    }

    private Integer countMerchantManagementMenus(Long tenantId, String roleCode) {
        return jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM sys_role_menu role_menu
            JOIN sys_role role_data ON role_data.id = role_menu.role_id AND role_data.deleted = 0
            WHERE role_data.tenant_id = ? AND role_data.code = ?
              AND role_menu.menu_id IN (
                690000000000100000, 690000000000100200, 690000000000100201, 690000000000100202,
                690000000000100203, 690000000000100204, 690000000000100205, 690000000000100206,
                690000000000100207
              )
            """, Integer.class, tenantId, roleCode);
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
