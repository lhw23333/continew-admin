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

package top.continew.admin.config.security;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Component;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.merchant.security.reveal.PrivilegedRevealDeniedException;
import top.continew.admin.merchant.security.reveal.SensitiveRevealPermissionPort;

import java.util.Objects;

/** Bridges reveal permission checks to ContiNew/Sa-Token. */
@Component
public class ContiNewSensitiveRevealPermissionAdapter implements SensitiveRevealPermissionPort {

    public static final String REVEAL_PERMISSION = "merchant:sensitive:reveal";

    @Override
    public void requireAllowed(Long actorUserId) {
        if (!Objects.equals(UserContextHolder.getUserId(), actorUserId)) {
            throw new PrivilegedRevealDeniedException();
        }
        StpUtil.checkPermission(REVEAL_PERMISSION);
    }
}
