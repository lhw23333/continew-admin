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
import org.springframework.jdbc.core.JdbcTemplate;
import top.continew.admin.system.config.file.FileStorageConfigLoader;
import top.continew.admin.system.config.sms.SmsConfigLoader;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@SpringBootTest
abstract class AbstractApplicationIT {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ProcessEngine processEngine;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

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
}
