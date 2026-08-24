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

import java.util.regex.Pattern;

/** External secret reference only; plaintext and inline encoded material are rejected. */
public record ChannelKeyReference(ChannelKeyPurpose purpose, String reference) {
    private static final Pattern REFERENCE = Pattern.compile("(?:env|vault|kms)://[A-Za-z0-9][A-Za-z0-9/_.:-]{0,190}");

    public ChannelKeyReference {
        reference = reference == null ? null : reference.trim();
        if (purpose == null || reference == null || !REFERENCE.matcher(reference).matches()) {
            throw ChannelContracts.invalid("key reference");
        }
    }

    @Override
    public String toString() {
        return "ChannelKeyReference[purpose=%s, reference=<redacted>]".formatted(purpose);
    }
}
