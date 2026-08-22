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

package top.continew.admin.workflow.internal.flowable;

import top.continew.admin.workflow.api.WorkflowOperationException;

import java.util.regex.Pattern;

/** Parsed deterministic business key: tenant:type:id:version. */
record WorkflowBusinessKey(Long tenantId, String businessType, Long businessId, Long businessVersion, String value) {

    private static final Pattern BUSINESS_TYPE = Pattern.compile("[A-Z][A-Z0-9_]{1,63}");

    static WorkflowBusinessKey parse(Long expectedTenantId, String value) {
        if (expectedTenantId == null || expectedTenantId <= 0 || value == null || value.length() > 255) {
            throw invalid();
        }
        String[] parts = value.split(":", -1);
        if (parts.length != 4 || !BUSINESS_TYPE.matcher(parts[1]).matches()) {
            throw invalid();
        }
        try {
            Long tenantId = Long.valueOf(parts[0]);
            Long businessId = Long.valueOf(parts[2]);
            Long businessVersion = Long.valueOf(parts[3]);
            if (!expectedTenantId.equals(tenantId) || businessId <= 0 || businessVersion <= 0) {
                throw invalid();
            }
            return new WorkflowBusinessKey(tenantId, parts[1], businessId, businessVersion, value);
        } catch (NumberFormatException ex) {
            throw invalid();
        }
    }

    private static WorkflowOperationException invalid() {
        return new WorkflowOperationException(WorkflowOperationException.Code.INVALID_REQUEST);
    }
}
