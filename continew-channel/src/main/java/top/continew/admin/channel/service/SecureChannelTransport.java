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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.continew.admin.channel.api.ChannelConfigurationException;
import top.continew.admin.channel.api.ChannelRecoveryRegistrationPort;
import top.continew.admin.channel.api.ChannelSecret;
import top.continew.admin.channel.api.ChannelTransportAuditPort;
import top.continew.admin.channel.api.ChannelTransportClient;
import top.continew.admin.channel.api.ChannelTransportException;
import top.continew.admin.channel.api.LoadedChannelConfiguration;
import top.continew.admin.channel.dto.ChannelCommandContext;
import top.continew.admin.channel.dto.ChannelConnectionConfig;
import top.continew.admin.channel.dto.ChannelOperation;
import top.continew.admin.channel.dto.ChannelOutboundRequest;
import top.continew.admin.channel.dto.ChannelRecoveryDraft;
import top.continew.admin.channel.dto.ChannelTransportAuditRecord;
import top.continew.admin.channel.dto.ChannelTransportOutcome;
import top.continew.admin.channel.dto.ChannelTransportResponse;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;

/** Prepares, audits, and exchanges authenticated channel requests without logging provider payloads. */
@Service
public class SecureChannelTransport {
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();
    private static final int REQUEST_NONCE_BYTES = 18;
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final ChannelConfigurationLoader configurationLoader;
    private final ChannelTransportAuditPort auditPort;
    private final ChannelResilienceExecutor resilienceExecutor;
    private final ChannelRecoveryRegistrationPort recoveryRegistration;
    private final ChannelRecoveryPolicy recoveryPolicy;
    private final Clock clock;
    private final SecureRandom secureRandom;

    @Autowired
    public SecureChannelTransport(ChannelConfigurationLoader configurationLoader,
                                  ChannelTransportAuditPort auditPort,
                                  ChannelResilienceExecutor resilienceExecutor,
                                  ChannelRecoveryRegistrationPort recoveryRegistration,
                                  ChannelRecoveryPolicy recoveryPolicy) {
        this(configurationLoader, auditPort, resilienceExecutor, recoveryRegistration, recoveryPolicy, Clock
            .systemUTC(), new SecureRandom());
    }

    SecureChannelTransport(ChannelConfigurationLoader configurationLoader,
                           ChannelTransportAuditPort auditPort,
                           Clock clock,
                           SecureRandom secureRandom) {
        this(configurationLoader, auditPort, new ChannelResilienceExecutor(clock, duration -> {
        }), draft -> 1L, new ChannelRecoveryPolicy(), clock, secureRandom);
    }

    SecureChannelTransport(ChannelConfigurationLoader configurationLoader,
                           ChannelTransportAuditPort auditPort,
                           ChannelResilienceExecutor resilienceExecutor,
                           ChannelRecoveryRegistrationPort recoveryRegistration,
                           ChannelRecoveryPolicy recoveryPolicy,
                           Clock clock,
                           SecureRandom secureRandom) {
        this.configurationLoader = configurationLoader;
        this.auditPort = auditPort;
        this.resilienceExecutor = resilienceExecutor;
        this.recoveryRegistration = recoveryRegistration;
        this.recoveryPolicy = recoveryPolicy;
        this.clock = clock;
        this.secureRandom = secureRandom;
    }

    public ChannelTransportResponse exchange(ChannelCommandContext context,
                                             ChannelOperation operation,
                                             byte[] plaintextPayload,
                                             ChannelTransportClient client) {
        require(context, operation, plaintextPayload, client);
        LocalDateTime requestTime = now();
        long startedNanos = System.nanoTime();
        try (LoadedChannelConfiguration loaded = configurationLoader.load(context.tenantId(), context.product(), context
            .configVersion(), requestTime)) {
            try {
                return resilienceExecutor.execute(context, operation, loaded.config()
                    .timeouts()
                    .resiliencePolicies()
                    .get(operation), attemptNumber -> exchangeAttempt(loaded, context, operation, plaintextPayload, client));
            } catch (ChannelTransportException ex) {
                if (ex.code() == ChannelTransportException.Code.CIRCUIT_OPEN || ex
                    .code() == ChannelTransportException.Code.BULKHEAD_FULL) {
                    appendAudit(contextAudit(context, operation, ChannelTransportOutcome.REJECTED, requestTime, startedNanos, ex
                        .code()
                        .name()));
                }
                throw ex;
            }
        } catch (ChannelConfigurationException ex) {
            appendAudit(contextAudit(context, operation, ChannelTransportOutcome.REJECTED, requestTime, startedNanos, ChannelTransportException.Code.CONFIGURATION_UNAVAILABLE
                .name()));
            throw new ChannelTransportException(ChannelTransportException.Code.CONFIGURATION_UNAVAILABLE, ChannelTransportException.TransmissionState.NOT_SENT);
        }
    }

    private ChannelTransportResponse exchangeAttempt(LoadedChannelConfiguration loaded,
                                                     ChannelCommandContext context,
                                                     ChannelOperation operation,
                                                     byte[] plaintextPayload,
                                                     ChannelTransportClient client) {
        LocalDateTime requestTime = now();
        long startedNanos = System.nanoTime();
        ChannelOutboundRequest request;
        try {
            request = prepare(loaded, context, operation, plaintextPayload);
        } catch (ChannelTransportException ex) {
            appendAudit(contextAudit(context, operation, ChannelTransportOutcome.REJECTED, requestTime, startedNanos, ex
                .code()
                .name()));
            throw ex;
        }
        appendAudit(audit(request, ChannelTransportOutcome.PREPARED, requestTime, null, null, null, null));
        try {
            Duration timeout = loaded.config().timeouts().operationTimeouts().get(operation);
            ChannelTransportResponse response = client.exchange(request, timeout);
            if (response == null) {
                throw new ChannelTransportException(ChannelTransportException.Code.TRANSPORT_FAILED, ChannelTransportException.TransmissionState.UNKNOWN);
            }
            appendAudit(audit(request, ChannelTransportOutcome.SUCCEEDED, requestTime, now(), elapsed(startedNanos), response
                .statusCode(), null));
            return response;
        } catch (ChannelTransportException ex) {
            ChannelTransportException effective = classify(operation, ex);
            appendAudit(audit(request, effective.code() == ChannelTransportException.Code.UNCERTAIN_RESULT
                ? ChannelTransportOutcome.UNCERTAIN
                : ChannelTransportOutcome.FAILED, requestTime, now(), elapsed(startedNanos), null, effective.code()
                    .name()));
            if (effective.code() == ChannelTransportException.Code.UNCERTAIN_RESULT) {
                registerRecovery(context, operation);
            }
            throw effective;
        } catch (RuntimeException ex) {
            ChannelTransportException effective = classify(operation, new ChannelTransportException(ChannelTransportException.Code.TRANSPORT_FAILED, ChannelTransportException.TransmissionState.UNKNOWN));
            appendAudit(audit(request, effective.code() == ChannelTransportException.Code.UNCERTAIN_RESULT
                ? ChannelTransportOutcome.UNCERTAIN
                : ChannelTransportOutcome.FAILED, requestTime, now(), elapsed(startedNanos), null, effective.code()
                    .name()));
            if (effective.code() == ChannelTransportException.Code.UNCERTAIN_RESULT) {
                registerRecovery(context, operation);
            }
            throw effective;
        }
    }

    private void registerRecovery(ChannelCommandContext context, ChannelOperation operation) {
        LocalDateTime now = now();
        try {
            Long id = recoveryRegistration
                .register(new ChannelRecoveryDraft(context, operation, queryOperation(operation), now
                    .plus(recoveryPolicy.retryDelay(1)), now));
            if (id == null) {
                throw new IllegalStateException();
            }
        } catch (RuntimeException ex) {
            throw new ChannelTransportException(ChannelTransportException.Code.RECOVERY_REGISTRATION_FAILED, ChannelTransportException.TransmissionState.UNKNOWN);
        }
    }

    private ChannelOperation queryOperation(ChannelOperation operation) {
        return switch (operation) {
            case SUBMIT_ONBOARDING -> ChannelOperation.QUERY_ONBOARDING_STATUS;
            case ADJUST_LIMIT -> ChannelOperation.QUERY_LIMIT_ADJUSTMENT;
            default -> null;
        };
    }

    private ChannelTransportException classify(ChannelOperation operation, ChannelTransportException exception) {
        boolean uncertainFailure = exception.code() == ChannelTransportException.Code.TIMEOUT || exception
            .code() == ChannelTransportException.Code.TRANSPORT_FAILED;
        if (!operation.safeToRetry() && uncertainFailure && exception
            .transmissionState() != ChannelTransportException.TransmissionState.NOT_SENT) {
            return new ChannelTransportException(ChannelTransportException.Code.UNCERTAIN_RESULT, exception
                .transmissionState());
        }
        return exception;
    }

    private ChannelTransportAuditRecord contextAudit(ChannelCommandContext context,
                                                     ChannelOperation operation,
                                                     ChannelTransportOutcome outcome,
                                                     LocalDateTime requestTime,
                                                     long startedNanos,
                                                     String failureCategory) {
        return new ChannelTransportAuditRecord(context, operation, outcome, requestTime, now(), elapsed(startedNanos), null, null, null, null, failureCategory);
    }

    private ChannelOutboundRequest prepare(LoadedChannelConfiguration loaded,
                                           ChannelCommandContext context,
                                           ChannelOperation operation,
                                           byte[] plaintextPayload) {
        ChannelConnectionConfig config = loaded.config();
        if (!config.tenantId().equals(context.tenantId()) || !config.product().equals(context.product()) || !config
            .configVersion()
            .equals(context.configVersion())) {
            throw new ChannelTransportException(ChannelTransportException.Code.CONFIGURATION_UNAVAILABLE);
        }
        URI endpoint = URI.create(config.endpoints().baseUrl() + config.endpoints().operationPaths().get(operation));
        long timestamp = clock.instant().toEpochMilli();
        String nonce = randomToken(REQUEST_NONCE_BYTES);
        String nonceFingerprint = fingerprint(nonce);
        String signingKeyVersion = keyVersion(config.keyReferences().signing().reference());
        String encryptionKeyVersion = config.keyReferences().encryption() == null
            ? null
            : keyVersion(config.keyReferences().encryption().reference());
        byte[] payload = plaintextPayload;
        boolean encrypted = loaded.encryptionSecret() != null;
        if (encrypted) {
            payload = encrypt(loaded.encryptionSecret(), plaintextPayload, aad(context, operation, timestamp, nonce));
        }
        String signature = sign(loaded
            .signingSecret(), canonical(context, operation, endpoint, timestamp, nonce, signingKeyVersion, encryptionKeyVersion, payload));
        return new ChannelOutboundRequest(context, operation, endpoint, timestamp, nonce, nonceFingerprint, signingKeyVersion, encryptionKeyVersion, encrypted, payload, signature);
    }

    private byte[] encrypt(ChannelSecret secret, byte[] plaintext, byte[] aad) {
        byte[] material = secret.copyMaterial();
        byte[] key = null;
        try {
            key = MessageDigest.getInstance("SHA-256").digest(material);
            byte[] iv = new byte[GCM_IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(aad);
            byte[] ciphertext = cipher.doFinal(plaintext);
            return ByteBuffer.allocate(1 + iv.length + ciphertext.length).put((byte)1).put(iv).put(ciphertext).array();
        } catch (GeneralSecurityException ex) {
            throw new ChannelTransportException(ChannelTransportException.Code.ENCRYPTION_FAILED);
        } finally {
            Arrays.fill(material, (byte)0);
            if (key != null)
                Arrays.fill(key, (byte)0);
        }
    }

    private String sign(ChannelSecret secret, byte[] canonical) {
        byte[] material = secret.copyMaterial();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(material, "HmacSHA256"));
            return BASE64_URL.encodeToString(mac.doFinal(canonical));
        } catch (GeneralSecurityException ex) {
            throw new ChannelTransportException(ChannelTransportException.Code.SIGNING_FAILED);
        } finally {
            Arrays.fill(material, (byte)0);
        }
    }

    private byte[] canonical(ChannelCommandContext context,
                             ChannelOperation operation,
                             URI endpoint,
                             long timestamp,
                             String nonce,
                             String signingKeyVersion,
                             String encryptionKeyVersion,
                             byte[] payload) {
        String value = String.join("\n", operation.name(), endpoint.getRawPath(), context.businessSerial(), context
            .traceId(), Long.toString(timestamp), nonce, signingKeyVersion, encryptionKeyVersion == null
                ? "NONE"
                : encryptionKeyVersion, digest(payload));
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] aad(ChannelCommandContext context, ChannelOperation operation, long timestamp, String nonce) {
        return String.join("\n", context.product().channelCode(), context.product().productCode(), operation
            .name(), context.businessType().name(), context.businessId().toString(), context.businessVersion()
                .toString(), context.businessSerial(), context.traceId(), Long.toString(timestamp), nonce)
            .getBytes(StandardCharsets.UTF_8);
    }

    private ChannelTransportAuditRecord audit(ChannelOutboundRequest request,
                                              ChannelTransportOutcome outcome,
                                              LocalDateTime requestTime,
                                              LocalDateTime responseTime,
                                              Long durationMillis,
                                              Integer statusCode,
                                              String failureCategory) {
        return new ChannelTransportAuditRecord(request.context(), request
            .operation(), outcome, requestTime, responseTime, durationMillis, request.nonceFingerprint(), request
                .signingKeyVersion(), request.encryptionKeyVersion(), statusCode, failureCategory);
    }

    private void appendAudit(ChannelTransportAuditRecord record) {
        try {
            if (auditPort.append(record) == null) {
                throw new IllegalStateException();
            }
        } catch (RuntimeException ex) {
            if (ex instanceof ChannelTransportException channelException && channelException
                .code() == ChannelTransportException.Code.AUDIT_FAILED) {
                throw channelException;
            }
            throw new ChannelTransportException(ChannelTransportException.Code.AUDIT_FAILED);
        }
    }

    private String keyVersion(String reference) {
        return "ref-" + digest(reference.getBytes(StandardCharsets.UTF_8)).substring(0, 16);
    }

    private String fingerprint(String nonce) {
        return digest(nonce.getBytes(StandardCharsets.UTF_8)).substring(0, 16);
    }

    private String digest(byte[] value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException ex) {
            throw new ChannelTransportException(ChannelTransportException.Code.SIGNING_FAILED);
        }
    }

    private String randomToken(int bytes) {
        byte[] value = new byte[bytes];
        secureRandom.nextBytes(value);
        return BASE64_URL.encodeToString(value);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private long elapsed(long startedNanos) {
        return Math.max(0, Duration.ofNanos(System.nanoTime() - startedNanos).toMillis());
    }

    private void require(ChannelCommandContext context,
                         ChannelOperation operation,
                         byte[] plaintextPayload,
                         ChannelTransportClient client) {
        if (context == null || operation == null || plaintextPayload == null || plaintextPayload.length == 0 || plaintextPayload.length > 10 * 1024 * 1024 || client == null) {
            throw new ChannelTransportException(ChannelTransportException.Code.TRANSPORT_FAILED);
        }
    }
}
