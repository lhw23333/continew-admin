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

package top.continew.admin.merchant.security.reveal;

import top.continew.admin.merchant.master.domain.Merchant;
import top.continew.admin.merchant.security.crypto.SensitiveValueProtector;

/** Server-owned field policy; clients cannot supply database column names or decryption purposes. */
public enum MerchantSensitiveField {
    CONTACT_MOBILE {
        @Override
        public String maskedValue(Merchant merchant) {
            requireAvailable(merchant);
            return merchant.contactMobile().maskedValue();
        }

        @Override
        String reveal(Merchant merchant, SensitiveValueProtector protector) {
            requireAvailable(merchant);
            return merchant.contactMobile().reveal(protector);
        }

        private void requireAvailable(Merchant merchant) {
            if (merchant.contactMobile() == null) {
                throw new PrivilegedRevealDeniedException();
            }
        }
    };

    public abstract String maskedValue(Merchant merchant);

    abstract String reveal(Merchant merchant, SensitiveValueProtector protector);
}
