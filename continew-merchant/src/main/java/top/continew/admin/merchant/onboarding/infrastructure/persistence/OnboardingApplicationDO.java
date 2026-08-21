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

/** Onboarding application persistence entity used by the draft boundary. */
@Data
@TableName("biz_onboarding_application")
public class OnboardingApplicationDO extends TenantBaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    private String applicationNo;
    private Long merchantId;
    private Long owningAgentId;
    private String channelCode;
    private String productCode;
    private String requirementVersion;
    private String channelConfigVersion;
    private Long kycVersionId;
    private String status;
    private String activeDraftGuard;
    private Long rowVersion;
}
