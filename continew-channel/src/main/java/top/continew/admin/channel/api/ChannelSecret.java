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

import top.continew.admin.channel.dto.ChannelKeyPurpose;

import java.util.Arrays;

/** Closeable secret material with defensive copies and redacted string rendering. */
public final class ChannelSecret implements AutoCloseable {
    private final ChannelKeyPurpose purpose;
    private final String reference;
    private byte[] material;

    public ChannelSecret(ChannelKeyPurpose purpose, String reference, byte[] material) {
        if (purpose == null || reference == null || reference
            .isBlank() || material == null || material.length < 16 || material.length > 16384) {
            throw new IllegalArgumentException("Channel secret is invalid");
        }
        this.purpose = purpose;
        this.reference = reference;
        this.material = Arrays.copyOf(material, material.length);
    }

    public ChannelKeyPurpose purpose() {
        return purpose;
    }

    public String reference() {
        return reference;
    }

    public synchronized byte[] copyMaterial() {
        if (material == null)
            throw new IllegalStateException("Channel secret is closed");
        return Arrays.copyOf(material, material.length);
    }

    @Override
    public synchronized void close() {
        if (material != null) {
            Arrays.fill(material, (byte)0);
            material = null;
        }
    }

    @Override
    public String toString() {
        return "ChannelSecret[purpose=%s, reference=<redacted>, material=<redacted>]".formatted(purpose);
    }
}
