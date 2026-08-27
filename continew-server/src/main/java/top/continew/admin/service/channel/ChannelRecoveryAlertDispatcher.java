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

package top.continew.admin.service.channel;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import top.continew.admin.channel.api.ChannelRecoveryAlertPort;
import top.continew.admin.channel.dto.ChannelRecoveryTask;
import top.continew.admin.system.enums.MessageTypeEnum;
import top.continew.admin.system.model.req.MessageReq;
import top.continew.admin.system.service.MessageService;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ChannelRecoveryAlertDispatcher implements ChannelRecoveryAlertPort {
    private final JdbcTemplate jdbcTemplate;
    private final MessageService messageService;

    @Override
    public void alert(ChannelRecoveryTask task) {
        List<String> recipients = jdbcTemplate.queryForList("""
            SELECT DISTINCT u.id
            FROM sys_user u
            JOIN sys_user_role ur ON ur.tenant_id = u.tenant_id AND ur.user_id = u.id
            JOIN sys_role r ON r.tenant_id = ur.tenant_id AND r.id = ur.role_id AND r.deleted = 0
            WHERE u.tenant_id = ? AND u.status = 1 AND u.deleted = 0
              AND r.code IN ('AGENT_ADMIN', 'MERCHANT_REVIEWER', 'RISK_REVIEWER')
            """, Long.class, task.context().tenantId()).stream().map(String::valueOf).toList();
        if (recipients.isEmpty()) {
            throw new IllegalStateException("No channel recovery alert recipient is available");
        }
        MessageReq request = new MessageReq(MessageTypeEnum.SYSTEM);
        request.setTitle("渠道结果需要人工处理");
        request.setContent("渠道任务 %s 已超过自动恢复范围，请核查业务流水。".formatted(task.id()));
        request.setPath("/merchant/channel-recovery?recoveryId=" + task.id());
        messageService.add(request, recipients);
    }
}
