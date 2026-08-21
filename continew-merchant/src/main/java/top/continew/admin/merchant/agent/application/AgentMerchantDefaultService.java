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

package top.continew.admin.merchant.agent.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.merchant.agent.domain.Agent;
import top.continew.admin.merchant.agent.domain.AgentDomainException;
import top.continew.admin.merchant.agent.domain.AgentMerchantDefaultProduct;
import top.continew.admin.merchant.agent.domain.AgentMerchantDefaultStatus;
import top.continew.admin.merchant.agent.domain.AgentMerchantDefaultVersion;
import top.continew.admin.merchant.agent.domain.AgentMerchantDefaults;
import top.continew.admin.merchant.agent.domain.AgentPricingBoundaryException;
import top.continew.admin.merchant.agent.domain.AgentPricingVersion;
import top.continew.admin.merchant.agent.domain.KycDraftDefaultSnapshot;
import top.continew.admin.merchant.security.audit.application.SecurityAuditWriter;
import top.continew.admin.merchant.security.audit.domain.SecurityAuditRecord;
import top.continew.admin.merchant.security.audit.domain.SecurityAuditResult;
import top.continew.starter.extension.tenant.context.TenantContextHolder;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Publishes immutable defaults and copies the effective value into each draft once. */
@Service
@RequiredArgsConstructor
public class AgentMerchantDefaultService {

    private final AgentPricingRepository pricingRepository;
    private final AgentMerchantDefaultRepository defaultRepository;
    private final KycDraftDefaultSnapshotRepository snapshotRepository;
    private final AgentScopeAuthorizationService scopeAuthorizationService;
    private final SecurityAuditWriter securityAuditWriter;
    private final Clock clock = Clock.systemDefaultZone();

    @Transactional
    public AgentMerchantDefaultVersion create(AgentMerchantDefaultCreateCommand command) {
        requireTenantContext(command.tenantId());
        AgentScopeAuthorizationService.AgentScope scope = scopeAuthorizationService.requireAccessible(command
            .tenantId(), command.actorUserId(), command.agentId());
        if (!scope.target().isEnabled()) {
            throw new AgentDomainException("Disabled agent cannot receive new merchant defaults");
        }
        AgentMerchantDefaults defaults = validatedDefaults(command.tenantId(), scope.target(), command
            .products(), command.effectiveTime());
        AgentMerchantDefaultVersion previous = defaultRepository.findEffective(command.tenantId(), command
            .agentId(), command.effectiveTime()).orElse(null);
        int versionNo = defaultRepository.nextVersionNo(command.tenantId(), command.agentId());
        AgentMerchantDefaultVersion created = defaultRepository.insert(new AgentMerchantDefaultVersionDraft(command
            .tenantId(), command.agentId(), versionNo, defaults, command.effectiveTime(), command
                .expiresTime(), AgentMerchantDefaultStatus.PUBLISHED, command.actorUserId(), LocalDateTime.now(clock)));
        auditVersion(command, scope.actor().id(), previous, created);
        return created;
    }

    public List<AgentMerchantDefaultVersion> list(Long tenantId, Long actorUserId, Long agentId) {
        requireTenantContext(tenantId);
        scopeAuthorizationService.requireAccessible(tenantId, actorUserId, agentId);
        return defaultRepository.list(tenantId, agentId);
    }

    @Transactional
    public Optional<KycDraftDefaultSnapshot> inheritIntoDraft(Long tenantId,
                                                              Long actorUserId,
                                                              Long kycVersionId,
                                                              String ipAddress) {
        requireTenantContext(tenantId);
        KycDraftDefaultContext context = snapshotRepository.findDraftContext(tenantId, kycVersionId)
            .orElseThrow(() -> new AgentDomainException("KYC draft is not available"));
        AgentScopeAuthorizationService.AgentScope scope = scopeAuthorizationService
            .requireAccessible(tenantId, actorUserId, context.owningAgentId());
        if (!"DRAFT".equals(context.status())) {
            throw new AgentDomainException("Agent defaults can only be inherited into a draft KYC version");
        }
        Optional<KycDraftDefaultSnapshot> existing = snapshotRepository.findByKycVersionId(tenantId, kycVersionId);
        if (existing.isPresent()) {
            return existing;
        }
        LocalDateTime copiedTime = LocalDateTime.now(clock);
        AgentMerchantDefaultVersion effective = defaultRepository.findEffective(tenantId, context
            .owningAgentId(), copiedTime).orElse(null);
        if (effective == null) {
            return Optional.empty();
        }
        Agent target = scope.target();
        AgentMerchantDefaults copiedDefaults = validatedDefaults(tenantId, target, effective.defaults()
            .products(), copiedTime);
        KycDraftDefaultSnapshot snapshot = snapshotRepository
            .insert(new KycDraftDefaultSnapshotDraft(tenantId, kycVersionId, effective
                .id(), copiedDefaults, copiedTime, actorUserId, copiedTime));
        auditInheritance(tenantId, actorUserId, scope.actor().id(), context, effective, snapshot, ipAddress);
        return Optional.of(snapshot);
    }

    private AgentMerchantDefaults validatedDefaults(Long tenantId,
                                                    Agent target,
                                                    List<AgentMerchantDefaultProduct> products,
                                                    LocalDateTime effectiveAt) {
        List<AgentMerchantDefaultProduct> validated = products.stream()
            .map(product -> validateProduct(tenantId, target, product, effectiveAt))
            .sorted(Comparator.comparing(AgentMerchantDefaultProduct::channelCode)
                .thenComparing(AgentMerchantDefaultProduct::productCode))
            .toList();
        return new AgentMerchantDefaults(validated);
    }

    private AgentMerchantDefaultProduct validateProduct(Long tenantId,
                                                        Agent target,
                                                        AgentMerchantDefaultProduct product,
                                                        LocalDateTime effectiveAt) {
        AgentPricingVersion pricing = pricingRepository.findById(tenantId, product.pricingVersionId())
            .orElseThrow(() -> new AgentPricingBoundaryException("Referenced pricing version is not available"));
        if (!target.id().equals(pricing.agentId()) || !product.channelCode().equals(pricing.channelCode()) || !product
            .productCode()
            .equals(pricing.productCode()) || !pricing.isEffectiveAt(effectiveAt)) {
            throw new AgentPricingBoundaryException("Referenced pricing version is outside the agent default scope");
        }
        return new AgentMerchantDefaultProduct(pricing.channelCode(), pricing.productCode(), pricing.id());
    }

    private void auditVersion(AgentMerchantDefaultCreateCommand command,
                              Long actorAgentId,
                              AgentMerchantDefaultVersion previous,
                              AgentMerchantDefaultVersion created) {
        String previousId = previous == null ? "none" : previous.id().toString();
        String reason = "previousId=%s;currentId=%s;products=%s;effective=%s;reason=%s".formatted(previousId, created
            .id(), created.defaults().products().size(), created.effectiveTime(), command.reason());
        appendAudit(command.tenantId(), command
            .actorUserId(), actorAgentId, "AGENT_MERCHANT_DEFAULT_VERSION_CREATE", "AGENT_MERCHANT_DEFAULT", created
                .id(), created.versionNo().longValue(), truncate(reason), command.ipAddress());
    }

    private void auditInheritance(Long tenantId,
                                  Long actorUserId,
                                  Long actorAgentId,
                                  KycDraftDefaultContext context,
                                  AgentMerchantDefaultVersion effective,
                                  KycDraftDefaultSnapshot snapshot,
                                  String ipAddress) {
        String reason = "defaultVersionId=%s;snapshotId=%s;merchantId=%s;products=%s".formatted(effective.id(), snapshot
            .id(), context.merchantId(), snapshot.defaults().products().size());
        appendAudit(tenantId, actorUserId, actorAgentId, "KYC_DRAFT_DEFAULTS_INHERIT", "KYC_VERSION", context
            .kycVersionId(), effective.versionNo().longValue(), reason, ipAddress);
    }

    private void appendAudit(Long tenantId,
                             Long actorUserId,
                             Long actorAgentId,
                             String action,
                             String objectType,
                             Long objectId,
                             Long businessVersion,
                             String reason,
                             String ipAddress) {
        securityAuditWriter
            .append(new SecurityAuditRecord(tenantId, actorUserId, actorAgentId, action, objectType, objectId, businessVersion, "MERCHANT_DEFAULTS", truncate(reason), ipAddress, SecurityAuditResult.SUCCESS, null, LocalDateTime
                .now(clock)));
    }

    private String truncate(String value) {
        return value.substring(0, Math.min(value.length(), 255));
    }

    private void requireTenantContext(Long tenantId) {
        if (tenantId == null || !tenantId.equals(TenantContextHolder.getTenantId())) {
            throw new AgentDomainException("Merchant-default tenant scope is not available");
        }
    }
}
