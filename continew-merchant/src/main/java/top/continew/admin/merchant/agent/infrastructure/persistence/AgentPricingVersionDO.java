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

package top.continew.admin.merchant.agent.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import top.continew.admin.common.base.model.entity.TenantBaseDO;
import top.continew.admin.merchant.agent.domain.AgentPricingStatus;

import java.io.Serial;
import java.time.LocalDateTime;

/** Append-only agent pricing persistence entity. */
@Data
@TableName("biz_agent_pricing_version")
public class AgentPricingVersionDO extends TenantBaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long agentId;
    private Long parentPricingVersionId;
    private Integer versionNo;
    private String channelCode;
    private String productCode;
    private String currency;
    private String pricingRulesJson;
    private LocalDateTime effectiveTime;
    private LocalDateTime expiresTime;
    private AgentPricingStatus status;
}
