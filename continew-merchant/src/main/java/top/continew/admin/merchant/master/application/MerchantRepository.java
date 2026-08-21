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

import top.continew.admin.merchant.master.domain.Merchant;

import java.util.Optional;

/** Tenant-explicit merchant master persistence port. */
public interface MerchantRepository {

    Optional<Merchant> findById(Long tenantId, Long merchantId);

    boolean existsById(Long tenantId, Long merchantId);

    boolean existsByLegalSubjectHash(Long tenantId, String legalSubjectHash);

    void insert(Merchant merchant);

    boolean updateProfile(Merchant merchant, Long expectedVersion);

    boolean updateLifecycle(Merchant merchant, Long expectedVersion);
}
