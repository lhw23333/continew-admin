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

package top.continew.admin.config.channel;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import top.continew.admin.channel.api.ChannelConfigurationException;
import top.continew.admin.channel.api.ChannelSecret;
import top.continew.admin.channel.api.ChannelSecretProvider;
import top.continew.admin.channel.dto.ChannelKeyReference;

import java.util.Arrays;
import java.util.Base64;

/** Resolves {@code env://VARIABLE} channel secret references as Base64 without logging names or values. */
@Component
public class EnvironmentChannelSecretProvider implements ChannelSecretProvider {
    private static final String PREFIX = "env://";
    private final Environment environment;

    public EnvironmentChannelSecretProvider(Environment environment) {
        this.environment = environment;
    }

    @Override
    public ChannelSecret resolve(ChannelKeyReference reference) {
        if (reference == null || !reference.reference().startsWith(PREFIX) || reference.reference().length() <= PREFIX
            .length()) {
            throw unavailable();
        }
        String encoded = environment.getProperty(reference.reference().substring(PREFIX.length()));
        if (encoded == null || encoded.isBlank())
            throw unavailable();
        byte[] material;
        try {
            material = Base64.getDecoder().decode(encoded.trim());
        } catch (IllegalArgumentException ex) {
            throw unavailable();
        }
        try {
            return new ChannelSecret(reference.purpose(), reference.reference(), material);
        } catch (IllegalArgumentException ex) {
            throw unavailable();
        } finally {
            Arrays.fill(material, (byte)0);
        }
    }

    private ChannelConfigurationException unavailable() {
        return new ChannelConfigurationException("Channel secret material is unavailable");
    }
}
