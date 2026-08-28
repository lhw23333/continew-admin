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

package top.continew.admin.merchant.limit.application;

import top.continew.admin.merchant.master.domain.MerchantDomainException;

/** Sanitized revalidation conflict raised before final approval or channel execution. */
public final class LimitAdjustmentRevalidationException extends MerchantDomainException {

    private final Code code;

    public LimitAdjustmentRevalidationException(Code code) {
        super("Limit adjustment requires revalidation: " + code.name());
        this.code = code;
    }

    public Code code() {
        return code;
    }

    public enum Code {
        EFFECTIVE_LIMIT_CHANGED, ELIGIBILITY_CHANGED, CHANNEL_CONFIGURATION_CHANGED, AMOUNT_POLICY_CHANGED,
        NORMALIZED_LIMIT_CHANGED
    }
}