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

/** Tenant-explicit operating-platform persistence port. */
public interface OperatingPlatformRepository {

    List<OperatingPlatform> list(Long tenantId, Long kycVersionId);

    Optional<OperatingPlatform> findById(Long tenantId, Long kycVersionId, Long platformId);

    OperatingPlatform insert(Long id,
                             Long tenantId,
                             Long kycVersionId,
                             String platformCode,
                             String storeName,
                             String storeUrl,
                             String storeIdentifier,
                             OperatingPlatform.CertificationStatus certificationStatus,
                             Long createUser,
                             LocalDateTime createTime);

    boolean update(Long tenantId,
                   Long kycVersionId,
                   Long platformId,
                   String storeName,
                   String storeUrl,
                   String storeIdentifier,
                   OperatingPlatform.CertificationStatus certificationStatus,
                   Long expectedVersion,
                   LocalDateTime updateTime);

    void linkProof(Long id,
                   Long tenantId,
                   Long kycVersionId,
                   Long platformId,
                   Long attachmentId,
                   String evidenceType,
                   Long createUser,
                   LocalDateTime createTime);
}
