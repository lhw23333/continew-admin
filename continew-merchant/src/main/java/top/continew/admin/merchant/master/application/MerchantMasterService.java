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
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.merchant.agent.application.AgentScopeAuthorizationService;
import top.continew.admin.merchant.agent.domain.AgentAccessDeniedException;
import top.continew.admin.merchant.master.domain.Merchant;
import top.continew.admin.merchant.master.domain.MerchantAccessDeniedException;
import top.continew.admin.merchant.master.domain.MerchantConcurrentModificationException;
import top.continew.admin.merchant.master.domain.MerchantDomainException;
import top.continew.admin.merchant.master.domain.MerchantRegistration;
import top.continew.admin.merchant.master.domain.MerchantStatus;
import top.continew.starter.extension.tenant.context.TenantContextHolder;

import java.time.Clock;
import java.time.LocalDateTime;

/** Creates merchant masters and controls their independent lifecycle. */
@Service
@RequiredArgsConstructor
public class MerchantMasterService {

    private final MerchantRepository merchantRepository;
    private final MerchantScopeAuthorizationService merchantScopeAuthorizationService;
    private final AgentScopeAuthorizationService agentScopeAuthorizationService;
    private final Clock clock = Clock.systemDefaultZone();

    @Transactional
    public Merchant register(Long actorUserId, MerchantRegistration registration) {
        requireTenantContext(registration.tenantId());
        try {
            AgentScopeAuthorizationService.AgentScope scope = agentScopeAuthorizationService
                .requireAccessible(registration.tenantId(), actorUserId, registration.owningAgentId());
            if (!scope.target().isEnabled()) {
                throw new MerchantAccessDeniedException();
            }
        } catch (AgentAccessDeniedException ex) {
            throw new MerchantAccessDeniedException();
        }
        if (merchantRepository.existsById(registration.tenantId(), registration.id())) {
            throw new MerchantDomainException("Merchant ID already exists");
        }
        Merchant merchant = Merchant.create(registration, LocalDateTime.now(clock));
        merchantRepository.insert(merchant);
        return merchant;
    }

    @Transactional
    public Merchant changeLifecycle(Long tenantId,
                                    Long actorUserId,
                                    Long merchantId,
                                    MerchantStatus status,
                                    String reason,
                                    Long expectedVersion) {
        requireTenantContext(tenantId);
        Merchant current = merchantScopeAuthorizationService.requireAccessible(tenantId, actorUserId, merchantId);
        if (!current.rowVersion().equals(expectedVersion)) {
            throw new MerchantConcurrentModificationException();
        }
        Merchant changed = current.changeStatus(status, reason, LocalDateTime.now(clock));
        if (!merchantRepository.updateLifecycle(changed, expectedVersion)) {
            throw new MerchantConcurrentModificationException();
        }
        return changed;
    }

    private void requireTenantContext(Long tenantId) {
        if (tenantId == null || !tenantId.equals(TenantContextHolder.getTenantId())) {
            throw new MerchantAccessDeniedException();
        }
    }
}
