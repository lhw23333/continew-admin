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

package top.continew.admin.channel.api;

import top.continew.admin.channel.dto.ChannelAccountInfoQuery;
import top.continew.admin.channel.dto.ChannelAccountInfoResult;
import top.continew.admin.channel.dto.ChannelLimitAdjustmentCommand;
import top.continew.admin.channel.dto.ChannelLimitAdjustmentQuery;
import top.continew.admin.channel.dto.ChannelLimitAdjustmentResult;
import top.continew.admin.channel.dto.ChannelOnboardingSubmitCommand;
import top.continew.admin.channel.dto.ChannelRef;
import top.continew.admin.channel.dto.ChannelSigningLinkCommand;
import top.continew.admin.channel.dto.ChannelSigningLinkResult;
import top.continew.admin.channel.dto.ChannelStatusQuery;
import top.continew.admin.channel.dto.ChannelStatusResult;
import top.continew.admin.channel.dto.ChannelSubmissionResult;

/** Stable project-owned boundary implemented by each payment/channel connector. */
public interface ChannelAdapter {
    ChannelRef channel();

    ChannelSubmissionResult submitOnboarding(ChannelOnboardingSubmitCommand command);

    ChannelStatusResult queryOnboardingStatus(ChannelStatusQuery query);

    ChannelSigningLinkResult createSigningLink(ChannelSigningLinkCommand command);

    ChannelAccountInfoResult queryAccountInfo(ChannelAccountInfoQuery query);

    ChannelLimitAdjustmentResult adjustLimit(ChannelLimitAdjustmentCommand command);

    ChannelLimitAdjustmentResult queryLimitAdjustment(ChannelLimitAdjustmentQuery query);
}
