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

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.continew.admin.channel.adapter.synthetic.SyntheticChannelAdapter;

import java.time.Clock;

/** Explicitly enables the in-memory reference channel for local and automated acceptance. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "merchant.synthetic", name = "channel-adapter-enabled", havingValue = "true")
public class SyntheticChannelAdapterConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "syntheticChannelAdapter")
    public SyntheticChannelAdapter syntheticChannelAdapter() {
        return new SyntheticChannelAdapter(Clock.systemUTC(), true);
    }
}
