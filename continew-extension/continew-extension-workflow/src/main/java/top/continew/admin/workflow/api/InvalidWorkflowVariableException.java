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

/** Sanitized rejection that never includes the submitted variable value. */
public final class InvalidWorkflowVariableException extends IllegalArgumentException {

    public InvalidWorkflowVariableException(String variableName, String reason) {
        super("Workflow variable '%s' is rejected: %s".formatted(sanitizeName(variableName), reason));
    }

    private static String sanitizeName(String variableName) {
        if (variableName == null) {
            return "<null>";
        }
        return variableName.replaceAll("[^A-Za-z0-9_.-]", "?").substring(0, Math.min(variableName.length(), 64));
    }
}
