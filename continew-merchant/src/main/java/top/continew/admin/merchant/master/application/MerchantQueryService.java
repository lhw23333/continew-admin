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

package top.continew.admin.merchant.master.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.continew.admin.merchant.agent.application.AgentRepository;
import top.continew.admin.merchant.agent.application.AgentScopeAuthorizationService;
import top.continew.admin.merchant.agent.domain.Agent;
import top.continew.admin.merchant.agent.domain.AgentAccessDeniedException;
import top.continew.admin.merchant.master.domain.MerchantAccessDeniedException;
import top.continew.admin.merchant.master.domain.MerchantStatus;
import top.continew.admin.merchant.security.audit.application.SecurityAuditWriter;
import top.continew.admin.merchant.security.audit.domain.SecurityAuditRecord;
import top.continew.admin.merchant.security.audit.domain.SecurityAuditResult;
import top.continew.starter.extension.tenant.context.TenantContextHolder;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Scope-aware merchant list/detail service with channel-independent master state. */
@Service
@RequiredArgsConstructor
public class MerchantQueryService {

    private final MerchantRepository merchantRepository;
    private final AgentRepository agentRepository;
    private final AgentScopeAuthorizationService agentScopeAuthorizationService;
    private final SecurityAuditWriter securityAuditWriter;
    private final Clock clock = Clock.systemDefaultZone();

    public MerchantPage page(Long tenantId,
                             Long actorUserId,
                             MerchantListQuery query,
                             MerchantActionPermissions permissions) {
        requireTenantContext(tenantId);
        List<Long> authorizedAgentIds = resolveAuthorizedAgentIds(tenantId, actorUserId);
        MerchantQuerySlice slice = merchantRepository.page(tenantId, actorUserId, authorizedAgentIds, query);
        if (slice.list().isEmpty()) {
            return new MerchantPage(List.of(), slice.total(), query.page(), query.size());
        }
        Map<Long, List<MerchantChannelSummary>> channelsByMerchant = merchantRepository
            .listLatestChannelSummaries(tenantId, slice.list().stream().map(MerchantQueryRecord::id).toList())
            .stream()
            .collect(Collectors.groupingBy(MerchantChannelSummary::merchantId));
        List<MerchantSummary> summaries = slice.list().stream().map(record -> {
            List<MerchantChannelSummary> channels = channelsByMerchant.getOrDefault(record.id(), List.of());
            return toSummary(record, channels, resolveActions(record, channels, permissions));
        }).toList();
        return new MerchantPage(summaries, slice.total(), query.page(), query.size());
    }

    public MerchantDetail get(Long tenantId,
                              Long actorUserId,
                              Long merchantId,
                              MerchantActionPermissions permissions,
                              String ipAddress) {
        requireTenantContext(tenantId);
        List<Long> authorizedAgentIds = resolveAuthorizedAgentIds(tenantId, actorUserId);
        MerchantQueryRecord record = merchantRepository
            .findScopedById(tenantId, actorUserId, authorizedAgentIds, merchantId)
            .orElseGet(() -> {
                auditDenied(tenantId, actorUserId, merchantId, ipAddress);
                throw new MerchantAccessDeniedException();
            });
        List<MerchantChannelSummary> channels = merchantRepository.listLatestChannelSummaries(tenantId, List.of(record
            .id()));
        return toDetail(record, channels, resolveActions(record, channels, permissions));
    }

    private List<Long> resolveAuthorizedAgentIds(Long tenantId, Long actorUserId) {
        try {
            return agentScopeAuthorizationService.listAuthorizedAgentIds(tenantId, actorUserId);
        } catch (AgentAccessDeniedException ex) {
            return List.of();
        }
    }

    private List<MerchantAction> resolveActions(MerchantQueryRecord merchant,
                                                List<MerchantChannelSummary> channels,
                                                MerchantActionPermissions permissions) {
        boolean active = !MerchantStatus.DISABLED.equals(merchant.status());
        boolean successfullyOnboarded = channels.stream().anyMatch(MerchantChannelSummary::isSuccessfullyOnboarded);
        List<MerchantAction> actions = new ArrayList<>();
        add(actions, permissions.view(), MerchantAction.VIEW);
        add(actions, permissions.editProfile(), MerchantAction.EDIT_PROFILE);
        add(actions, permissions.startOnboarding() && active, MerchantAction.START_ONBOARDING);
        add(actions, permissions.changeLifecycle(), MerchantAction.CHANGE_LIFECYCLE);
        add(actions, permissions.requestReverification() && active && (merchant
            .certifiedKycVersionId() != null || successfullyOnboarded), MerchantAction.REQUEST_REVERIFICATION);
        add(actions, permissions.adjustLimit() && active && successfullyOnboarded, MerchantAction.ADJUST_LIMIT);
        add(actions, permissions.viewLimitHistory() && successfullyOnboarded, MerchantAction.VIEW_LIMIT_HISTORY);
        return List.copyOf(actions);
    }

    private void add(List<MerchantAction> actions, boolean allowed, MerchantAction action) {
        if (allowed) {
            actions.add(action);
        }
    }

    private MerchantSummary toSummary(MerchantQueryRecord record,
                                      List<MerchantChannelSummary> channels,
                                      List<MerchantAction> actions) {
        return new MerchantSummary(record.id(), record.merchantNo(), record.merchantType(), record.legalName(), record
            .shortName(), record.legalRepresentativeName(), record.operatorUsername(), record.reviewerUsername(), record
                .contactName(), record.contactMobileMasked(), record.owningAgentId(), record.owningAgentNo(), record
                    .owningAgentName(), record.status(), channels, actions, record.createTime());
    }

    private MerchantDetail toDetail(MerchantQueryRecord record,
                                    List<MerchantChannelSummary> channels,
                                    List<MerchantAction> actions) {
        return new MerchantDetail(record.id(), record.merchantNo(), record.merchantType(), record.legalName(), record
            .shortName(), record.legalRepresentativeName(), record.operatorUserId(), record.operatorUsername(), record
                .reviewerUserId(), record.reviewerUsername(), record.contactName(), record.contactMobileMasked(), record
                    .reviewerMobileMasked(), record.industry(), record.productDescription(), record
                        .owningAgentId(), record.owningAgentNo(), record.owningAgentName(), record.status(), record
                            .disabledReason(), record.certifiedKycVersionId(), record
                                .rowVersion(), channels, actions, record.createTime(), record.updateTime());
    }

    private void auditDenied(Long tenantId, Long actorUserId, Long merchantId, String ipAddress) {
        Long actorAgentId = agentRepository.findByUserId(tenantId, actorUserId).map(Agent::id).orElse(null);
        securityAuditWriter
            .append(new SecurityAuditRecord(tenantId, actorUserId, actorAgentId, "MERCHANT_READ_DENIED", "MERCHANT", merchantId, null, null, null, ipAddress, SecurityAuditResult.DENIED, "MERCHANT_DETAIL_SCOPE_DENIED", LocalDateTime
                .now(clock)));
    }

    private void requireTenantContext(Long tenantId) {
        if (tenantId == null || !tenantId.equals(TenantContextHolder.getTenantId())) {
            throw new MerchantAccessDeniedException();
        }
    }
}
