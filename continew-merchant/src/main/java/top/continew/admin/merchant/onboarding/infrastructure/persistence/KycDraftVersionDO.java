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

package top.continew.admin.merchant.onboarding.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import top.continew.admin.common.base.model.entity.TenantBaseDO;

import java.io.Serial;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Minimal KYC draft persistence entity for progress and optimistic versioning. */
@Data
@TableName("biz_kyc_version")
public class KycDraftVersionDO extends TenantBaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long merchantId;
    private Long onboardingApplicationId;
    private Integer versionNo;
    private Long sourceKycVersionId;
    private String reuseProvenanceJson;
    private String requirementVersion;
    private String status;
    private Integer savedStep;
    private String stepCompletionJson;
    private String legalName;
    private byte[] legalIdentifierCiphertext;
    private String legalIdentifierHash;
    private String legalIdentifierHashKeyVersion;
    private String legalIdentifierMasked;
    private String legalIdentifierKeyVersion;
    private LocalDate licenseIssueDate;
    private LocalDate licenseExpiryDate;
    private String businessScope;
    private Long pricingVersionId;
    private LocalDateTime frozenTime;
    private Long rowVersion;
}
