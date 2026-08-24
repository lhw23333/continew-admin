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

import java.net.URI;
import java.util.EnumMap;
import java.util.Map;

/** HTTPS base endpoint plus relative operation paths. */
public record ChannelEndpointConfiguration(String baseUrl, Map<ChannelOperation, String> operationPaths) {
    public ChannelEndpointConfiguration {
        try {
            URI uri = URI.create(baseUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || uri
                .getQuery() != null || uri.getFragment() != null) {
                throw ChannelContracts.invalid("baseUrl");
            }
            baseUrl = uri.toString().replaceAll("/+$", "");
        } catch (RuntimeException ex) {
            throw ChannelContracts.invalid("baseUrl");
        }
        if (operationPaths == null || operationPaths.size() != ChannelOperation.values().length) {
            throw ChannelContracts.invalid("operationPaths");
        }
        EnumMap<ChannelOperation, String> normalized = new EnumMap<>(ChannelOperation.class);
        operationPaths.forEach((operation, path) -> {
            String value = path == null ? null : path.trim();
            if (operation == null || value == null || !value.startsWith("/") || value.length() > 255 || value
                .contains("..") || value.contains("?") || value.contains("#") || value.chars()
                    .anyMatch(Character::isISOControl)) {
                throw ChannelContracts.invalid("operationPaths");
            }
            normalized.put(operation, value);
        });
        operationPaths = Map.copyOf(normalized);
    }
}
