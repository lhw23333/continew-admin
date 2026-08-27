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
import org.springframework.stereotype.Service;
import top.continew.admin.channel.api.ChannelRecoveryRepository;
import top.continew.admin.channel.dto.ChannelRecoveryStatus;
import top.continew.admin.channel.dto.ChannelRecoveryTask;
import top.continew.admin.channel.service.ChannelRecoveryProcessor;
import top.continew.admin.merchant.master.application.MerchantScopeAuthorizationService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChannelRecoveryManagementService {
    private final ChannelRecoveryRepository repository;
    private final ChannelRecoveryProcessor processor;
    private final MerchantScopeAuthorizationService merchantScope;
    private final JdbcTemplate jdbcTemplate;

    public List<ChannelRecoveryTask> list(Long tenantId, Long userId, ChannelRecoveryStatus status, int limit) {
        int bounded = Math.min(Math.max(limit, 1), 200);
        return repository.list(tenantId, status, bounded)
            .stream()
            .filter(task -> merchantScope.canAccess(tenantId, userId, merchantId(task)))
            .toList();
    }

    public boolean requeue(Long tenantId, Long userId, Long recoveryId) {
        ChannelRecoveryTask task = repository.find(tenantId, recoveryId)
            .orElseThrow(() -> new IllegalArgumentException("Channel recovery task is unavailable"));
        merchantScope.requireAccessible(tenantId, userId, merchantId(task));
        return processor.requeueRepair(tenantId, recoveryId);
    }

    private Long merchantId(ChannelRecoveryTask task) {
        return jdbcTemplate.queryForObject("""
            SELECT merchant_id FROM biz_onboarding_application
            WHERE tenant_id = ? AND id = ? AND deleted = 0
            """, Long.class, task.context().tenantId(), task.context().businessId());
    }
}
