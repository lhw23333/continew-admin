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

package top.continew.admin.channel.dto;

import java.time.LocalDateTime;

/** Safe operational view and probe input for one uncertain command. */
public record ChannelRecoveryTask(Long id, ChannelCommandContext context, ChannelOperation commandOperation,
                                  ChannelOperation queryOperation, ChannelRecoveryStatus status, Integer retryCount,
                                  LocalDateTime nextRetryTime, String lastErrorCategory, String lockedBy,
                                  LocalDateTime lockedTime, Long resolvedEventId, LocalDateTime resolvedTime,
                                  String alertStatus, LocalDateTime createTime, LocalDateTime updateTime) {
    public ChannelRecoveryTask {
        id = ChannelContracts.positive(id, "recoveryId");
        if (context == null || commandOperation == null || status == null || retryCount == null || retryCount < 0 || retryCount > 1000 || createTime == null) {
            throw ChannelContracts.invalid("channel recovery task");
        }
        lastErrorCategory = ChannelContracts.optionalText(lastErrorCategory, 64, "lastErrorCategory");
        lockedBy = ChannelContracts.optionalText(lockedBy, 128, "lockedBy");
        alertStatus = ChannelContracts.optionalText(alertStatus, 32, "alertStatus");
    }
}
