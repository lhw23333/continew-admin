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
import top.continew.admin.merchant.agent.domain.AgentStatus;
import top.continew.admin.merchant.agent.domain.AgentPromotionCodeStatus;

import java.io.Serial;

/** Agent persistence entity. */
@Data
@TableName("biz_agent")
public class AgentDO extends TenantBaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long parentId;
    private String path;
    private Long userId;
    private Long deptId;
    private String agentNo;
    private String name;
    private String contactName;
    private byte[] contactMobileCiphertext;
    private String contactMobileHash;
    private String contactMobileHashKeyVersion;
    private String contactMobileMasked;
    private String contactMobileKeyVersion;
    private String remarks;
    private String promotionCode;
    private AgentPromotionCodeStatus promotionCodeStatus;
    private AgentStatus status;
    private String disabledReason;
    private Long rowVersion;
}
