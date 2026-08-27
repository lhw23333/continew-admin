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

package top.continew.admin.channel.adapter;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import top.continew.admin.channel.dto.ChannelBusinessType;
import top.continew.admin.channel.dto.ChannelEvidenceAccessMode;
import top.continew.admin.channel.dto.ChannelEvidenceAuditOutcome;
import top.continew.admin.channel.dto.ChannelOperation;
import top.continew.starter.extension.crud.model.entity.BaseIdDO;

import java.io.Serial;
import java.time.LocalDateTime;

/** Append-only channel evidence access audit row with no URL, object key, or file content. */
@Data
@TableName("biz_channel_evidence_audit")
public class ChannelEvidenceAuditDO extends BaseIdDO {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long tenantId;
    private String channelCode;
    private String productCode;
    private String configVersion;
    private ChannelOperation operation;
    private ChannelBusinessType businessType;
    private Long businessId;
    private Long businessVersion;
    private String businessSerial;
    private String traceId;
    private Long kycVersionId;
    private Long objectId;
    private String evidenceType;
    private String objectSha256;
    private ChannelEvidenceAccessMode accessMode;
    private LocalDateTime expiresAt;
    private ChannelEvidenceAuditOutcome outcome;
    private String failureCategory;
    private LocalDateTime createTime;
}
