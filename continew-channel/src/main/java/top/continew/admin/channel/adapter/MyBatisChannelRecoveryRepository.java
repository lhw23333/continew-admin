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

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.channel.api.ChannelRecoveryRepository;
import top.continew.admin.channel.api.ChannelRecoveryRegistrationPort;
import top.continew.admin.channel.dto.ChannelCommandContext;
import top.continew.admin.channel.dto.ChannelProductKey;
import top.continew.admin.channel.dto.ChannelRecoveryDraft;
import top.continew.admin.channel.dto.ChannelRecoveryStatus;
import top.continew.admin.channel.dto.ChannelRecoveryTask;
import top.continew.starter.extension.tenant.annotation.TenantIgnore;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
@TenantIgnore
public class MyBatisChannelRecoveryRepository implements ChannelRecoveryRepository, ChannelRecoveryRegistrationPort {
    private final ChannelRecoveryMapper mapper;

    @Override
    @Transactional
    public Long register(ChannelRecoveryDraft draft) {
        ChannelRecoveryDO row = new ChannelRecoveryDO();
        row.setTenantId(draft.context().tenantId());
        row.setChannelCode(draft.context().product().channelCode());
        row.setProductCode(draft.context().product().productCode());
        row.setConfigVersion(draft.context().configVersion());
        row.setCommandOperation(draft.commandOperation());
        row.setQueryOperation(draft.queryOperation());
        row.setBusinessType(draft.context().businessType());
        row.setBusinessId(draft.context().businessId());
        row.setBusinessVersion(draft.context().businessVersion());
        row.setBusinessSerial(draft.context().businessSerial());
        row.setTraceId(draft.context().traceId());
        row.setStatus(ChannelRecoveryStatus.PENDING);
        row.setRetryCount(0);
        row.setNextRetryTime(draft.nextRetryTime());
        row.setAlertStatus("NOT_REQUIRED");
        row.setCreateTime(draft.createTime());
        if (mapper.register(row) == 1) {
            return row.getId();
        }
        return mapper.lambdaQuery()
            .select(ChannelRecoveryDO::getId)
            .eq(ChannelRecoveryDO::getTenantId, row.getTenantId())
            .eq(ChannelRecoveryDO::getChannelCode, row.getChannelCode())
            .eq(ChannelRecoveryDO::getProductCode, row.getProductCode())
            .eq(ChannelRecoveryDO::getBusinessSerial, row.getBusinessSerial())
            .eq(ChannelRecoveryDO::getCommandOperation, row.getCommandOperation())
            .oneOpt()
            .map(ChannelRecoveryDO::getId)
            .orElseThrow(() -> new IllegalStateException("Channel recovery registration failed"));
    }

    @Override
    @Transactional
    public List<ChannelRecoveryTask> claimAvailable(Long tenantId,
                                                    String workerId,
                                                    LocalDateTime now,
                                                    LocalDateTime staleBefore,
                                                    int limit) {
        List<ChannelRecoveryDO> candidates = new ArrayList<>(mapper.lambdaQuery()
            .eq(tenantId != null, ChannelRecoveryDO::getTenantId, tenantId)
            .in(ChannelRecoveryDO::getStatus, ChannelRecoveryStatus.PENDING, ChannelRecoveryStatus.RETRY)
            .le(ChannelRecoveryDO::getNextRetryTime, now)
            .orderByAsc(ChannelRecoveryDO::getNextRetryTime)
            .orderByAsc(ChannelRecoveryDO::getId)
            .last("LIMIT " + limit)
            .list());
        int remaining = limit - candidates.size();
        if (remaining > 0) {
            candidates.addAll(mapper.lambdaQuery()
                .eq(tenantId != null, ChannelRecoveryDO::getTenantId, tenantId)
                .eq(ChannelRecoveryDO::getStatus, ChannelRecoveryStatus.PROCESSING)
                .le(ChannelRecoveryDO::getLockedTime, staleBefore)
                .orderByAsc(ChannelRecoveryDO::getLockedTime)
                .last("LIMIT " + remaining)
                .list());
        }
        List<ChannelRecoveryTask> claimed = new ArrayList<>();
        for (ChannelRecoveryDO candidate : candidates) {
            var update = mapper.lambdaUpdate()
                .eq(ChannelRecoveryDO::getId, candidate.getId())
                .eq(ChannelRecoveryDO::getStatus, candidate.getStatus());
            if (candidate.getStatus() == ChannelRecoveryStatus.PROCESSING) {
                update.eq(ChannelRecoveryDO::getLockedTime, candidate.getLockedTime());
            }
            if (update.set(ChannelRecoveryDO::getStatus, ChannelRecoveryStatus.PROCESSING)
                .set(ChannelRecoveryDO::getLockedBy, workerId)
                .set(ChannelRecoveryDO::getLockedTime, now)
                .set(ChannelRecoveryDO::getUpdateTime, now)
                .update()) {
                candidate.setStatus(ChannelRecoveryStatus.PROCESSING);
                candidate.setLockedBy(workerId);
                candidate.setLockedTime(now);
                claimed.add(toTask(candidate));
            }
        }
        return List.copyOf(claimed);
    }

    @Override
    public boolean markResolved(Long id, String workerId, Long eventRecordId, LocalDateTime time) {
        return processingUpdate(id, workerId).set(ChannelRecoveryDO::getStatus, ChannelRecoveryStatus.RESOLVED)
            .set(ChannelRecoveryDO::getResolvedEventId, eventRecordId)
            .set(ChannelRecoveryDO::getResolvedTime, time)
            .set(ChannelRecoveryDO::getNextRetryTime, null)
            .set(ChannelRecoveryDO::getLockedBy, null)
            .set(ChannelRecoveryDO::getLockedTime, null)
            .set(ChannelRecoveryDO::getLastErrorCategory, null)
            .set(ChannelRecoveryDO::getUpdateTime, time)
            .update();
    }

    @Override
    public boolean markRetry(Long id,
                             String workerId,
                             int count,
                             LocalDateTime next,
                             String category,
                             LocalDateTime time) {
        return processingUpdate(id, workerId).set(ChannelRecoveryDO::getStatus, ChannelRecoveryStatus.RETRY)
            .set(ChannelRecoveryDO::getRetryCount, count)
            .set(ChannelRecoveryDO::getNextRetryTime, next)
            .set(ChannelRecoveryDO::getLastErrorCategory, category)
            .set(ChannelRecoveryDO::getLockedBy, null)
            .set(ChannelRecoveryDO::getLockedTime, null)
            .set(ChannelRecoveryDO::getUpdateTime, time)
            .update();
    }

    @Override
    public boolean markRepairRequired(Long id, String workerId, int count, String category, LocalDateTime time) {
        return processingUpdate(id, workerId).set(ChannelRecoveryDO::getStatus, ChannelRecoveryStatus.REPAIR_REQUIRED)
            .set(ChannelRecoveryDO::getRetryCount, count)
            .set(ChannelRecoveryDO::getNextRetryTime, null)
            .set(ChannelRecoveryDO::getLastErrorCategory, category)
            .set(ChannelRecoveryDO::getLockedBy, null)
            .set(ChannelRecoveryDO::getLockedTime, null)
            .set(ChannelRecoveryDO::getAlertStatus, "PENDING")
            .set(ChannelRecoveryDO::getUpdateTime, time)
            .update();
    }

    @Override
    public List<ChannelRecoveryTask> listPendingAlerts(int limit) {
        return mapper.lambdaQuery()
            .eq(ChannelRecoveryDO::getStatus, ChannelRecoveryStatus.REPAIR_REQUIRED)
            .eq(ChannelRecoveryDO::getAlertStatus, "PENDING")
            .orderByAsc(ChannelRecoveryDO::getId)
            .last("LIMIT " + limit)
            .list()
            .stream()
            .map(this::toTask)
            .toList();
    }

    @Override
    public boolean markAlerted(Long id, LocalDateTime time) {
        return mapper.lambdaUpdate()
            .eq(ChannelRecoveryDO::getId, id)
            .eq(ChannelRecoveryDO::getAlertStatus, "PENDING")
            .set(ChannelRecoveryDO::getAlertStatus, "SENT")
            .set(ChannelRecoveryDO::getUpdateTime, time)
            .update();
    }

    @Override
    public Optional<ChannelRecoveryTask> find(Long tenantId, Long id) {
        return mapper.lambdaQuery()
            .eq(ChannelRecoveryDO::getTenantId, tenantId)
            .eq(ChannelRecoveryDO::getId, id)
            .oneOpt()
            .map(this::toTask);
    }

    @Override
    public List<ChannelRecoveryTask> list(Long tenantId, ChannelRecoveryStatus status, int limit) {
        return mapper.lambdaQuery()
            .eq(ChannelRecoveryDO::getTenantId, tenantId)
            .eq(status != null, ChannelRecoveryDO::getStatus, status)
            .orderByDesc(ChannelRecoveryDO::getCreateTime)
            .last("LIMIT " + limit)
            .list()
            .stream()
            .map(this::toTask)
            .toList();
    }

    @Override
    public boolean requeueRepair(Long tenantId, Long id, LocalDateTime time) {
        return mapper.lambdaUpdate()
            .eq(ChannelRecoveryDO::getTenantId, tenantId)
            .eq(ChannelRecoveryDO::getId, id)
            .eq(ChannelRecoveryDO::getStatus, ChannelRecoveryStatus.REPAIR_REQUIRED)
            .set(ChannelRecoveryDO::getStatus, ChannelRecoveryStatus.PENDING)
            .set(ChannelRecoveryDO::getRetryCount, 0)
            .set(ChannelRecoveryDO::getNextRetryTime, time)
            .set(ChannelRecoveryDO::getLastErrorCategory, null)
            .set(ChannelRecoveryDO::getAlertStatus, "NOT_REQUIRED")
            .set(ChannelRecoveryDO::getUpdateTime, time)
            .update();
    }

    private com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper<ChannelRecoveryDO> processingUpdate(Long id,
                                                                                                                              String workerId) {
        return mapper.lambdaUpdate()
            .eq(ChannelRecoveryDO::getId, id)
            .eq(ChannelRecoveryDO::getStatus, ChannelRecoveryStatus.PROCESSING)
            .eq(ChannelRecoveryDO::getLockedBy, workerId);
    }

    private ChannelRecoveryTask toTask(ChannelRecoveryDO row) {
        ChannelCommandContext context = new ChannelCommandContext(row.getTenantId(), new ChannelProductKey(row
            .getChannelCode(), row.getProductCode()), row.getConfigVersion(), row.getBusinessType(), row
                .getBusinessId(), row.getBusinessVersion(), row.getBusinessSerial(), row.getTraceId());
        return new ChannelRecoveryTask(row.getId(), context, row.getCommandOperation(), row.getQueryOperation(), row
            .getStatus(), row.getRetryCount(), row.getNextRetryTime(), row.getLastErrorCategory(), row
                .getLockedBy(), row.getLockedTime(), row.getResolvedEventId(), row.getResolvedTime(), row
                    .getAlertStatus(), row.getCreateTime(), row.getUpdateTime());
    }
}
