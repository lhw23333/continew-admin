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
import top.continew.admin.merchant.limit.domain.LimitApprovalStatus;
import top.continew.admin.merchant.limit.domain.LimitChannelStatus;
import top.continew.admin.merchant.limit.domain.LimitEffectiveStatus;
import top.continew.starter.extension.crud.model.entity.BaseIdDO;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("biz_limit_adjustment_history")
public class LimitAdjustmentHistoryDO extends BaseIdDO {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long tenantId;
    private Long requestId;
    private Long requestVersion;
    private String action;
    private LimitApprovalStatus approvalStatus;
    private LimitChannelStatus channelStatus;
    private LimitEffectiveStatus effectiveStatus;
    private BigDecimal originalLimit;
    private BigDecimal requestedLimit;
    private BigDecimal normalizedLimit;
    private BigDecimal effectiveLimit;
    private Long actorUserId;
    private String opinion;
    private String channelResultCode;
    private String channelResultMessage;
    private LocalDateTime occurredTime;
}
