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
import top.continew.admin.channel.dto.ChannelConnectionStatus;
import top.continew.admin.common.base.model.entity.TenantBaseDO;

import java.io.Serial;
import java.time.LocalDateTime;

@Data
@TableName("biz_channel_connection_version")
public class ChannelConnectionVersionDO extends TenantBaseDO {
    @Serial
    private static final long serialVersionUID = 1L;
    private String channelCode;
    private String productCode;
    private String configVersion;
    private String endpointJson;
    private String timeoutJson;
    private String statusMappingVersion;
    private String statusMappingJson;
    private String signingKeyRef;
    private String encryptionKeyRef;
    private String callbackVerificationKeyRef;
    private ChannelConnectionStatus status;
    private LocalDateTime effectiveTime;
    private LocalDateTime expiresTime;
}
