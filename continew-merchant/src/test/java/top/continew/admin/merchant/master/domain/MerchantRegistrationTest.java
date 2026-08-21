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

package top.continew.admin.merchant.master.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MerchantRegistrationTest {

    @Test
    void legalSubjectHashIsNormalizedToLowercase() {
        MerchantRegistration registration = new MerchantRegistration(1L, 2L, 3L, "M-1", MerchantType.ENTERPRISE, "Synthetic Legal", "Synthetic", "ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789", 4L, 5L, "Contact", null, "Technology", "Synthetic merchant");

        assertEquals("abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789", registration
            .legalSubjectHash());
    }
}
