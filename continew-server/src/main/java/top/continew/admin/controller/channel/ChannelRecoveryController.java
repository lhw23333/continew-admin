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

package top.continew.admin.controller.channel;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.channel.dto.ChannelRecoveryStatus;
import top.continew.admin.channel.dto.ChannelRecoveryTask;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.service.channel.ChannelRecoveryManagementService;
import top.continew.starter.log.annotation.Log;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/channel/recoveries")
public class ChannelRecoveryController {
    private final ChannelRecoveryManagementService service;

    @Log(ignore = true)
    @SaCheckPermission("workflow:task:list")
    @GetMapping
    public List<ChannelRecoveryTask> list(@RequestParam(required = false) ChannelRecoveryStatus status,
                                          @RequestParam(defaultValue = "100") int limit) {
        return service.list(UserContextHolder.getTenantId(), UserContextHolder.getUserId(), status, limit);
    }

    @Log(ignore = true)
    @SaCheckPermission("workflow:task:review")
    @PostMapping("/{recoveryId}/requeue")
    public boolean requeue(@PathVariable Long recoveryId) {
        return service.requeue(UserContextHolder.getTenantId(), UserContextHolder.getUserId(), recoveryId);
    }
}
