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

import java.math.BigDecimal;

public record ChannelLimitAdjustmentCommand(ChannelCommandContext context, Long requestId, String platformCode,
                                            String currency, BigDecimal originalLimit, BigDecimal requestedLimit,
                                            BigDecimal normalizedLimit, String reasonCode) {
    public ChannelLimitAdjustmentCommand {
        if (context == null || context.businessType() != ChannelBusinessType.LIMIT_ADJUSTMENT)
            throw ChannelContracts.invalid("limit context");
        requestId = ChannelContracts.positive(requestId, "requestId");
        platformCode = ChannelContracts.code(platformCode, "platformCode");
        currency = ChannelContracts.code(currency, "currency");
        if (currency.length() != 3)
            throw ChannelContracts.invalid("currency");
        originalLimit = ChannelContracts.nonNegative(originalLimit, "originalLimit");
        requestedLimit = ChannelContracts.positive(requestedLimit, "requestedLimit");
        normalizedLimit = ChannelContracts.positive(normalizedLimit, "normalizedLimit");
        if (normalizedLimit.compareTo(requestedLimit) < 0)
            throw ChannelContracts.invalid("normalizedLimit");
        reasonCode = ChannelContracts.code(reasonCode, "reasonCode");
    }
}
