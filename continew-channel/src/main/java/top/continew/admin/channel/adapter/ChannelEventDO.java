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
import top.continew.admin.channel.dto.ChannelEventType;
import top.continew.admin.channel.dto.ChannelOperationStatus;
import top.continew.admin.channel.dto.ChannelStageStatus;
import top.continew.starter.extension.crud.model.entity.BaseIdDO;

import java.io.Serial;
import java.time.LocalDateTime;

/** Raw-code-retaining channel event row with sanitized normalized output. */
@Data
@TableName("biz_channel_event")
public class ChannelEventDO extends BaseIdDO {
    @Serial
    private static final long serialVersionUID = 1L;
    private Long tenantId;
    private String channelCode;
    private String productCode;
    private String configVersion;
    private String channelEventId;
    private String eventKey;
    private Long applicationId;
    private Long merchantId;
    private ChannelBusinessType businessType;
    private Long businessVersion;
    private String businessSerial;
    private ChannelEventType eventType;
    private String channelRequestId;
    private String rawStatus;
    private String normalizedStateType;
    private String normalizedStatus;
    private ChannelOperationStatus operationStatus;
    private ChannelStageStatus reportingStatus;
    private ChannelStageStatus signingStatus;
    private ChannelStageStatus cardBindingStatus;
    private ChannelStageStatus reserveAccountStatus;
    private ChannelStageStatus finalStatus;
    private Integer progressionRank;
    private String mappingVersion;
    private String payloadHash;
    private String sanitizedPayloadJson;
    private String signatureKeyVersion;
    private LocalDateTime occurredTime;
    private LocalDateTime receivedTime;
    private LocalDateTime processedTime;
    private String processingStatus;
    private Boolean stateApplied;
    private Integer retryCount;
    private String lastErrorCategory;
    private String lastErrorMessage;
    private String traceId;
    private Long rowVersion;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
