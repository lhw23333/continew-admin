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

package top.continew.admin.service.merchant;

import java.util.Locale;

/** Server-side subordinate provisioning command; parent and login identity are not client-selectable. */
public record SubordinateAgentCreateCommand(Long tenantId, Long actorUserId, String agentNo, String name,
                                            String contactName, String contactMobile, String temporaryPassword) {

    public SubordinateAgentCreateCommand {
        if (tenantId == null || tenantId <= 0 || actorUserId == null || actorUserId <= 0) {
            throw new IllegalArgumentException("Tenant and actor must be positive");
        }
        if (agentNo == null || agentNo.isBlank() || agentNo.trim().length() > 64 || name == null || name
            .isBlank() || name.trim().length() > 100 || contactName == null || contactName.isBlank() || contactName
                .trim()
                .length() > 100 || contactMobile == null || contactMobile
                    .isBlank() || temporaryPassword == null || temporaryPassword.isBlank()) {
            throw new IllegalArgumentException("Subordinate agent fields are invalid");
        }
        agentNo = agentNo.trim().toUpperCase(Locale.ROOT);
        name = name.trim();
        contactName = contactName.trim();
        contactMobile = contactMobile.trim();
    }

    @Override
    public String toString() {
        return "SubordinateAgentCreateCommand[tenantId=%s, actorUserId=%s, agentNo=%s, name=%s, contactMobile=<redacted>, temporaryPassword=<redacted>]"
            .formatted(tenantId, actorUserId, agentNo, name);
    }
}
