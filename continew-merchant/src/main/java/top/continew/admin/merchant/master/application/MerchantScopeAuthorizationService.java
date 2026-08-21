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
import top.continew.admin.merchant.agent.application.AgentScopeAuthorizationService;
import top.continew.admin.merchant.agent.domain.AgentAccessDeniedException;
import top.continew.admin.merchant.master.domain.Merchant;
import top.continew.admin.merchant.master.domain.MerchantAccessDeniedException;
import top.continew.starter.extension.tenant.context.TenantContextHolder;

/** Enforces merchant ownership without revealing whether an inaccessible merchant exists. */
@Service
@RequiredArgsConstructor
public class MerchantScopeAuthorizationService {

    private final MerchantRepository merchantRepository;
    private final AgentScopeAuthorizationService agentScopeAuthorizationService;

    public Merchant requireAccessible(Long tenantId, Long actorUserId, Long merchantId) {
        requireTenantContext(tenantId);
        Merchant merchant = merchantRepository.findById(tenantId, merchantId)
            .orElseThrow(MerchantAccessDeniedException::new);
        if (merchant.isDirectIdentity(actorUserId)) {
            return merchant;
        }
        try {
            agentScopeAuthorizationService.requireAccessible(tenantId, actorUserId, merchant.owningAgentId());
            return merchant;
        } catch (AgentAccessDeniedException ex) {
            throw new MerchantAccessDeniedException();
        }
    }

    public boolean canAccess(Long tenantId, Long actorUserId, Long merchantId) {
        try {
            requireAccessible(tenantId, actorUserId, merchantId);
            return true;
        } catch (MerchantAccessDeniedException ex) {
            return false;
        }
    }

    private void requireTenantContext(Long tenantId) {
        if (tenantId == null || !tenantId.equals(TenantContextHolder.getTenantId())) {
            throw new MerchantAccessDeniedException();
        }
    }
}
