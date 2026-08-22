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

package top.continew.admin.merchant.onboarding.application;

/** Plaintext settlement account command kept only in controlled request memory. */
public record SettlementAccountSaveCommand(Long tenantId, Long actorUserId, Long merchantId, Long applicationId,
                                           SettlementAccountVerificationPort.SettlementMode mode,
                                           String accountHolderName, String bankCode, String bankBranchName,
                                           String accountNumber, Long expectedVersion, String ipAddress) {
    @Override
    public String toString() {
        return "SettlementAccountSaveCommand[tenantId=%s, actorUserId=%s, merchantId=%s, applicationId=%s, mode=%s, account=<redacted>]"
            .formatted(tenantId, actorUserId, merchantId, applicationId, mode);
    }
}
