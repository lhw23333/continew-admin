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

package top.continew.admin.merchant.master.application;

import top.continew.admin.merchant.master.domain.Merchant;

/** Masked ordinary merchant profile response. */
public record MerchantProfileView(Long merchantId, String merchantNo, String legalName, String shortName,
                                  Long owningAgentId, String contactName, String contactMobileMasked,
                                  String reviewerMobileMasked, String industry, String productDescription,
                                  Long rowVersion) {

    public static MerchantProfileView from(Merchant merchant) {
        return new MerchantProfileView(merchant.id(), merchant.merchantNo(), merchant.legalName(), merchant
            .shortName(), merchant.owningAgentId(), merchant.contactName(), merchant.contactMobile() == null
                ? null
                : merchant.contactMobile().maskedValue(), merchant.reviewerMobile() == null
                    ? null
                    : merchant.reviewerMobile().maskedValue(), merchant.industry(), merchant
                        .productDescription(), merchant.rowVersion());
    }
}
