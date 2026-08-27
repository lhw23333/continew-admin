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
import top.continew.admin.merchant.limit.domain.LimitAdjustmentPolicyStatus;
import top.continew.admin.merchant.limit.domain.LimitRoundingMode;
import top.continew.starter.extension.crud.model.entity.BaseIdDO;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("biz_limit_adjustment_policy_version")
public class LimitAdjustmentPolicyVersionDO extends BaseIdDO {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long tenantId;
    private String channelCode;
    private String platformCode;
    private String currency;
    private String policyVersion;
    private BigDecimal minimumLimit;
    private BigDecimal maximumLimit;
    private Integer currencyScale;
    private BigDecimal roundingUnit;
    private LimitRoundingMode roundingMode;
    private LimitAdjustmentPolicyStatus status;
    private LocalDateTime effectiveTime;
    private LocalDateTime expiresTime;
    private Long createUser;
    private LocalDateTime createTime;
    private Long deleted;
}
