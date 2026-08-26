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

/** Independent mapping progression ranks for each onboarding sub-state. */
public record ChannelStateRanks(Integer reportingRank, Integer signingRank, Integer cardBindingRank,
                                Integer reserveAccountRank, Integer finalRank) {
    public ChannelStateRanks {
        if (!valid(reportingRank) || !valid(signingRank) || !valid(cardBindingRank) || !valid(reserveAccountRank) || !valid(finalRank)) {
            throw ChannelContracts.invalid("channel state ranks");
        }
    }

    public static ChannelStateRanks initial() {
        return new ChannelStateRanks(0, 0, 0, 0, 0);
    }

    private static boolean valid(Integer value) {
        return value != null && value >= 0 && value <= 100000;
    }
}
