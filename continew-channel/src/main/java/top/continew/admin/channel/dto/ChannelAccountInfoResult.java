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

package top.continew.admin.channel.dto;

/** Channel account information safe for ordinary responses; complete account numbers are prohibited. */
public record ChannelAccountInfoResult(ChannelResultMeta meta, String accountReference, String accountNumberMasked,
                                       String bankCode, ChannelAccountStatus accountStatus) {
    public ChannelAccountInfoResult {
        if (meta == null || accountStatus == null)
            throw ChannelContracts.invalid("account info result");
        accountReference = accountReference == null
            ? null
            : ChannelContracts.reference(accountReference, "accountReference");
        accountNumberMasked = ChannelContracts.optionalText(accountNumberMasked, 64, "accountNumberMasked");
        if (accountNumberMasked != null && !accountNumberMasked.contains("*"))
            throw ChannelContracts.invalid("accountNumberMasked");
        bankCode = bankCode == null ? null : ChannelContracts.code(bankCode, "bankCode");
        if (accountStatus == ChannelAccountStatus.ACTIVE && (accountReference == null || accountNumberMasked == null))
            throw ChannelContracts.invalid("active account info");
    }
}
