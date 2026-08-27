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

package top.continew.admin.channel.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.continew.admin.channel.api.ChannelRecoveryAlertPort;
import top.continew.admin.channel.api.ChannelRecoveryProbe;
import top.continew.admin.channel.api.ChannelRecoveryRepository;
import top.continew.admin.channel.dto.ChannelRecoveryBatchResult;
import top.continew.admin.channel.dto.ChannelRecoveryProbeResult;
import top.continew.admin.channel.dto.ChannelRecoveryTask;
import top.continew.starter.extension.tenant.annotation.TenantIgnore;
import top.continew.starter.extension.tenant.util.TenantUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Claims uncertain commands and resolves them only through safe business-serial probes. */
@Service
@RequiredArgsConstructor
@TenantIgnore
public class ChannelRecoveryProcessor {
    private final ChannelRecoveryRepository repository;
    private final List<ChannelRecoveryProbe> probes;
    private final ChannelRecoveryAlertPort alertPort;
    private final ChannelRecoveryPolicy policy;
    private final Clock clock = Clock.systemUTC();
    private final String workerId = "channel-recovery-" + UUID.randomUUID();

    public ChannelRecoveryBatchResult processAvailable() {
        return processAvailable(null);
    }

    public ChannelRecoveryBatchResult processTenant(Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            return ChannelRecoveryBatchResult.empty();
        }
        return processAvailable(tenantId);
    }

    public boolean requeueRepair(Long tenantId, Long recoveryId) {
        return tenantId != null && tenantId > 0 && recoveryId != null && recoveryId > 0 && repository
            .requeueRepair(tenantId, recoveryId, now());
    }

    private ChannelRecoveryBatchResult processAvailable(Long tenantId) {
        LocalDateTime now = now();
        List<ChannelRecoveryTask> tasks = repository.claimAvailable(tenantId, workerId, now, now.minus(policy
            .lockTimeout()), policy.batchSize());
        int resolved = 0;
        int retried = 0;
        int repairs = 0;
        for (ChannelRecoveryTask task : tasks) {
            Result result = recover(task);
            resolved += result == Result.RESOLVED ? 1 : 0;
            retried += result == Result.RETRIED ? 1 : 0;
            repairs += result == Result.REPAIR_REQUIRED ? 1 : 0;
        }
        int alerted = dispatchAlerts(policy.batchSize());
        return new ChannelRecoveryBatchResult(tasks.size(), resolved, retried, repairs, alerted);
    }

    private Result recover(ChannelRecoveryTask task) {
        ChannelRecoveryProbe probe = probes.stream().filter(value -> value.supports(task)).findFirst().orElse(null);
        if (task.queryOperation() == null || probe == null) {
            return markRepair(task, "UNSUPPORTED_RECOVERY");
        }
        try {
            ChannelRecoveryProbeResult[] result = new ChannelRecoveryProbeResult[1];
            TenantUtils.execute(task.context().tenantId(), () -> result[0] = probe.probe(task));
            if (result[0] == null) {
                return markRetry(task, "EMPTY_PROBE_RESULT");
            }
            return switch (result[0].outcome()) {
                case RESOLVED -> repository.markResolved(task.id(), workerId, result[0].eventRecordId(), now())
                    ? Result.RESOLVED
                    : Result.REPAIR_REQUIRED;
                case PENDING, RETRYABLE_FAILURE -> markRetry(task, category(result[0]));
                case PERMANENT_FAILURE -> markRepair(task, category(result[0]));
            };
        } catch (RuntimeException ex) {
            return markRetry(task, "PROBE_FAILURE");
        }
    }

    private Result markRetry(ChannelRecoveryTask task, String category) {
        int retryCount = task.retryCount() + 1;
        if (retryCount >= policy.maxRetries()) {
            return markRepair(task, retryCount, category);
        }
        LocalDateTime now = now();
        return repository.markRetry(task.id(), workerId, retryCount, now.plus(policy
            .retryDelay(retryCount)), category, now) ? Result.RETRIED : Result.REPAIR_REQUIRED;
    }

    private Result markRepair(ChannelRecoveryTask task, String category) {
        return markRepair(task, task.retryCount() + 1, category);
    }

    private Result markRepair(ChannelRecoveryTask task, int retryCount, String category) {
        repository.markRepairRequired(task.id(), workerId, retryCount, category, now());
        return Result.REPAIR_REQUIRED;
    }

    private int dispatchAlerts(int limit) {
        int alerted = 0;
        for (ChannelRecoveryTask task : repository.listPendingAlerts(limit)) {
            try {
                TenantUtils.execute(task.context().tenantId(), () -> alertPort.alert(task));
                if (repository.markAlerted(task.id(), now())) {
                    alerted++;
                }
            } catch (RuntimeException ignored) {
                // Keep alert_status=PENDING for the next dispatcher run.
            }
        }
        return alerted;
    }

    private String category(ChannelRecoveryProbeResult result) {
        return result.failureCategory() == null ? result.outcome().name() : result.failureCategory();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private enum Result { RESOLVED, RETRIED, REPAIR_REQUIRED }
}
