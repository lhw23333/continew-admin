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

package top.continew.admin.merchant.onboarding.application;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Tenant and merchant-scoped historical KYC reuse persistence port. */
public interface KycReuseRepository {

    List<KycReusableSnapshot> listSources(Long tenantId, Long merchantId, Long targetKycVersionId);

    Optional<KycReusableSnapshot> findSource(Long tenantId, Long merchantId, Long sourceKycVersionId);

    boolean apply(Long tenantId,
                  Long merchantId,
                  Long targetApplicationId,
                  Long targetKycVersionId,
                  KycReusableSnapshot source,
                  Set<KycReuseField> fields,
                  String provenanceJson,
                  Long expectedVersion,
                  LocalDateTime updateTime);
}
