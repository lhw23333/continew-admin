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
import java.time.LocalDateTime;

public record ChannelLimitAdjustmentResult(ChannelResultMeta meta, Long requestId, String platformCode, String currency,
                                           BigDecimal requestedLimit, BigDecimal effectiveLimit,
                                           ChannelLimitStatus limitStatus, LocalDateTime effectiveTime) {
    public ChannelLimitAdjustmentResult {
        if (meta == null || limitStatus == null)
            throw ChannelContracts.invalid("limit result");
        requestId = ChannelContracts.positive(requestId, "requestId");
        platformCode = ChannelContracts.code(platformCode, "platformCode");
        currency = ChannelContracts.code(currency, "currency");
        if (currency.length() != 3)
            throw ChannelContracts.invalid("currency");
        requestedLimit = ChannelContracts.positive(requestedLimit, "requestedLimit");
        effectiveLimit = effectiveLimit == null ? null : ChannelContracts.nonNegative(effectiveLimit, "effectiveLimit");
        if (limitStatus == ChannelLimitStatus.EFFECTIVE && (effectiveLimit == null || effectiveTime == null)) {
            throw ChannelContracts.invalid("effective limit result");
        }
    }
}
