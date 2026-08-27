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

package top.continew.admin.job;

import cn.hutool.extra.spring.SpringUtil;
import com.aizuda.snailjob.client.job.core.annotation.JobExecutor;
import com.aizuda.snailjob.common.log.SnailJobLog;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import top.continew.admin.channel.dto.ChannelRecoveryBatchResult;
import top.continew.admin.channel.service.ChannelRecoveryProcessor;
import top.continew.admin.schedule.annotation.ConditionalOnEnabledScheduleJob;
import top.continew.starter.core.constant.PropertiesConstants;
import top.continew.starter.extension.tenant.annotation.TenantIgnore;

public final class ChannelRecoveryJob {
    private ChannelRecoveryJob() {
    }

    @Component
    @ConditionalOnProperty(prefix = "snail-job", name = PropertiesConstants.ENABLED, havingValue = "false")
    public static class Scheduler {
        @TenantIgnore
        @Scheduled(fixedDelayString = "${channel.recovery.fallback-delay:60000}")
        public void recover() {
            process();
        }
    }

    @Component
    @ConditionalOnEnabledScheduleJob
    public static class SnailJob {
        @TenantIgnore
        @JobExecutor(name = "ChannelUncertainRecoveryJob")
        public void recover() {
            ChannelRecoveryBatchResult result = process();
            SnailJobLog.REMOTE
                .info("渠道恢复任务完成: claimed={}, resolved={}, retried={}, repairRequired={}, alerted={}", result
                    .claimed(), result.resolved(), result.retried(), result.repairRequired(), result.alerted());
        }
    }

    private static ChannelRecoveryBatchResult process() {
        return SpringUtil.getBean(ChannelRecoveryProcessor.class).processAvailable();
    }
}
