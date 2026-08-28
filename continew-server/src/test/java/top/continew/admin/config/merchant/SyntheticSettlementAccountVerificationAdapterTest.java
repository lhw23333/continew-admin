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

package top.continew.admin.config.merchant;

import org.junit.jupiter.api.Test;
import top.continew.admin.merchant.onboarding.application.SettlementAccountVerificationPort;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SyntheticSettlementAccountVerificationAdapterTest {

    @Test
    void shouldVerifyOnlyWhenExplicitlySelectedByConfiguration() {
        SyntheticSettlementAccountVerificationAdapter adapter = new SyntheticSettlementAccountVerificationAdapter();
        SettlementAccountVerificationPort.VerificationCommand command = new SettlementAccountVerificationPort.VerificationCommand(
            1L, 2L, 3L, SettlementAccountVerificationPort.SettlementMode.ORDINARY, "holder", "bank", "branch", "account");
        SettlementAccountVerificationPort.VerificationResult result = adapter.verify(command);

        assertEquals(SettlementAccountVerificationPort.SettlementVerificationStatus.VERIFIED, result.status());
        assertEquals("SYNTHETIC-3", result.reference());
        assertEquals("SYNTHETIC_VERIFIER_V1", result.verifierVersion());
    }
}
