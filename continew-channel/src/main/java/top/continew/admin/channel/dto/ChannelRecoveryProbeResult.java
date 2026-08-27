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

/** Sanitized outcome returned by a channel-specific query-and-apply probe. */
public record ChannelRecoveryProbeResult(Outcome outcome, Long eventRecordId, String failureCategory) {
    public ChannelRecoveryProbeResult {
        if (outcome == null || outcome == Outcome.RESOLVED && (eventRecordId == null || eventRecordId <= 0) || outcome != Outcome.RESOLVED && eventRecordId != null || failureCategory != null && failureCategory
            .length() > 64) {
            throw ChannelContracts.invalid("channel recovery probe result");
        }
    }

    public static ChannelRecoveryProbeResult resolved(Long eventRecordId) {
        return new ChannelRecoveryProbeResult(Outcome.RESOLVED, eventRecordId, null);
    }

    public static ChannelRecoveryProbeResult pending(String category) {
        return new ChannelRecoveryProbeResult(Outcome.PENDING, null, category);
    }

    public static ChannelRecoveryProbeResult retryable(String category) {
        return new ChannelRecoveryProbeResult(Outcome.RETRYABLE_FAILURE, null, category);
    }

    public static ChannelRecoveryProbeResult permanent(String category) {
        return new ChannelRecoveryProbeResult(Outcome.PERMANENT_FAILURE, null, category);
    }

    public enum Outcome { RESOLVED, PENDING, RETRYABLE_FAILURE, PERMANENT_FAILURE }
}
