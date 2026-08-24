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
import top.continew.admin.channel.api.ChannelConfigurationException;
import top.continew.admin.channel.api.ChannelConnectionConfigCatalog;
import top.continew.admin.channel.api.ChannelSecret;
import top.continew.admin.channel.api.ChannelSecretProvider;
import top.continew.admin.channel.api.LoadedChannelConfiguration;
import top.continew.admin.channel.dto.ChannelConnectionConfig;
import top.continew.admin.channel.dto.ChannelProductKey;

import java.time.LocalDateTime;

/** Fails closed when a version is inactive or required external secret material cannot be resolved. */
@Service
@RequiredArgsConstructor
public class ChannelConfigurationLoader {
    private final ChannelConnectionConfigCatalog catalog;
    private final ChannelSecretProvider secretProvider;

    public LoadedChannelConfiguration load(Long tenantId,
                                           ChannelProductKey product,
                                           String configVersion,
                                           LocalDateTime effectiveAt) {
        ChannelConnectionConfig config = catalog.findVersion(tenantId, product, configVersion)
            .orElseThrow(() -> unavailable("Channel configuration is unavailable"));
        if (effectiveAt == null || !config.isEffectiveAt(effectiveAt)) {
            throw unavailable("Channel configuration is not effective");
        }
        ChannelSecret signing = null;
        ChannelSecret encryption = null;
        try {
            signing = secretProvider.resolve(config.keyReferences().signing());
            if (config.keyReferences().encryption() != null) {
                encryption = secretProvider.resolve(config.keyReferences().encryption());
            }
            ChannelSecret callback = secretProvider.resolve(config.keyReferences().callbackVerification());
            return new LoadedChannelConfiguration(config, signing, encryption, callback);
        } catch (RuntimeException ex) {
            if (signing != null)
                signing.close();
            if (encryption != null)
                encryption.close();
            if (ex instanceof ChannelConfigurationException configurationException)
                throw configurationException;
            throw unavailable("Channel secret material is unavailable");
        }
    }

    private ChannelConfigurationException unavailable(String message) {
        return new ChannelConfigurationException(message);
    }
}
