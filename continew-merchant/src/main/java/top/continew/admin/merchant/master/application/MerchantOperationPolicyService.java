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
import top.continew.admin.merchant.master.domain.Merchant;
import top.continew.admin.merchant.master.domain.MerchantAccessDeniedException;
import top.continew.admin.merchant.master.domain.MerchantDomainException;
import top.continew.admin.merchant.master.domain.MerchantStatus;
import top.continew.starter.extension.tenant.context.TenantContextHolder;

/** Authoritative guard for new downstream operations while preserving historical reads. */
@Service
@RequiredArgsConstructor
public class MerchantOperationPolicyService {

    private final MerchantRepository merchantRepository;

    public void requireAllowed(Long tenantId, Long merchantId, MerchantOperation operation) {
        if (tenantId == null || !tenantId.equals(TenantContextHolder.getTenantId())) {
            throw new MerchantAccessDeniedException();
        }
        Merchant merchant = merchantRepository.findById(tenantId, merchantId)
            .orElseThrow(MerchantAccessDeniedException::new);
        if (MerchantStatus.DISABLED.equals(merchant.status())) {
            throw new MerchantDomainException("Disabled merchant cannot start " + operation.name().toLowerCase());
        }
    }

    public enum MerchantOperation { NEW_ONBOARDING, TRANSACTION, SETTLEMENT }
}
