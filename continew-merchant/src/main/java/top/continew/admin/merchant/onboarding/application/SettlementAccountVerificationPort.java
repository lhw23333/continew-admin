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

/** Pluggable bank/account-ownership verification boundary. */
public interface SettlementAccountVerificationPort {

    VerificationResult verify(VerificationCommand command);

    record VerificationCommand(Long tenantId, Long merchantId, Long kycVersionId, SettlementMode mode,
                               String accountHolderName, String bankCode, String bankBranchName, String accountNumber) {
        @Override
        public String toString() {
            return "VerificationCommand[tenantId=%s, merchantId=%s, kycVersionId=%s, mode=%s, account=<redacted>]"
                .formatted(tenantId, merchantId, kycVersionId, mode);
        }
    }

    record VerificationResult(SettlementVerificationStatus status, String reference, String verifierVersion) {
        public VerificationResult {
            if (status == null || verifierVersion == null || verifierVersion.isBlank()) {
                throw new IllegalArgumentException("Settlement verification result is invalid");
            }
        }
    }

    enum SettlementVerificationStatus { VERIFIED, PENDING, FAILED, UNAVAILABLE }

    enum SettlementMode { ORDINARY, ACCELERATED }
}
