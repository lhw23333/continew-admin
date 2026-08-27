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
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.regex.Pattern;

/** Short-lived channel evidence reference; the URL is deliberately redacted from diagnostics. */
public record ChannelEvidenceAccess(Long objectId, String evidenceType, String sha256, String detectedMime,
                                    Long sizeBytes, URI url, LocalDateTime expiresAt) {

    private static final Pattern SHA_256 = Pattern.compile("[a-f0-9]{64}");

    public ChannelEvidenceAccess {
        objectId = ChannelContracts.positive(objectId, "objectId");
        evidenceType = ChannelContracts.code(evidenceType, "evidenceType");
        sha256 = sha256 == null ? null : sha256.trim().toLowerCase(Locale.ROOT);
        if (sha256 == null || !SHA_256.matcher(sha256).matches())
            throw ChannelContracts.invalid("sha256");
        detectedMime = ChannelContracts.optionalText(detectedMime, 128, "detectedMime");
        if (detectedMime == null)
            throw ChannelContracts.invalid("detectedMime");
        sizeBytes = ChannelContracts.positive(sizeBytes, "sizeBytes");
        if (url == null || !url.isAbsolute() || url.getUserInfo() != null || !("https".equalsIgnoreCase(url
            .getScheme()) || "http".equalsIgnoreCase(url.getScheme())) || expiresAt == null) {
            throw ChannelContracts.invalid("temporary evidence access");
        }
    }

    @Override
    public String toString() {
        return "ChannelEvidenceAccess[objectId=%s, evidenceType=%s, sha256=%s, detectedMime=%s, sizeBytes=%s, " + "url=<redacted>, expiresAt=%s]"
            .formatted(objectId, evidenceType, sha256, detectedMime, sizeBytes, expiresAt);
    }
}
