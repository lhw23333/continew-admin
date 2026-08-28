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

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Immutable runtime lookup for project-owned channel adapters. */
@Component
public class ChannelAdapterRegistry {

    private final Map<String, ChannelAdapter> adapters;

    public ChannelAdapterRegistry(List<ChannelAdapter> discovered) {
        Map<String, ChannelAdapter> indexed = new LinkedHashMap<>();
        for (ChannelAdapter adapter : discovered) {
            String channelCode = adapter.channel().channelCode().toUpperCase(Locale.ROOT);
            if (indexed.putIfAbsent(channelCode, adapter) != null) {
                throw new IllegalStateException("Duplicate channel adapter registration: " + channelCode);
            }
        }
        adapters = Map.copyOf(indexed);
    }

    public ChannelAdapter require(String channelCode) {
        String normalized = channelCode == null ? null : channelCode.trim().toUpperCase(Locale.ROOT);
        ChannelAdapter adapter = normalized == null ? null : adapters.get(normalized);
        if (adapter == null) {
            throw new ChannelAdapterException(ChannelAdapterException.Code.UNSUPPORTED_CHANNEL);
        }
        return adapter;
    }

    public boolean supports(String channelCode) {
        String normalized = channelCode == null ? null : channelCode.trim().toUpperCase(Locale.ROOT);
        return normalized != null && adapters.containsKey(normalized);
    }
}