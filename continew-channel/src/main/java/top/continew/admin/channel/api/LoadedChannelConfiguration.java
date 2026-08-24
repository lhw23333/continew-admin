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

/** Effective connection configuration and closeable resolved secret handles. */
public final class LoadedChannelConfiguration implements AutoCloseable {
    private final ChannelConnectionConfig config;
    private final ChannelSecret signingSecret;
    private final ChannelSecret encryptionSecret;
    private final ChannelSecret callbackVerificationSecret;

    public LoadedChannelConfiguration(ChannelConnectionConfig config,
                                      ChannelSecret signingSecret,
                                      ChannelSecret encryptionSecret,
                                      ChannelSecret callbackVerificationSecret) {
        if (config == null || signingSecret == null || callbackVerificationSecret == null) {
            throw new IllegalArgumentException("Loaded channel configuration is invalid");
        }
        this.config = config;
        this.signingSecret = signingSecret;
        this.encryptionSecret = encryptionSecret;
        this.callbackVerificationSecret = callbackVerificationSecret;
    }

    public ChannelConnectionConfig config() {
        return config;
    }

    public ChannelSecret signingSecret() {
        return signingSecret;
    }

    public ChannelSecret encryptionSecret() {
        return encryptionSecret;
    }

    public ChannelSecret callbackVerificationSecret() {
        return callbackVerificationSecret;
    }

    @Override
    public void close() {
        signingSecret.close();
        if (encryptionSecret != null)
            encryptionSecret.close();
        callbackVerificationSecret.close();
    }

    @Override
    public String toString() {
        return "LoadedChannelConfiguration[configId=%s, configVersion=%s, secrets=<redacted>]".formatted(config
            .id(), config.configVersion());
    }
}
