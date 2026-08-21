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

package top.continew.admin.merchant.security.audit.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import top.continew.admin.merchant.security.audit.domain.SecurityAuditResult;
import top.continew.starter.extension.crud.model.entity.BaseIdDO;

import java.io.Serial;
import java.time.LocalDateTime;

/** Append-only security-audit persistence entity. */
@Data
@TableName("biz_security_audit")
public class SecurityAuditDO extends BaseIdDO {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long tenantId;
    private Long actorUserId;
    private Long actorAgentId;
    private String action;
    private String objectType;
    private Long objectId;
    private Long businessVersion;
    private String fieldName;
    private String reason;
    private String ipAddress;
    private SecurityAuditResult result;
    private String failureCode;
    private LocalDateTime createTime;
}
