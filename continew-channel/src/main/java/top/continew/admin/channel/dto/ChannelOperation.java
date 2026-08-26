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

public enum ChannelOperation {
    SUBMIT_ONBOARDING(false), QUERY_ONBOARDING_STATUS(true), CREATE_SIGNING_LINK(false), QUERY_ACCOUNT_INFO(true),
    ADJUST_LIMIT(false), QUERY_LIMIT_ADJUSTMENT(true);

    private final boolean safeToRetry;

    ChannelOperation(boolean safeToRetry) {
        this.safeToRetry = safeToRetry;
    }

    public boolean safeToRetry() {
        return safeToRetry;
    }
}
