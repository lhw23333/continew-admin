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

import org.junit.jupiter.api.Test;
import top.continew.admin.channel.dto.ChannelApplicationState;
import top.continew.admin.channel.dto.ChannelMappedStatus;
import top.continew.admin.channel.dto.ChannelOnboardingState;
import top.continew.admin.channel.dto.ChannelOperationStatus;
import top.continew.admin.channel.dto.ChannelProductKey;
import top.continew.admin.channel.dto.ChannelStageStatus;
import top.continew.admin.channel.dto.ChannelStateMergeResult;
import top.continew.admin.channel.dto.ChannelStateRanks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelStateMergerTest {
    private final ChannelStateMerger merger = new ChannelStateMerger();

    @Test
    void lateReportingSuccessFillsReportingWithoutRegressingCardBindingOrFinalState() {
        ChannelApplicationState current = state(new ChannelOnboardingState(ChannelStageStatus.NOT_STARTED, ChannelStageStatus.NOT_STARTED, ChannelStageStatus.SUCCEEDED, ChannelStageStatus.NOT_STARTED, ChannelStageStatus.PROCESSING), new ChannelStateRanks(0, 0, 40, 0, 50), false);
        ChannelMappedStatus incoming = new ChannelMappedStatus(ChannelOperationStatus.SUCCEEDED, new ChannelOnboardingState(ChannelStageStatus.SUCCEEDED, ChannelStageStatus.NOT_STARTED, ChannelStageStatus.NOT_STARTED, ChannelStageStatus.NOT_STARTED, ChannelStageStatus.NOT_STARTED), null, 20, false);

        ChannelStateMergeResult merged = merger.merge(current, incoming);

        assertTrue(merged.changed());
        assertEquals(ChannelStageStatus.SUCCEEDED, merged.state().reportingStatus());
        assertEquals(20, merged.ranks().reportingRank());
        assertEquals(ChannelStageStatus.SUCCEEDED, merged.state().cardBindingStatus());
        assertEquals(40, merged.ranks().cardBindingRank());
        assertEquals(ChannelStageStatus.PROCESSING, merged.state().finalStatus());
        assertEquals(50, merged.ranks().finalRank());
    }

    @Test
    void terminalFinalAndSucceededStagesNeverRegress() {
        ChannelApplicationState current = state(new ChannelOnboardingState(ChannelStageStatus.SUCCEEDED, ChannelStageStatus.SUCCEEDED, ChannelStageStatus.SUCCEEDED, ChannelStageStatus.SUCCEEDED, ChannelStageStatus.SUCCEEDED), new ChannelStateRanks(100, 100, 100, 100, 100), true);
        ChannelMappedStatus incoming = new ChannelMappedStatus(ChannelOperationStatus.FAILED, new ChannelOnboardingState(ChannelStageStatus.FAILED, ChannelStageStatus.FAILED, ChannelStageStatus.FAILED, ChannelStageStatus.FAILED, ChannelStageStatus.FAILED), null, 101, true);

        ChannelStateMergeResult merged = merger.merge(current, incoming);

        assertFalse(merged.changed());
        assertEquals(current.state(), merged.state());
        assertEquals(current.ranks(), merged.ranks());
        assertTrue(merged.finalTerminal());
    }

    @Test
    void notStartedAndUnknownSnapshotsCarryNoRegressionInformation() {
        ChannelApplicationState current = state(new ChannelOnboardingState(ChannelStageStatus.PROCESSING, ChannelStageStatus.NOT_STARTED, ChannelStageStatus.NOT_STARTED, ChannelStageStatus.NOT_STARTED, ChannelStageStatus.PROCESSING), new ChannelStateRanks(10, 0, 0, 0, 10), false);
        ChannelMappedStatus incoming = new ChannelMappedStatus(ChannelOperationStatus.UNCERTAIN, new ChannelOnboardingState(ChannelStageStatus.UNKNOWN, ChannelStageStatus.NOT_STARTED, ChannelStageStatus.UNKNOWN, ChannelStageStatus.NOT_STARTED, ChannelStageStatus.UNKNOWN), null, 20, false);

        ChannelStateMergeResult merged = merger.merge(current, incoming);

        assertFalse(merged.changed());
        assertEquals(current.state(), merged.state());
        assertEquals(current.ranks(), merged.ranks());
    }

    private ChannelApplicationState state(ChannelOnboardingState state,
                                          ChannelStateRanks ranks,
                                          boolean finalTerminal) {
        return new ChannelApplicationState(1L, 2L, 3L, 4L, new ChannelProductKey("SYNTHETIC", "ONBOARDING"), "CFG-1", "SERIAL-1", state, ranks, finalTerminal, 0L);
    }
}
