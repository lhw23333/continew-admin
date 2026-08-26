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

package top.continew.admin.channel.service;

import org.springframework.stereotype.Component;
import top.continew.admin.channel.dto.ChannelApplicationState;
import top.continew.admin.channel.dto.ChannelMappedStatus;
import top.continew.admin.channel.dto.ChannelOnboardingState;
import top.continew.admin.channel.dto.ChannelStageStatus;
import top.continew.admin.channel.dto.ChannelStateMergeResult;
import top.continew.admin.channel.dto.ChannelStateRanks;

/** Merges independently ranked onboarding states without allowing known progress to regress. */
@Component
public class ChannelStateMerger {

    public ChannelStateMergeResult merge(ChannelApplicationState current, ChannelMappedStatus incoming) {
        if (current == null || incoming == null || incoming.onboardingState() == null) {
            throw new IllegalArgumentException("Channel onboarding state merge input is invalid");
        }
        int rank = incoming.progressionRank();
        ChannelOnboardingState existingState = current.state();
        ChannelStateRanks existingRanks = current.ranks();
        Value reporting = merge(existingState.reportingStatus(), existingRanks.reportingRank(), incoming
            .onboardingState()
            .reportingStatus(), rank, false);
        Value signing = merge(existingState.signingStatus(), existingRanks.signingRank(), incoming.onboardingState()
            .signingStatus(), rank, false);
        Value card = merge(existingState.cardBindingStatus(), existingRanks.cardBindingRank(), incoming
            .onboardingState()
            .cardBindingStatus(), rank, false);
        Value reserve = merge(existingState.reserveAccountStatus(), existingRanks.reserveAccountRank(), incoming
            .onboardingState()
            .reserveAccountStatus(), rank, false);
        Value finalValue = merge(existingState.finalStatus(), existingRanks.finalRank(), incoming.onboardingState()
            .finalStatus(), rank, current.finalTerminal());
        ChannelOnboardingState mergedState = new ChannelOnboardingState(reporting.status(), signing.status(), card
            .status(), reserve.status(), finalValue.status());
        ChannelStateRanks mergedRanks = new ChannelStateRanks(reporting.rank(), signing.rank(), card.rank(), reserve
            .rank(), finalValue.rank());
        boolean finalTerminal = current.finalTerminal() || isImmutable(existingState.finalStatus()) || finalValue
            .changed() && incoming.terminal() && isTerminal(finalValue.status());
        boolean changed = reporting.changed() || signing.changed() || card.changed() || reserve.changed() || finalValue
            .changed() || finalTerminal != current.finalTerminal();
        return new ChannelStateMergeResult(mergedState, mergedRanks, finalTerminal, changed);
    }

    private Value merge(ChannelStageStatus current,
                        int currentRank,
                        ChannelStageStatus incoming,
                        int incomingRank,
                        boolean terminal) {
        if (terminal || isImmutable(current) || !hasInformation(incoming)) {
            return new Value(current, currentRank, false);
        }
        boolean replace = incomingRank > currentRank || !hasInformation(current) && incomingRank == currentRank;
        return replace
            ? new Value(incoming, incomingRank, incoming != current || incomingRank != currentRank)
            : new Value(current, currentRank, false);
    }

    private boolean hasInformation(ChannelStageStatus status) {
        return status != ChannelStageStatus.NOT_STARTED && status != ChannelStageStatus.UNKNOWN;
    }

    private boolean isImmutable(ChannelStageStatus status) {
        return status == ChannelStageStatus.SUCCEEDED;
    }

    private boolean isTerminal(ChannelStageStatus status) {
        return status == ChannelStageStatus.SUCCEEDED || status == ChannelStageStatus.FAILED || status == ChannelStageStatus.REJECTED;
    }

    private record Value(ChannelStageStatus status, int rank, boolean changed) {
    }
}
