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

package top.continew.admin.channel.api;

import top.continew.admin.channel.dto.ChannelConnectionConfig;

/** Exact callback configuration with only the verification secret resolved. */
public final class LoadedChannelCallbackConfiguration implements AutoCloseable {
    private final ChannelConnectionConfig config;
    private final ChannelSecret verificationSecret;

    public LoadedChannelCallbackConfiguration(ChannelConnectionConfig config, ChannelSecret verificationSecret) {
        if (config == null || verificationSecret == null) {
            throw new IllegalArgumentException("Loaded callback configuration is invalid");
        }
        this.config = config;
        this.verificationSecret = verificationSecret;
    }

    public ChannelConnectionConfig config() {
        return config;
    }

    public ChannelSecret verificationSecret() {
        return verificationSecret;
    }

    @Override
    public void close() {
        verificationSecret.close();
    }

    @Override
    public String toString() {
        return "LoadedChannelCallbackConfiguration[configId=%s, configVersion=%s, verificationSecret=<redacted>]"
            .formatted(config.id(), config.configVersion());
    }
}
