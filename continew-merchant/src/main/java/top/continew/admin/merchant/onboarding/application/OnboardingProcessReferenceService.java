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

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.continew.admin.channel.dto.ChannelSigningAction;
import top.continew.admin.merchant.master.application.MerchantScopeAuthorizationService;
import top.continew.admin.merchant.master.domain.MerchantAccessDeniedException;
import top.continew.admin.merchant.security.audit.application.SecurityAuditWriter;
import top.continew.admin.merchant.security.audit.domain.SecurityAuditRecord;
import top.continew.admin.merchant.security.audit.domain.SecurityAuditResult;

import java.net.URI;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** Issues and resolves tenant/merchant/application/action-bound process QR and action links. */
@Service
@RequiredArgsConstructor
public class OnboardingProcessReferenceService {

    private final MerchantScopeAuthorizationService merchantScopeAuthorizationService;
    private final OnboardingProcessReferenceRepository referenceRepository;
    private final OnboardingProcessReferenceTokenService tokenService;
    private final OnboardingProcessReferencePolicy policy;
    private final ProcessQrCodePort qrCodePort;
    private final SecurityAuditWriter securityAuditWriter;
    private final Clock clock = Clock.systemUTC();

    /** The same authorized operation is the regeneration path; a fresh nonce produces a distinct reference. */
    public OnboardingProcessReference issue(Long tenantId,
                                            Long actorUserId,
                                            Long merchantId,
                                            Long applicationId,
                                            ChannelSigningAction action,
                                            String ipAddress) {
        merchantScopeAuthorizationService.requireAccessible(tenantId, actorUserId, merchantId);
        OnboardingProcessReferenceBinding binding = requireBinding(tenantId, merchantId, applicationId);
        OnboardingProcessReferenceTokenService.IssuedToken issued = tokenService
            .issue(tenantId, merchantId, applicationId, binding.channelCode(), action, policy.validity());
        URI processUrl = URI.create(policy.baseUri().toASCIIString() + "?token=" + issued.token());
        String qrCodeBase64;
        try {
            qrCodeBase64 = qrCodePort.encodePngBase64(processUrl.toASCIIString());
        } catch (ProcessReferenceException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ProcessReferenceException(ProcessReferenceException.Code.QR_GENERATION_FAILED, ex);
        }
        audit(tenantId, actorUserId, binding, action, "PROCESS_REFERENCE_ISSUE", ipAddress);
        return new OnboardingProcessReference(merchantId, applicationId, binding
            .channelCode(), action, processUrl, "image/png", qrCodeBase64, issued.claims().expiresAt());
    }

    public OnboardingProcessReferenceClaims resolve(Long tenantId,
                                                    Long actorUserId,
                                                    Long merchantId,
                                                    Long applicationId,
                                                    String token,
                                                    String ipAddress) {
        OnboardingProcessReferenceClaims claims = tokenService.verify(token);
        if (!tenantId.equals(claims.tenantId()) || !merchantId.equals(claims.merchantId()) || !applicationId
            .equals(claims.applicationId())) {
            throw new ProcessReferenceException(ProcessReferenceException.Code.INVALID);
        }
        merchantScopeAuthorizationService.requireAccessible(tenantId, actorUserId, merchantId);
        OnboardingProcessReferenceBinding binding = requireBinding(tenantId, merchantId, applicationId);
        if (!binding.channelCode().equals(claims.channelCode())) {
            throw new ProcessReferenceException(ProcessReferenceException.Code.INVALID);
        }
        audit(tenantId, actorUserId, binding, claims.action(), "PROCESS_REFERENCE_RESOLVE", ipAddress);
        return claims;
    }

    private OnboardingProcessReferenceBinding requireBinding(Long tenantId, Long merchantId, Long applicationId) {
        return referenceRepository.find(tenantId, merchantId, applicationId)
            .orElseThrow(MerchantAccessDeniedException::new);
    }

    private void audit(Long tenantId,
                       Long actorUserId,
                       OnboardingProcessReferenceBinding binding,
                       ChannelSigningAction action,
                       String auditAction,
                       String ipAddress) {
        securityAuditWriter
            .append(new SecurityAuditRecord(tenantId, actorUserId, null, auditAction, "ONBOARDING_APPLICATION", binding
                .applicationId(), binding.rowVersion(), action.name(), "channel=" + binding
                    .channelCode(), ipAddress, SecurityAuditResult.SUCCESS, null, LocalDateTime.ofInstant(clock
                        .instant(), ZoneOffset.UTC)));
    }
}
