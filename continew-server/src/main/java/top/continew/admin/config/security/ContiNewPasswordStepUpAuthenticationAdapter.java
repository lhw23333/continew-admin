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

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import top.continew.admin.common.constant.GlobalConstants;
import top.continew.admin.common.enums.DisEnableStatusEnum;
import top.continew.admin.common.util.SecureUtils;
import top.continew.admin.merchant.security.reveal.StepUpAuthenticationPort;
import top.continew.admin.system.mapper.user.UserMapper;
import top.continew.admin.system.model.entity.user.UserDO;
import top.continew.starter.cache.redisson.util.RedisUtils;

import java.time.Duration;

/** Reuses the login RSA transport and password encoder with a separate short-lived failure lock. */
@Component
@RequiredArgsConstructor
public class ContiNewPasswordStepUpAuthenticationAdapter implements StepUpAuthenticationPort {

    private static final String CACHE_PREFIX = "merchant:reveal:step-up:";

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Value("${merchant.security.reveal.max-failures:5}")
    private int maxFailures;

    @Value("${merchant.security.reveal.lock-minutes:10}")
    private int lockMinutes;

    @Override
    public boolean verify(Long actorUserId, String encryptedPasswordProof, String ipAddress) {
        String cacheKey = CACHE_PREFIX + actorUserId + ':' + StrUtil.blankToDefault(ipAddress, "unknown");
        Integer failures = RedisUtils.get(cacheKey);
        if (failures != null && failures >= Math.max(maxFailures, 1)) {
            return false;
        }
        String rawPassword;
        try {
            rawPassword = SecureUtils.decryptPasswordByRsaPrivateKey(encryptedPasswordProof, "身份验证失败");
        } catch (RuntimeException ex) {
            recordFailure(cacheKey, failures);
            return false;
        }
        UserDO user = userMapper.selectById(actorUserId);
        boolean verified = user != null && DisEnableStatusEnum.ENABLE.equals(user.getStatus()) && StrUtil
            .isNotBlank(user.getPassword()) && passwordEncoder.matches(rawPassword, user.getPassword());
        if (verified) {
            RedisUtils.delete(cacheKey);
        } else {
            recordFailure(cacheKey, failures);
        }
        return verified;
    }

    private void recordFailure(String cacheKey, Integer failures) {
        int next = (failures == null ? GlobalConstants.Boolean.NO : failures) + 1;
        RedisUtils.set(cacheKey, next, Duration.ofMinutes(Math.max(lockMinutes, 1)));
    }
}
