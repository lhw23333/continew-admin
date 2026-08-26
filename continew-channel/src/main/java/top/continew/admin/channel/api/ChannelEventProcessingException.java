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

/** Sanitized event-processing failure without callback or domain payload details. */
public final class ChannelEventProcessingException extends RuntimeException {
    private final Code code;

    public ChannelEventProcessingException(Code code) {
        super("Channel event processing failed: " + code.name());
        this.code = code;
    }

    public Code code() {
        return code;
    }

    public enum Code {
        INVALID_EVENT, UNSUPPORTED_BUSINESS_TYPE, CONFIGURATION_UNAVAILABLE, APPLICATION_NOT_FOUND,
        APPLICATION_IDENTITY_MISMATCH, BUSINESS_VERSION_MISMATCH, BUSINESS_SERIAL_CONFLICT, EVENT_ID_CONFLICT,
        CONCURRENT_STATE_CHANGE, PERSISTENCE_FAILED
    }
}
