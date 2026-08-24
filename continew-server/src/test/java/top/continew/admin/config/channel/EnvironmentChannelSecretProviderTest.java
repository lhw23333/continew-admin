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

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import top.continew.admin.channel.api.ChannelConfigurationException;
import top.continew.admin.channel.dto.ChannelKeyPurpose;
import top.continew.admin.channel.dto.ChannelKeyReference;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EnvironmentChannelSecretProviderTest {
    @Test
    void resolvesBase64EnvironmentReferenceAndClosesMaterial() {
        MockEnvironment environment = new MockEnvironment().withProperty("CHANNEL_SIGNING_TEST", Base64.getEncoder()
            .encodeToString(new byte[32]));
        EnvironmentChannelSecretProvider provider = new EnvironmentChannelSecretProvider(environment);
        var secret = provider.resolve(new ChannelKeyReference(ChannelKeyPurpose.SIGNING, "env://CHANNEL_SIGNING_TEST"));
        assertEquals(32, secret.copyMaterial().length);
        assertFalse(secret.toString().contains("CHANNEL_SIGNING_TEST"));
        secret.close();
        assertThrows(IllegalStateException.class, secret::copyMaterial);
    }

    @Test
    void unsupportedReferenceFailsWithoutLeakingReference() {
        EnvironmentChannelSecretProvider provider = new EnvironmentChannelSecretProvider(new MockEnvironment());
        ChannelConfigurationException exception = assertThrows(ChannelConfigurationException.class, () -> provider
            .resolve(new ChannelKeyReference(ChannelKeyPurpose.SIGNING, "vault://merchant/channel/signing/v1")));
        assertFalse(exception.getMessage().contains("vault://"));
    }
}
