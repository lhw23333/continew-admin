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

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.merchant.agent.application.AgentScopeAuthorizationService;
import top.continew.admin.merchant.master.application.MerchantMasterService;
import top.continew.admin.merchant.master.domain.Merchant;
import top.continew.admin.merchant.master.domain.MerchantDomainException;
import top.continew.admin.merchant.security.audit.application.SecurityAuditWriter;
import top.continew.admin.merchant.security.audit.domain.SecurityAuditRecord;
import top.continew.admin.merchant.security.audit.domain.SecurityAuditResult;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;

/** Creates reference-only re-verification routes that later attach a KYC draft and reuse onboarding BPMN. */
@Service
@RequiredArgsConstructor
public class MerchantReverificationRoutingService {

    public static final String BUSINESS_TYPE = "MERCHANT_REVERIFICATION";
    public static final String PROCESS_KEY = "merchant-onboarding-review-v1";
    public static final String STATUS = "AWAITING_KYC_DRAFT";

    private final MerchantMasterService merchantMasterService;
    private final AgentScopeAuthorizationService agentScopeAuthorizationService;
    private final IdentifierGenerator identifierGenerator;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final SecurityAuditWriter securityAuditWriter;

    @Transactional
    public MerchantReverificationRoute route(Long tenantId,
                                             Long actorUserId,
                                             Long merchantId,
                                             Set<MerchantReverificationChangeType> changeTypes,
                                             Long targetAgentId,
                                             String reason,
                                             String ipAddress) {
        if (changeTypes == null || changeTypes.isEmpty() || reason == null || reason.isBlank()) {
            throw new MerchantDomainException("Re-verification change types and reason are required");
        }
        Merchant merchant = merchantMasterService.requireAccessible(tenantId, actorUserId, merchantId);
        if (changeTypes.contains(MerchantReverificationChangeType.OWNERSHIP)) {
            if (targetAgentId == null || targetAgentId.equals(merchant.owningAgentId())) {
                throw new MerchantDomainException("A different target agent is required for ownership change");
            }
            agentScopeAuthorizationService.requireAccessible(tenantId, actorUserId, targetAgentId);
        } else if (targetAgentId != null) {
            throw new MerchantDomainException("Target agent is only allowed for ownership change");
        }
        Long id = identifierGenerator.nextId(new Object()).longValue();
        String requestNo = "RV" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
            INSERT INTO biz_merchant_reverification_request
            (id, tenant_id, request_no, merchant_id, owning_agent_id, target_agent_id, source_merchant_version,
             change_types_json, reason, business_type, process_definition_key, status, requested_by,
             requested_time, row_version, create_time, deleted)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, 0)
            """, id, tenantId, requestNo, merchantId, merchant.owningAgentId(), targetAgentId, merchant
            .rowVersion(), writeTypes(changeTypes), sanitize(reason), BUSINESS_TYPE, PROCESS_KEY, STATUS, actorUserId, now, now);
        securityAuditWriter.append(new SecurityAuditRecord(tenantId, actorUserId, merchant
            .owningAgentId(), "MERCHANT_REVERIFICATION_ROUTE", "MERCHANT_REVERIFICATION", id, merchant
                .rowVersion(), null, "requestNo=%s;changeTypes=%s"
                    .formatted(requestNo, writeTypes(changeTypes)), ipAddress, SecurityAuditResult.SUCCESS, null, now));
        return new MerchantReverificationRoute(id, requestNo, BUSINESS_TYPE, PROCESS_KEY, STATUS);
    }

    private String writeTypes(Set<MerchantReverificationChangeType> types) {
        try {
            return objectMapper.writeValueAsString(types.stream().sorted(Comparator.comparing(Enum::name)).toList());
        } catch (JsonProcessingException ex) {
            throw new MerchantDomainException("Re-verification change types are invalid");
        }
    }

    private String sanitize(String value) {
        String sanitized = value.replaceAll("[\\p{Cntrl}]", " ")
            .replaceAll("(?<!\\d)\\d{7,}(?!\\d)", "[REDACTED]")
            .replaceAll("\\s+", " ")
            .trim();
        return sanitized.substring(0, Math.min(sanitized.length(), 255));
    }

    public enum MerchantReverificationChangeType { LEGAL_IDENTITY, OWNERSHIP, SETTLEMENT_ACCOUNT }

    public record MerchantReverificationRoute(Long requestId, String requestNo, String businessType,
                                              String processDefinitionKey, String status) {}
}
