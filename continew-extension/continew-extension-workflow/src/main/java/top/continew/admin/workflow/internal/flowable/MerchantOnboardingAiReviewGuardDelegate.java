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

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;
import top.continew.admin.workflow.api.WorkflowVariablePolicy;

/**
 * Fail-closed guard for the optional AI hook. It validates identifier-only routing context and deliberately never
 * completes or replaces the following human review.
 */
@Component("merchantOnboardingAiReviewGuardDelegate")
public class MerchantOnboardingAiReviewGuardDelegate implements JavaDelegate {

    private final WorkflowVariablePolicy variablePolicy;

    public MerchantOnboardingAiReviewGuardDelegate(WorkflowVariablePolicy variablePolicy) {
        this.variablePolicy = variablePolicy;
    }

    @Override
    public void execute(DelegateExecution execution) {
        variablePolicy.validateAndCopy(execution.getVariables());
    }
}
