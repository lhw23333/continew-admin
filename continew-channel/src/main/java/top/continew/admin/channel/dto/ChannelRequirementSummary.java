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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Non-sensitive requirement summary returned before an onboarding draft is created. */
public record ChannelRequirementSummary(List<String> requiredEvidenceTypes, List<String> optionalEvidenceTypes,
                                        Integer maxSupplementAttachments) {

    public ChannelRequirementSummary {
        requiredEvidenceTypes = normalized(requiredEvidenceTypes, "requiredEvidenceTypes");
        optionalEvidenceTypes = normalized(optionalEvidenceTypes, "optionalEvidenceTypes");
        Set<String> overlap = new HashSet<>(requiredEvidenceTypes);
        overlap.retainAll(optionalEvidenceTypes);
        if (!overlap.isEmpty()) {
            throw new IllegalArgumentException("Evidence requirement types must be distinct");
        }
        if (maxSupplementAttachments == null || maxSupplementAttachments < 0 || maxSupplementAttachments > 100) {
            throw new IllegalArgumentException("maxSupplementAttachments is invalid");
        }
    }

    private static List<String> normalized(List<String> values, String name) {
        if (values == null || values.size() > 100) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        List<String> normalized = values.stream().map(value -> {
            if (value == null || value.isBlank() || value.trim().length() > 64) {
                throw new IllegalArgumentException(name + " contains an invalid value");
            }
            return value.trim();
        }).distinct().toList();
        if (normalized.size() != values.size()) {
            throw new IllegalArgumentException(name + " contains duplicate values");
        }
        return normalized;
    }
}
