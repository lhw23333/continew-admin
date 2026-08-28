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

package top.continew.admin.merchant.limit.application;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.merchant.limit.domain.LimitAdjustment;
import top.continew.admin.merchant.master.application.MerchantScopeAuthorizationService;
import top.continew.admin.merchant.master.domain.Merchant;
import top.continew.admin.merchant.master.domain.MerchantAccessDeniedException;
import top.continew.admin.merchant.security.audit.application.SecurityAuditWriter;
import top.continew.admin.merchant.security.audit.domain.SecurityAuditRecord;
import top.continew.admin.merchant.security.audit.domain.SecurityAuditResult;
import top.continew.admin.workflow.definition.MerchantLimitAdjustmentWorkflowDefinition;
import top.continew.starter.extension.tenant.context.TenantContextHolder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Creates one active versioned request per merchant/channel/platform dimension and appends immutable evidence. */
@Service
@RequiredArgsConstructor
public class LimitAdjustmentService {

    private final LimitAdjustmentRepository repository;
    private final LimitAdjustmentEligibilityPort eligibilityPort;
    private final MerchantScopeAuthorizationService merchantScopeAuthorizationService;
    private final IdentifierGenerator identifierGenerator;
    private final SecurityAuditWriter securityAuditWriter;
    private final LimitAdjustmentWorkflowOutboxPort workflowOutboxPort;
    private final ObjectMapper objectMapper;
    private final Clock clock = Clock.systemDefaultZone();

    @Transactional
    public LimitAdjustmentCreateResult create(LimitAdjustmentCreateCommand command) {
        requireTenantContext(command == null ? null : command.tenantId());
        NormalizedCommand normalized = normalize(command);
        Merchant merchant = merchantScopeAuthorizationService.requireAccessible(command.tenantId(), command
            .actorUserId(), command.merchantId());
        LimitAdjustment active = repository.findActive(command.tenantId(), command.merchantId(), normalized
            .channelCode(), normalized.platformCode()).orElse(null);
        if (active != null) {
            return new LimitAdjustmentCreateResult(active, false);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LimitAdjustmentEligibility eligibility = eligibilityPort.requireEligible(command
            .tenantId(), merchant, normalized.channelCode(), now);
        BigDecimal originalLimit = repository.findCurrentEffectiveLimit(command.tenantId(), command
            .merchantId(), normalized.channelCode(), normalized.platformCode(), normalized.currency())
            .orElse(BigDecimal.ZERO)
            .setScale(2);
        LimitAdjustmentDraft draft = new LimitAdjustmentDraft(identifierGenerator.nextId(new Object())
            .longValue(), command.tenantId(), requestNo(), command.merchantId(), merchant.owningAgentId(), normalized
                .channelCode(), normalized.platformCode(), normalized.currency(), originalLimit, normalized
                    .requestedLimit(), normalized.normalizedLimit(), normalized.reason(), eligibility
                        .eligibilityVersion(), eligibility.channelConfigVersion(), normalized
                            .amountPolicyVersion(), command.actorUserId(), now);
        LimitAdjustment created;
        try {
            created = repository.insert(draft);
        } catch (LimitAdjustmentConflictException ex) {
            LimitAdjustment concurrent = repository.findActive(command.tenantId(), command.merchantId(), normalized
                .channelCode(), normalized.platformCode()).orElseThrow(() -> ex);
            return new LimitAdjustmentCreateResult(concurrent, false);
        }
        repository.appendHistory(new LimitAdjustmentHistoryDraft(identifierGenerator.nextId(new Object())
            .longValue(), created, "CREATE", command.actorUserId(), now));
        enqueueWorkflow(created, command.actorUserId(), now);
        audit(command, merchant, created);
        return new LimitAdjustmentCreateResult(created, true);
    }

    public List<LimitAdjustmentHistory> history(Long tenantId, Long actorUserId, Long merchantId, Long requestId) {
        requireTenantContext(tenantId);
        merchantScopeAuthorizationService.requireAccessible(tenantId, actorUserId, merchantId);
        repository.findById(tenantId, merchantId, requestId).orElseThrow(MerchantAccessDeniedException::new);
        List<LimitAdjustmentHistory> history = repository.listHistory(tenantId, requestId);
        if (history.isEmpty()) {
            throw new MerchantAccessDeniedException();
        }
        return history;
    }

    private NormalizedCommand normalize(LimitAdjustmentCreateCommand command) {
        if (command.actorUserId() == null || command.actorUserId() <= 0 || command.merchantId() == null || command
            .merchantId() <= 0) {
            throw new IllegalArgumentException("Limit adjustment identity is invalid");
        }
        String channelCode = code(command.channelCode(), "channelCode");
        String platformCode = code(command.platformCode(), "platformCode");
        String currency = code(command.currency(), "currency");
        if (currency.length() != 3) {
            throw new IllegalArgumentException("Limit adjustment currency is invalid");
        }
        BigDecimal requested = amount(command.requestedLimit(), "requestedLimit");
        BigDecimal normalized = amount(command.normalizedLimit(), "normalizedLimit");
        if (normalized.compareTo(requested) < 0) {
            throw new IllegalArgumentException("Normalized limit must not be below requested limit");
        }
        String reason = command.reason() == null ? null : command.reason().trim();
        if (reason == null || reason.isBlank() || reason.length() > 1000 || reason.chars()
            .anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Limit adjustment reason is invalid");
        }
        String amountPolicyVersion = code(command.amountPolicyVersion(), "amountPolicyVersion");
        return new NormalizedCommand(channelCode, platformCode, currency, requested, normalized, amountPolicyVersion, reason);
    }

    private BigDecimal amount(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0 || value.scale() > 2 || value.precision() > 20) {
            throw new IllegalArgumentException("Limit adjustment " + name + " is invalid");
        }
        return value.setScale(2);
    }

    private String code(String value, String name) {
        String normalized = value == null ? null : value.trim().toUpperCase(Locale.ROOT);
        if (normalized == null || !normalized.matches("[A-Z0-9][A-Z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("Limit adjustment " + name + " is invalid");
        }
        return normalized;
    }

    private String requestNo() {
        return "LA" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
    }

    private void enqueueWorkflow(LimitAdjustment request, Long actorUserId, LocalDateTime now) {
        Long businessVersion = request.rowVersion() + 1;
        String businessKey = "%s:MERCHANT_LIMIT_ADJUSTMENT:%s:%s".formatted(request.tenantId(), request
            .id(), businessVersion);
        LimitAdjustmentWorkflowStartPayload payload = new LimitAdjustmentWorkflowStartPayload(request.id(), request
            .merchantId(), request.owningAgentId(), actorUserId, businessVersion, request
                .channelCode(), MerchantLimitAdjustmentWorkflowDefinition.PROCESS_KEY, businessKey);
        workflowOutboxPort.enqueue(new LimitAdjustmentWorkflowRequestDraft(identifierGenerator.nextId(new Object())
            .longValue(), request.tenantId(), request.id(), businessVersion, "LIMIT_WORKFLOW_START:%s:%s"
                .formatted(request.tenantId(), request.id()), json(payload), null, now));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Limit adjustment workflow payload serialization failed");
        }
    }

    private void audit(LimitAdjustmentCreateCommand command, Merchant merchant, LimitAdjustment request) {
        String reason = "channel=%s;platform=%s;original=%s;requested=%s;normalized=%s;policy=%s;eligibility=%s;config=%s"
            .formatted(request.channelCode(), request.platformCode(), request.originalLimit(), request
                .requestedLimit(), request.normalizedLimit(), request.amountPolicyVersion(), request
                    .eligibilityVersion(), request.channelConfigVersion());
        securityAuditWriter.append(new SecurityAuditRecord(command.tenantId(), command.actorUserId(), merchant
            .owningAgentId(), "LIMIT_ADJUSTMENT_CREATE", "LIMIT_ADJUSTMENT", request.id(), request
                .rowVersion(), null, reason, command.ipAddress(), SecurityAuditResult.SUCCESS, null, request
                    .applicationTime()));
    }

    private void requireTenantContext(Long tenantId) {
        if (tenantId == null || !tenantId.equals(TenantContextHolder.getTenantId())) {
            throw new MerchantAccessDeniedException();
        }
    }

    private record NormalizedCommand(String channelCode, String platformCode, String currency,
                                     BigDecimal requestedLimit, BigDecimal normalizedLimit, String amountPolicyVersion,
                                     String reason) {
    }
}
