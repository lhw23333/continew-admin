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

package top.continew.admin.merchant.onboarding.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.continew.admin.merchant.limit.application.LimitAdjustmentWorkflowBindingService;
import top.continew.admin.merchant.limit.application.LimitAdjustmentWorkflowStartPayload;
import top.continew.admin.merchant.onboarding.application.OnboardingWorkflowStartPayload;
import top.continew.admin.workflow.api.WorkflowOperationException;
import top.continew.admin.workflow.api.WorkflowService;
import top.continew.admin.workflow.command.StartWorkflowCommand;
import top.continew.admin.workflow.dto.WorkflowRef;
import top.continew.starter.extension.tenant.annotation.TenantIgnore;
import top.continew.starter.extension.tenant.util.TenantUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Claims and idempotently delivers workflow commands from the transactional outbox. */
@Service
@RequiredArgsConstructor
@TenantIgnore
public class WorkflowOutboxProcessor {

    public static final String WORKFLOW_START_REQUESTED = "MERCHANT_ONBOARDING_WORKFLOW_START_REQUESTED";
    public static final String LIMIT_WORKFLOW_START_REQUESTED = "MERCHANT_LIMIT_ADJUSTMENT_WORKFLOW_START_REQUESTED";

    private final WorkflowOutboxRepository repository;
    private final WorkflowService workflowService;
    private final WorkflowOutboxPolicy policy;
    private final ObjectMapper objectMapper;
    private final LimitAdjustmentWorkflowBindingService limitWorkflowBindingService;
    private final Clock clock = Clock.systemDefaultZone();
    private final String workerId = "workflow-outbox-" + UUID.randomUUID();

    public WorkflowOutboxBatchResult processAvailable() {
        return processAvailable(null);
    }

    public WorkflowOutboxBatchResult processTenant(Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            return WorkflowOutboxBatchResult.empty();
        }
        WorkflowOutboxBatchResult[] result = {WorkflowOutboxBatchResult.empty()};
        TenantUtils.execute(tenantId, () -> result[0] = processAvailable(tenantId));
        return result[0];
    }

    private WorkflowOutboxBatchResult processAvailable(Long tenantId) {
        if (!policy.isEnabled()) {
            return WorkflowOutboxBatchResult.empty();
        }
        LocalDateTime now = LocalDateTime.now(clock);
        List<WorkflowOutboxEvent> events = repository.claimAvailable(tenantId, workerId, now, now.minus(policy
            .boundedLockTimeout()), policy.boundedBatchSize());
        int published = 0;
        int retried = 0;
        int repairRequired = 0;
        for (WorkflowOutboxEvent event : events) {
            DeliveryResult result = deliver(event);
            if (DeliveryResult.PUBLISHED.equals(result)) {
                published++;
            } else if (DeliveryResult.RETRIED.equals(result)) {
                retried++;
            } else {
                repairRequired++;
            }
        }
        return new WorkflowOutboxBatchResult(events.size(), published, retried, repairRequired);
    }

    public boolean requeueRepair(Long tenantId, Long eventId) {
        if (tenantId == null || tenantId <= 0 || eventId == null || eventId <= 0) {
            return false;
        }
        boolean[] requeued = {false};
        TenantUtils.execute(tenantId, () -> requeued[0] = repository.requeueRepair(tenantId, eventId, LocalDateTime
            .now(clock)));
        return requeued[0];
    }

    private DeliveryResult deliver(WorkflowOutboxEvent event) {
        try {
            if (WORKFLOW_START_REQUESTED.equals(event.eventType())) {
                return deliverOnboarding(event);
            }
            if (LIMIT_WORKFLOW_START_REQUESTED.equals(event.eventType())) {
                return deliverLimitAdjustment(event);
            }
            return markRepair(event, "UNSUPPORTED_EVENT", "Unsupported workflow outbox event");
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            return markRepair(event, "INVALID_EVENT", "Invalid workflow outbox event");
        } catch (WorkflowOperationException ex) {
            Failure failure = workflowFailure(ex);
            return failure.retryable()
                ? markRetry(event, failure.category(), failure.safeMessage())
                : markRepair(event, failure.category(), failure.safeMessage());
        } catch (RuntimeException ex) {
            return markRetry(event, "UNEXPECTED", "Unexpected workflow delivery failure");
        }
    }

    private DeliveryResult deliverOnboarding(WorkflowOutboxEvent event) throws JsonProcessingException {
        OnboardingWorkflowStartPayload payload = objectMapper.readValue(event
            .payloadJson(), OnboardingWorkflowStartPayload.class);
        validate(event, payload);
        WorkflowRef workflow = workflowService.start(new StartWorkflowCommand(event.tenantId(), payload
            .processDefinitionKey(), payload.businessKey(), variables(event, payload)));
        return markPublished(event, workflow);
    }

    private DeliveryResult deliverLimitAdjustment(WorkflowOutboxEvent event) throws JsonProcessingException {
        LimitAdjustmentWorkflowStartPayload payload = objectMapper.readValue(event
            .payloadJson(), LimitAdjustmentWorkflowStartPayload.class);
        validate(event, payload);
        WorkflowRef workflow = workflowService.start(new StartWorkflowCommand(event.tenantId(), payload
            .processDefinitionKey(), payload.businessKey(), variables(event, payload)));
        limitWorkflowBindingService.bind(event.tenantId(), payload, workflow.processInstanceId(), LocalDateTime
            .now(clock));
        return markPublished(event, workflow);
    }

    private DeliveryResult markPublished(WorkflowOutboxEvent event,
                                         WorkflowRef workflow) throws JsonProcessingException {
        String resultHeaders = resultHeaders(workflow);
        return repository.markPublished(event.id(), workerId, resultHeaders, LocalDateTime.now(clock))
            ? DeliveryResult.PUBLISHED
            : DeliveryResult.REPAIR_REQUIRED;
    }

    private void validate(WorkflowOutboxEvent event, OnboardingWorkflowStartPayload payload) {
        if (payload == null || !"ONBOARDING_APPLICATION".equals(event.aggregateType()) || !event.aggregateId()
            .equals(payload.applicationId()) || !event.aggregateVersion().equals(payload.businessVersion()) || payload
                .merchantId() == null || payload.merchantId() <= 0 || payload.owningAgentId() == null || payload
                    .owningAgentId() <= 0 || payload.kycVersionId() == null || payload.kycVersionId() <= 0 || payload
                        .applicantId() == null || payload.applicantId() <= 0 || blank(payload
                            .channelCode()) || blank(payload.processDefinitionKey()) || blank(payload.businessKey())) {
            throw new IllegalArgumentException("Invalid workflow outbox identifiers");
        }
    }

    private Map<String, Object> variables(WorkflowOutboxEvent event, OnboardingWorkflowStartPayload payload) {
        return Map.ofEntries(Map.entry("tenantId", event.tenantId()), Map.entry("merchantId", payload.merchantId()), Map
            .entry("applicationId", payload.applicationId()), Map.entry("kycVersion", payload.businessVersion()), Map
                .entry("channelCode", payload.channelCode()), Map.entry("applicantId", payload.applicantId()), Map
                    .entry("owningAgentId", payload.owningAgentId()), Map.entry("riskLevel", "UNASSESSED"), Map
                        .entry("requiresSupplement", Boolean.FALSE));
    }

    private void validate(WorkflowOutboxEvent event, LimitAdjustmentWorkflowStartPayload payload) {
        if (payload == null || !"LIMIT_ADJUSTMENT".equals(event.aggregateType()) || !event.aggregateId()
            .equals(payload.requestId()) || !event.aggregateVersion().equals(payload.businessVersion()) || payload
                .merchantId() == null || payload.merchantId() <= 0 || payload.owningAgentId() == null || payload
                    .owningAgentId() <= 0 || payload.applicantId() == null || payload
                        .applicantId() <= 0 || blank(payload.channelCode()) || blank(payload
                            .processDefinitionKey()) || blank(payload.businessKey())) {
            throw new IllegalArgumentException("Invalid limit workflow outbox identifiers");
        }
    }

    private Map<String, Object> variables(WorkflowOutboxEvent event, LimitAdjustmentWorkflowStartPayload payload) {
        return Map.ofEntries(Map.entry("tenantId", event.tenantId()), Map.entry("merchantId", payload.merchantId()), Map
            .entry("requestId", payload.requestId()), Map.entry("channelCode", payload.channelCode()), Map
                .entry("applicantId", payload.applicantId()), Map.entry("owningAgentId", payload.owningAgentId()));
    }

    private String resultHeaders(WorkflowRef workflow) throws JsonProcessingException {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mappingId", workflow.mappingId());
        result.put("processInstanceId", workflow.processInstanceId());
        result.put("processDefinitionId", workflow.processDefinitionId());
        result.put("processDefinitionKey", workflow.processDefinitionKey());
        result.put("processDefinitionVersion", workflow.processDefinitionVersion());
        return objectMapper.writeValueAsString(result);
    }

    private DeliveryResult markRetry(WorkflowOutboxEvent event, String category, String safeMessage) {
        int retryCount = event.retryCount() + 1;
        if (retryCount >= policy.boundedMaxRetries()) {
            return markRepair(event, retryCount, category, safeMessage);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        return repository.markRetry(event.id(), workerId, retryCount, now.plus(policy
            .retryDelay(retryCount)), category, safeMessage, now)
                ? DeliveryResult.RETRIED
                : DeliveryResult.REPAIR_REQUIRED;
    }

    private DeliveryResult markRepair(WorkflowOutboxEvent event, String category, String safeMessage) {
        return markRepair(event, event.retryCount() + 1, category, safeMessage);
    }

    private DeliveryResult markRepair(WorkflowOutboxEvent event, int retryCount, String category, String safeMessage) {
        repository.markRepairRequired(event.id(), workerId, retryCount, category, safeMessage, LocalDateTime
            .now(clock));
        return DeliveryResult.REPAIR_REQUIRED;
    }

    private Failure workflowFailure(WorkflowOperationException exception) {
        return switch (exception.code()) {
            case NOT_FOUND -> new Failure("WORKFLOW_NOT_READY", "Workflow definition or scope is not ready", true);
            case ENGINE_FAILURE -> new Failure("WORKFLOW_ENGINE", "Workflow engine is temporarily unavailable", true);
            case MAPPING_CONFLICT ->
                new Failure("WORKFLOW_CONFLICT", "Workflow mapping conflicts with the event", false);
            default -> new Failure("INVALID_WORKFLOW_COMMAND", "Workflow command was rejected", false);
        };
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private enum DeliveryResult { PUBLISHED, RETRIED, REPAIR_REQUIRED }

    private record Failure(String category, String safeMessage, boolean retryable) {
    }
}
