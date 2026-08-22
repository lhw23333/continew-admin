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

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** Strict name/type allowlist for every variable written to Flowable runtime or history. */
@Component
public class WorkflowVariablePolicy {

    private static final Pattern CHANNEL_CODE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    private static final Pattern RISK_LEVEL = Pattern.compile("[A-Z][A-Z0-9_]{0,31}");
    private static final Pattern REVIEW_ACTION = Pattern.compile("APPROVE|REJECT|REQUEST_SUPPLEMENT|RESUBMIT");
    private static final Predicate<Object> POSITIVE_LONG = value -> value instanceof Long number && number > 0;
    private static final Map<String, Predicate<Object>> ALLOWED = Map.ofEntries(Map
        .entry("tenantId", POSITIVE_LONG), Map.entry("merchantId", POSITIVE_LONG), Map
            .entry("applicationId", POSITIVE_LONG), Map.entry("kycVersion", POSITIVE_LONG), Map
                .entry("channelCode", value -> matches(value, CHANNEL_CODE)), Map
                    .entry("applicantId", POSITIVE_LONG), Map.entry("owningAgentId", POSITIVE_LONG), Map
                        .entry("riskLevel", value -> matches(value, RISK_LEVEL)), Map
                            .entry("requiresSupplement", value -> value instanceof Boolean), Map
                                .entry("reviewAction", value -> matches(value, REVIEW_ACTION)));
    private static final Set<String> EXPLICITLY_FORBIDDEN_NAMES = Set
        .of("identityNumber", "legalIdentifier", "bankAccount", "bankAccountNumber", "mobile", "mobileNumber", "password", "paymentPassword", "credential", "kyc", "kycJson", "attachmentUrl", "channelPayload", "binary");

    public Map<String, Object> validateAndCopy(Map<String, ?> variables) {
        if (variables == null || variables.isEmpty()) {
            return Map.of();
        }
        if (variables.size() > ALLOWED.size()) {
            throw new InvalidWorkflowVariableException("<collection>", "too many variables");
        }
        Map<String, Object> validated = new LinkedHashMap<>(variables.size());
        variables.forEach((name, value) -> {
            if (EXPLICITLY_FORBIDDEN_NAMES.contains(name) || !ALLOWED.containsKey(name)) {
                throw new InvalidWorkflowVariableException(name, "name is not allowlisted");
            }
            if (value == null || !ALLOWED.get(name).test(value)) {
                throw new InvalidWorkflowVariableException(name, "type or format is invalid");
            }
            validated.put(name, value);
        });
        return Map.copyOf(validated);
    }

    public Set<String> allowedNames() {
        return ALLOWED.keySet();
    }

    private static boolean matches(Object value, Pattern pattern) {
        return value instanceof String text && pattern.matcher(text).matches();
    }
}
