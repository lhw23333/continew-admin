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

package top.continew.admin.merchant.limit.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import top.continew.admin.common.base.model.entity.TenantBaseDO;
import top.continew.admin.merchant.limit.domain.LimitApprovalStatus;
import top.continew.admin.merchant.limit.domain.LimitChannelStatus;
import top.continew.admin.merchant.limit.domain.LimitEffectiveStatus;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("biz_limit_adjustment")
public class LimitAdjustmentDO extends TenantBaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    private String requestNo;
    private Long merchantId;
    private Long owningAgentId;
    private String channelCode;
    private String platformCode;
    private String currency;
    private BigDecimal originalLimit;
    private BigDecimal requestedLimit;
    private BigDecimal normalizedLimit;
    private BigDecimal effectiveLimit;
    private String reason;
    private String eligibilityVersion;
    private String channelConfigVersion;
    private String amountPolicyVersion;
    private String processInstanceId;
    private LimitApprovalStatus approvalStatus;
    private LimitChannelStatus channelStatus;
    private LimitEffectiveStatus effectiveStatus;
    private String activeRequestGuard;
    private Long applicantId;
    private LocalDateTime applicationTime;
    private LocalDateTime approvalTime;
    private LocalDateTime effectiveTime;
    private String opinion;
    private String channelResultCode;
    private String channelResultMessage;
    private Long rowVersion;
}
