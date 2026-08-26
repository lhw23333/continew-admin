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

/** Sanitized secure-transport failure without endpoint, payload, signature, or key material. */
public final class ChannelTransportException extends RuntimeException {
    private final Code code;
    private final TransmissionState transmissionState;

    public ChannelTransportException(Code code) {
        this(code, TransmissionState.UNKNOWN);
    }

    public ChannelTransportException(Code code, TransmissionState transmissionState) {
        super("Channel transport operation failed: " + safeName(code));
        if (transmissionState == null) {
            throw new IllegalArgumentException("Channel transport failure is invalid");
        }
        this.code = code;
        this.transmissionState = transmissionState;
    }

    public Code code() {
        return code;
    }

    public TransmissionState transmissionState() {
        return transmissionState;
    }

    public enum TransmissionState { NOT_SENT, SENT, UNKNOWN }

    public enum Code {
        CONFIGURATION_UNAVAILABLE, SIGNING_FAILED, ENCRYPTION_FAILED, AUDIT_FAILED, TIMEOUT, CIRCUIT_OPEN,
        BULKHEAD_FULL, TRANSPORT_FAILED, UNCERTAIN_RESULT
    }

    private static String safeName(Code code) {
        if (code == null) {
            throw new IllegalArgumentException("Channel transport failure is invalid");
        }
        return code.name();
    }
}
