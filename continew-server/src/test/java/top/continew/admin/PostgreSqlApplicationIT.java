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
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles({"integration-test", "integration-postgresql"})
class PostgreSqlApplicationIT extends AbstractApplicationIT {

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void representativeQueriesUseCompositeIndexes() {
        seedRepresentativeQueryData();
        jdbcTemplate.execute("ANALYZE biz_merchant");
        jdbcTemplate.execute("ANALYZE biz_onboarding_application");
        jdbcTemplate.execute("ANALYZE biz_outbox_event");
        jdbcTemplate.execute("ANALYZE biz_channel_event");
        jdbcTemplate.execute("SET enable_seqscan = off");
        try {
            assertUsesIndex("idx_merchant_scope_status_time", """
                EXPLAIN (COSTS OFF) SELECT id FROM biz_merchant
                WHERE tenant_id = 18 AND owning_agent_id = 18 AND status = 'ENABLED' AND deleted = 0
                ORDER BY create_time DESC, id DESC LIMIT 20
                """);
            assertUsesIndex("idx_onboarding_scope_status_time", """
                EXPLAIN (COSTS OFF) SELECT id FROM biz_onboarding_application
                WHERE tenant_id = 18 AND owning_agent_id = 18 AND status = 'CHANNEL_PROCESSING'
                ORDER BY submitted_time DESC, id DESC LIMIT 20
                """);
            assertUsesIndex("idx_outbox_status_retry", """
                EXPLAIN (COSTS OFF) SELECT id FROM biz_outbox_event
                WHERE status = 'PENDING' AND next_retry_time <= CURRENT_TIMESTAMP
                ORDER BY next_retry_time, id LIMIT 50
                """);
            assertUsesIndex("idx_channel_event_status_time", """
                EXPLAIN (COSTS OFF) SELECT id FROM biz_channel_event
                WHERE tenant_id = 18 AND channel_code = 'CH2' AND processing_status = 'FAILED'
                ORDER BY received_time DESC, id DESC LIMIT 20
                """);
        } finally {
            jdbcTemplate.execute("SET enable_seqscan = on");
        }
    }

    @Test
    void agentHierarchyUsesTenantBoundClosureScope() {
        verifyAgentHierarchyScope();
    }

    @Test
    void merchantScopeUsesAgentOwnership() {
        verifyMerchantScopeUsesAgentOwnership();
    }

    @Test
    void securityAuditIsAppendOnly() {
        verifySecurityAuditIsAppendOnly();
    }

    @Test
    void kycAttachmentMetadataPersistsWithTenantOwnership() {
        verifyKycAttachmentMetadataPersistence();
    }

    @Test
    void agentQueriesRejectSiblingEnumeration() {
        verifyAgentScopedQueries();
    }

    @Test
    void subordinateAgentProvisioningIsAtomic() {
        verifySubordinateAgentProvisioningIsAtomic();
    }

    @Test
    void promotionCodeOwnershipIsServerResolved() {
        verifyPromotionCodeOwnership();
    }

    @Test
    void agentPricingVersionsAreBoundedAndImmutable() {
        verifyAgentPricingVersions();
    }

    @Test
    void agentMerchantDefaultsAreSnapshottedPerDraft() {
        verifyAgentMerchantDefaults();
    }

    @Test
    void concurrentLegalSubjectCreationIsDeterministic() throws Exception {
        verifyConcurrentLegalSubjectUniqueness();
    }

    @Test
    void merchantProvisioningIsAtomic() {
        verifyMerchantProvisioningIsAtomic();
    }

    @Test
    void merchantQueriesAreScopedAndChannelIndependent() {
        verifyMerchantScopedQueries();
    }

    @Test
    void onboardingChannelsRespectAgentProductsMerchantTypeAndChannelStatus() {
        verifyChannelEligibility();
    }

    @Test
    void onboardingDraftsAreExplicitRecoverableAndOptimisticallyVersioned() {
        verifyOnboardingDraftPersistence();
    }

    @Test
    void historicalKycReuseIsSameMerchantAllowlistedAndRevalidated() {
        verifySameMerchantKycReuse();
    }

    private void assertUsesIndex(String expectedIndex, String explainSql) {
        List<String> plan = jdbcTemplate.queryForList(explainSql, String.class);
        assertTrue(plan.stream()
            .anyMatch(line -> line
                .contains(expectedIndex)), () -> "PostgreSQL plan did not use " + expectedIndex + ": " + plan);
    }
}
