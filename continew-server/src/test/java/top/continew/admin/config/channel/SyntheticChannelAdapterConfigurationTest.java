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
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import top.continew.admin.channel.adapter.synthetic.SyntheticChannelAdapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyntheticChannelAdapterConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(SyntheticChannelAdapterConfiguration.class);

    @Test
    void keepsSyntheticAdapterDisabledByDefault() {
        contextRunner.run(context -> assertFalse(context.containsBean("syntheticChannelAdapter")));
    }

    @Test
    void registersSyntheticAdapterWhenExplicitlyEnabled() {
        contextRunner.withPropertyValues("merchant.synthetic.channel-adapter-enabled=true").run(context -> {
            assertTrue(context.containsBean("syntheticChannelAdapter"));
            assertEquals(SyntheticChannelAdapter.CHANNEL_CODE, context
                .getBean(SyntheticChannelAdapter.class)
                .channel()
                .channelCode());
        });
    }
}
