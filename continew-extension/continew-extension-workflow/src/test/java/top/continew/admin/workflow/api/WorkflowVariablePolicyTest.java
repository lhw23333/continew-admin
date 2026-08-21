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

package top.continew.admin.workflow.api;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowVariablePolicyTest {

    private final WorkflowVariablePolicy policy = new WorkflowVariablePolicy();

    @Test
    void acceptsOnlyApprovedIdentifiersAndRoutingMetadata() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("tenantId", 1001L);
        variables.put("merchantId", 2001L);
        variables.put("applicationId", 3001L);
        variables.put("kycVersion", 4L);
        variables.put("channelCode", "GRG_PAY-01");
        variables.put("applicantId", 4001L);
        variables.put("owningAgentId", 5001L);
        variables.put("riskLevel", "MEDIUM_RISK");
        variables.put("requiresSupplement", Boolean.FALSE);

        Map<String, Object> validated = policy.validateAndCopy(variables);

        assertEquals(variables, validated);
        assertThrows(UnsupportedOperationException.class, () -> validated.put("tenantId", 2L));
        assertEquals(9, policy.allowedNames().size());
    }

    @Test
    void rejectsRawKycCredentialAndSensitiveFieldNames() {
        for (String name : new String[] {"identityNumber", "bankAccountNumber", "mobile", "password", "kycJson",
            "credential", "channelPayload", "attachmentUrl"}) {
            InvalidWorkflowVariableException exception = assertThrows(InvalidWorkflowVariableException.class, () -> policy
                .validateAndCopy(Map.of(name, "synthetic-secret")));
            assertFalse(exception.getMessage().contains("synthetic-secret"));
        }
    }

    @Test
    void rejectsBinaryObjectsCollectionsAndPermanentUrlsEvenUnderAllowedNames() {
        assertThrows(InvalidWorkflowVariableException.class, () -> policy.validateAndCopy(Map
            .of("merchantId", new byte[] {1, 2, 3})));
        assertThrows(InvalidWorkflowVariableException.class, () -> policy.validateAndCopy(Map.of("merchantId", Map
            .of("identityNumber", "synthetic"))));
        assertThrows(InvalidWorkflowVariableException.class, () -> policy.validateAndCopy(Map
            .of("merchantId", new SyntheticKyc("synthetic"))));
        assertThrows(InvalidWorkflowVariableException.class, () -> policy.validateAndCopy(Map.of("channelCode", URI
            .create("https://storage.example/private/object"))));
        assertThrows(InvalidWorkflowVariableException.class, () -> policy.validateAndCopy(Map
            .of("channelCode", "https://storage.example/private/object")));
    }

    @Test
    void rejectsWrongScalarTypesInvalidFormatsAndUnknownNames() {
        assertThrows(InvalidWorkflowVariableException.class, () -> policy.validateAndCopy(Map.of("tenantId", "1001")));
        assertThrows(InvalidWorkflowVariableException.class, () -> policy.validateAndCopy(Map.of("merchantId", -1L)));
        assertThrows(InvalidWorkflowVariableException.class, () -> policy.validateAndCopy(Map
            .of("requiresSupplement", "false")));
        assertThrows(InvalidWorkflowVariableException.class, () -> policy.validateAndCopy(Map
            .of("riskLevel", "medium")));
        assertThrows(InvalidWorkflowVariableException.class, () -> policy.validateAndCopy(Map.of("unknown", 1L)));
        assertTrue(policy.validateAndCopy(Map.of()).isEmpty());
    }

    private record SyntheticKyc(String identityNumber) {
    }
}
