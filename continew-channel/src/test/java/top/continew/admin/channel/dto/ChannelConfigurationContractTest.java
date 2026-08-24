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

import org.junit.jupiter.api.Test;
import top.continew.admin.channel.api.ChannelConfigurationException;
import top.continew.admin.channel.api.ChannelConnectionConfigCatalog;
import top.continew.admin.channel.api.ChannelSecret;
import top.continew.admin.channel.api.ChannelSecretProvider;
import top.continew.admin.channel.service.ChannelConfigurationLoader;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChannelConfigurationContractTest {

    @Test
    void keyReferencesRejectInlineMaterialAndRedactStringRendering() {
        assertThrows(IllegalArgumentException.class, () -> new ChannelKeyReference(ChannelKeyPurpose.SIGNING, "plain-secret-value"));
        ChannelKeyReference reference = new ChannelKeyReference(ChannelKeyPurpose.SIGNING, "vault://merchant/channel/signing/v1");
        assertFalse(reference.toString().contains("merchant/channel"));
    }

    @Test
    void endpointAndTimeoutPoliciesRequireEveryOperation() {
        assertThrows(IllegalArgumentException.class, () -> new ChannelEndpointConfiguration("http://channel.invalid", Map
            .of()));
        EnumMap<ChannelOperation, Duration> incomplete = new EnumMap<>(ChannelOperation.class);
        incomplete.put(ChannelOperation.SUBMIT_ONBOARDING, Duration.ofSeconds(3));
        assertThrows(IllegalArgumentException.class, () -> new ChannelTimeoutPolicy(Duration.ofSeconds(1), Duration
            .ofSeconds(3), incomplete));
    }

    @Test
    void loaderResolvesOnlyEffectiveVersionAndSecretsAreCloseable() {
        ChannelConnectionConfig config = config(ChannelConnectionStatus.ENABLED);
        ChannelConnectionConfigCatalog catalog = new ChannelConnectionConfigCatalog() {
            @Override
            public Optional<ChannelConnectionConfig> findVersion(Long tenantId,
                                                                 ChannelProductKey product,
                                                                 String configVersion) {
                return Optional.of(config);
            }

            @Override
            public Optional<ChannelConnectionConfig> findEffective(Long tenantId,
                                                                   ChannelProductKey product,
                                                                   LocalDateTime effectiveAt) {
                return Optional.of(config);
            }
        };
        ChannelSecretProvider provider = reference -> new ChannelSecret(reference.purpose(), reference
            .reference(), new byte[32]);
        var loaded = new ChannelConfigurationLoader(catalog, provider).load(1L, config.product(), config
            .configVersion(), LocalDateTime.of(2026, 8, 24, 12, 0));
        assertEquals(32, loaded.signingSecret().copyMaterial().length);
        assertFalse(loaded.toString().contains("env://"));
        loaded.close();
        assertThrows(IllegalStateException.class, () -> loaded.signingSecret().copyMaterial());
    }

    @Test
    void loaderRejectsDisabledVersionWithoutResolvingSecrets() {
        ChannelConnectionConfig config = config(ChannelConnectionStatus.DISABLED);
        ChannelConnectionConfigCatalog catalog = new FixedCatalog(config);
        ChannelSecretProvider provider = reference -> {
            throw new AssertionError("Disabled configuration must not resolve secrets");
        };
        assertThrows(ChannelConfigurationException.class, () -> new ChannelConfigurationLoader(catalog, provider)
            .load(1L, config.product(), config.configVersion(), LocalDateTime.of(2026, 8, 24, 12, 0)));
    }

    private ChannelConnectionConfig config(ChannelConnectionStatus status) {
        EnumMap<ChannelOperation, String> paths = new EnumMap<>(ChannelOperation.class);
        EnumMap<ChannelOperation, Duration> operationTimeouts = new EnumMap<>(ChannelOperation.class);
        for (ChannelOperation operation : ChannelOperation.values()) {
            paths.put(operation, "/api/" + operation.name().toLowerCase(java.util.Locale.ROOT));
            operationTimeouts.put(operation, Duration.ofSeconds(5));
        }
        ChannelOnboardingState state = new ChannelOnboardingState(ChannelStageStatus.PROCESSING, ChannelStageStatus.NOT_STARTED, ChannelStageStatus.NOT_STARTED, ChannelStageStatus.NOT_STARTED, ChannelStageStatus.PROCESSING);
        return new ChannelConnectionConfig(1L, 1L, new ChannelProductKey("SYNTHETIC", "ONBOARDING"), "CFG-1", new ChannelEndpointConfiguration("https://synthetic.invalid", paths), new ChannelTimeoutPolicy(Duration
            .ofSeconds(1), Duration.ofSeconds(5), operationTimeouts), "MAP-1", new ChannelStatusMapping(Map
                .of("PROCESSING", new ChannelMappedStatus(ChannelOperationStatus.PROCESSING, state, null, 10, false))), new ChannelKeyReferences(new ChannelKeyReference(ChannelKeyPurpose.SIGNING, "env://CHANNEL_SIGNING"), null, new ChannelKeyReference(ChannelKeyPurpose.CALLBACK_VERIFICATION, "env://CHANNEL_CALLBACK")), status, LocalDateTime
                    .of(2026, 8, 24, 0, 0), null, LocalDateTime.of(2026, 8, 23, 0, 0));
    }

    private record FixedCatalog(ChannelConnectionConfig config) implements ChannelConnectionConfigCatalog {
        @Override
        public Optional<ChannelConnectionConfig> findVersion(Long tenantId,
                                                             ChannelProductKey product,
                                                             String configVersion) {
            return Optional.of(config);
        }

        @Override
        public Optional<ChannelConnectionConfig> findEffective(Long tenantId,
                                                               ChannelProductKey product,
                                                               LocalDateTime effectiveAt) {
            return Optional.of(config);
        }
    }
}
