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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import top.continew.admin.channel.api.ChannelAdapter;
import top.continew.admin.channel.api.ChannelAdapterRegistry;
import top.continew.admin.channel.api.ChannelApplicationStatePort;
import top.continew.admin.channel.dto.ChannelApplicationState;
import top.continew.admin.channel.dto.ChannelOnboardingState;
import top.continew.admin.channel.dto.ChannelOnboardingSubmitCommand;
import top.continew.admin.channel.dto.ChannelOperationStatus;
import top.continew.admin.channel.dto.ChannelProductKey;
import top.continew.admin.channel.dto.ChannelRef;
import top.continew.admin.channel.dto.ChannelResultMeta;
import top.continew.admin.channel.dto.ChannelStageStatus;
import top.continew.admin.channel.dto.ChannelStateRanks;
import top.continew.admin.channel.dto.ChannelSubmissionResult;
import top.continew.admin.channel.service.ChannelStateMerger;
import top.continew.admin.merchant.master.application.MerchantScopeAuthorizationService;
import top.continew.admin.workflow.api.WorkflowActor;
import top.continew.admin.workflow.api.WorkflowAuthorizationPort;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OnboardingChannelExecutionServiceTest {

    private static final long TENANT_ID = 101;
    private static final long USER_ID = 201;
    private static final long MERCHANT_ID = 301;
    private static final long APPLICATION_ID = 401;
    private static final long KYC_VERSION_ID = 501;

    private EmbeddedDatabase database;
    private ChannelAdapter adapter;
    private ChannelApplicationStatePort statePort;
    private OnboardingChannelExecutionService service;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).build();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(database);
        jdbcTemplate.execute("""
            CREATE TABLE biz_onboarding_application (id BIGINT, application_no VARCHAR(64), tenant_id BIGINT,
                merchant_id BIGINT, channel_code VARCHAR(64), product_code VARCHAR(64), channel_config_version VARCHAR(64),
                requirement_version VARCHAR(64), kyc_version_id BIGINT, status VARCHAR(32), deleted BIGINT)
            """);
        jdbcTemplate.execute("""
            CREATE TABLE biz_kyc_attachment (id BIGINT, tenant_id BIGINT, kyc_version_id BIGINT,
                scan_status VARCHAR(32), validation_status VARCHAR(32), sort INT, deleted BIGINT)
            """);
        jdbcTemplate.update("""
            INSERT INTO biz_onboarding_application VALUES
            (401, 'APP-401', 101, 301, 'SYNTH', 'PAYMENT', 'CFG-1', 'REQ-1', 501, 'APPROVED', 0)
            """);
        jdbcTemplate.update("INSERT INTO biz_kyc_attachment VALUES (601, 101, 501, 'CLEAN', 'VALID', 1, 0)");

        WorkflowAuthorizationPort authorization = mock(WorkflowAuthorizationPort.class);
        when(authorization.requireActor(TENANT_ID, USER_ID)).thenReturn(new WorkflowActor(TENANT_ID, USER_ID, Set
            .of("CHANNEL_OPERATIONS")));
        MerchantScopeAuthorizationService merchantScope = mock(MerchantScopeAuthorizationService.class);
        ChannelAdapterRegistry registry = mock(ChannelAdapterRegistry.class);
        adapter = mock(ChannelAdapter.class);
        when(adapter.channel()).thenReturn(new ChannelRef("SYNTH"));
        when(registry.require("SYNTH")).thenReturn(adapter);
        statePort = mock(ChannelApplicationStatePort.class);
        ChannelProductKey product = new ChannelProductKey("SYNTH", "PAYMENT");
        ChannelOnboardingState initial = new ChannelOnboardingState(ChannelStageStatus.NOT_STARTED, ChannelStageStatus.NOT_STARTED, ChannelStageStatus.NOT_STARTED, ChannelStageStatus.NOT_STARTED, ChannelStageStatus.NOT_STARTED);
        ChannelApplicationState current = new ChannelApplicationState(TENANT_ID, APPLICATION_ID, MERCHANT_ID, 14L, product, "CFG-1", null, initial, ChannelStateRanks
            .initial(), false, 2L);
        when(statePort.lock(TENANT_ID, APPLICATION_ID, product, "CFG-1")).thenReturn(Optional.of(current));
        when(statePort.apply(any(), any(), any(), any(), any())).thenReturn(true);
        service = new OnboardingChannelExecutionService(authorization, merchantScope, registry, statePort, new ChannelStateMerger(), jdbcTemplate);
    }

    @AfterEach
    void tearDown() {
        database.shutdown();
    }

    @Test
    void submitsExactKycAndEvidenceReferencesAndPersistsState() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 29, 2, 0);
        ChannelOnboardingState processing = new ChannelOnboardingState(ChannelStageStatus.PROCESSING, ChannelStageStatus.NOT_STARTED, ChannelStageStatus.NOT_STARTED, ChannelStageStatus.NOT_STARTED, ChannelStageStatus.PROCESSING);
        ChannelResultMeta meta = new ChannelResultMeta(new ChannelProductKey("SYNTH", "PAYMENT"), "CFG-1", "APP-401", "REQ-401", "ACCEPTED", "MAP-1", ChannelOperationStatus.ACCEPTED, "Accepted", now);
        when(adapter.submitOnboarding(any())).thenReturn(new ChannelSubmissionResult(meta, processing));

        var result = service.submit(new OnboardingChannelExecutionService.OnboardingChannelExecutionCommand(TENANT_ID, USER_ID, MERCHANT_ID, APPLICATION_ID, null));

        ArgumentCaptor<ChannelOnboardingSubmitCommand> command = ArgumentCaptor
            .forClass(ChannelOnboardingSubmitCommand.class);
        verify(adapter).submitOnboarding(command.capture());
        assertEquals(KYC_VERSION_ID, command.getValue().kycVersionId());
        assertEquals(List.of(601L), command.getValue().evidenceObjectIds());
        assertEquals(14L, command.getValue().context().businessVersion());
        assertEquals(ChannelOperationStatus.ACCEPTED, result.operationStatus());
        assertEquals(ChannelStageStatus.PROCESSING, result.state().reportingStatus());
        assertFalse(result.finalTerminal());
        verify(statePort).apply(any(), any(), any(), any(), any());
    }
}
