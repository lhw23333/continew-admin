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

package top.continew.admin.merchant.onboarding.application;

import top.continew.admin.channel.dto.ChannelSigningAction;
import top.continew.admin.merchant.security.crypto.VersionedKeyProvider;

import javax.crypto.Mac;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;

/** Compact HMAC-SHA256 token codec with a structured binary payload and constant-time verification. */
public final class OnboardingProcessReferenceTokenService {

    private static final int FORMAT_VERSION = 1;
    private static final int NONCE_BYTES = 16;
    private static final int MAX_TOKEN_LENGTH = 2048;
    private static final Duration MAX_VALIDITY = Duration.ofMinutes(30);
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final VersionedKeyProvider keyProvider;
    private final Clock clock;
    private final SecureRandom secureRandom;

    public OnboardingProcessReferenceTokenService(VersionedKeyProvider keyProvider) {
        this(keyProvider, Clock.systemUTC(), new SecureRandom());
    }

    public OnboardingProcessReferenceTokenService(VersionedKeyProvider keyProvider,
                                                  Clock clock,
                                                  SecureRandom secureRandom) {
        this.keyProvider = Objects.requireNonNull(keyProvider, "keyProvider");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
    }

    public IssuedToken issue(Long tenantId,
                             Long merchantId,
                             Long applicationId,
                             String channelCode,
                             ChannelSigningAction action,
                             Duration validity) {
        Instant issuedAt = clock.instant();
        if (validity == null || validity.isZero() || validity.isNegative() || validity.compareTo(MAX_VALIDITY) > 0) {
            throw new ProcessReferenceException(ProcessReferenceException.Code.INVALID);
        }
        OnboardingProcessReferenceClaims claims = new OnboardingProcessReferenceClaims(tenantId, merchantId, applicationId, normalizeChannel(channelCode), action, issuedAt, issuedAt
            .plus(validity));
        byte[] payload = encode(claims);
        byte[] signature = sign(payload);
        return new IssuedToken(ENCODER.encodeToString(payload) + '.' + ENCODER.encodeToString(signature), claims);
    }

    public OnboardingProcessReferenceClaims verify(String token) {
        try {
            if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
                throw invalid();
            }
            String[] parts = token.split("\\.", -1);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw invalid();
            }
            byte[] payload = DECODER.decode(parts[0]);
            byte[] suppliedSignature = DECODER.decode(parts[1]);
            byte[] expectedSignature = sign(payload);
            if (suppliedSignature.length != expectedSignature.length || !MessageDigest
                .isEqual(expectedSignature, suppliedSignature)) {
                throw invalid();
            }
            OnboardingProcessReferenceClaims claims = decode(payload);
            Instant now = clock.instant();
            if (!now.isBefore(claims.expiresAt())) {
                throw new ProcessReferenceException(ProcessReferenceException.Code.EXPIRED);
            }
            if (claims.issuedAt().isAfter(now.plusSeconds(30)) || Duration.between(claims.issuedAt(), claims
                .expiresAt()).compareTo(MAX_VALIDITY) > 0) {
                throw invalid();
            }
            return claims;
        } catch (ProcessReferenceException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw invalid();
        }
    }

    private byte[] encode(OnboardingProcessReferenceClaims claims) {
        byte[] nonce = new byte[NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeByte(FORMAT_VERSION);
            output.writeLong(claims.tenantId());
            output.writeLong(claims.merchantId());
            output.writeLong(claims.applicationId());
            output.writeUTF(claims.channelCode());
            output.writeUTF(claims.action().name());
            output.writeLong(claims.issuedAt().getEpochSecond());
            output.writeLong(claims.expiresAt().getEpochSecond());
            output.write(nonce);
            return bytes.toByteArray();
        } catch (IOException ex) {
            throw new ProcessReferenceException(ProcessReferenceException.Code.INVALID, ex);
        }
    }

    private OnboardingProcessReferenceClaims decode(byte[] payload) {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
            if (input.readUnsignedByte() != FORMAT_VERSION) {
                throw invalid();
            }
            Long tenantId = input.readLong();
            Long merchantId = input.readLong();
            Long applicationId = input.readLong();
            String channelCode = normalizeChannel(input.readUTF());
            ChannelSigningAction action = ChannelSigningAction.valueOf(input.readUTF());
            Instant issuedAt = Instant.ofEpochSecond(input.readLong());
            Instant expiresAt = Instant.ofEpochSecond(input.readLong());
            if (input.readNBytes(NONCE_BYTES).length != NONCE_BYTES || input.read() != -1) {
                throw invalid();
            }
            return new OnboardingProcessReferenceClaims(tenantId, merchantId, applicationId, channelCode, action, issuedAt, expiresAt);
        } catch (IOException | IllegalArgumentException ex) {
            throw invalid();
        }
    }

    private byte[] sign(byte[] payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(keyProvider.currentHashKey().key());
            mac.update("ONBOARDING_PROCESS_REFERENCE_V1".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            mac.update((byte)0);
            return mac.doFinal(payload);
        } catch (GeneralSecurityException | RuntimeException ex) {
            throw new ProcessReferenceException(ProcessReferenceException.Code.KEY_UNAVAILABLE, ex);
        }
    }

    private String normalizeChannel(String channelCode) {
        String normalized = channelCode == null ? null : channelCode.trim().toUpperCase(Locale.ROOT);
        if (normalized == null || !normalized.matches("[A-Z0-9][A-Z0-9._-]{0,63}")) {
            throw invalid();
        }
        return normalized;
    }

    private ProcessReferenceException invalid() {
        return new ProcessReferenceException(ProcessReferenceException.Code.INVALID);
    }

    public record IssuedToken(String token, OnboardingProcessReferenceClaims claims) {

        public IssuedToken {
            if (token == null || token.isBlank() || claims == null) {
                throw new IllegalArgumentException("Issued process reference token is invalid");
            }
        }

        @Override
        public String toString() {
            return "IssuedToken[token=<redacted>, claims=%s]".formatted(claims);
        }
    }
}
