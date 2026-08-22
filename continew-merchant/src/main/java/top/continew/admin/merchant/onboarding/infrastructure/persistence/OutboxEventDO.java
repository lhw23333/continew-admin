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
import top.continew.starter.extension.crud.model.entity.BaseIdDO;

import java.io.Serial;
import java.time.LocalDateTime;

/** Transactional outbox row containing only sanitized routing identifiers. */
@Data
@TableName("biz_outbox_event")
public class OutboxEventDO extends BaseIdDO {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long tenantId;
    private String aggregateType;
    private Long aggregateId;
    private Long aggregateVersion;
    private String eventType;
    private String eventKey;
    private String payloadJson;
    private String headersJson;
    private String status;
    private Integer retryCount;
    private LocalDateTime nextRetryTime;
    private String lockedBy;
    private LocalDateTime lockedTime;
    private LocalDateTime occurredTime;
    private LocalDateTime publishedTime;
    private String lastErrorCategory;
    private String lastErrorMessage;
    private String traceId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
