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

/** Non-regressing merge output applied atomically to the merchant application. */
public record ChannelStateMergeResult(ChannelOnboardingState state, ChannelStateRanks ranks, boolean finalTerminal,
                                      boolean changed) {
    public ChannelStateMergeResult {
        if (state == null || ranks == null) {
            throw ChannelContracts.invalid("channel state merge result");
        }
    }
}
