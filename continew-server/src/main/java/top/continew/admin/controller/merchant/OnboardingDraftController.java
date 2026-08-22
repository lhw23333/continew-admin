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

package top.continew.admin.controller.merchant;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.extra.servlet.JakartaServletUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.merchant.onboarding.application.KycReuseField;
import top.continew.admin.merchant.onboarding.application.KycReuseResult;
import top.continew.admin.merchant.onboarding.application.KycReuseService;
import top.continew.admin.merchant.onboarding.application.KycReuseSourceView;
import top.continew.admin.merchant.onboarding.application.KycProfileSaveCommand;
import top.continew.admin.merchant.onboarding.application.KycProfileService;
import top.continew.admin.merchant.onboarding.application.KycProfileView;
import top.continew.admin.merchant.onboarding.application.OnboardingEvidenceService;
import top.continew.admin.merchant.onboarding.application.OnboardingEvidenceSummary;
import top.continew.admin.merchant.onboarding.application.OnboardingFinalPreview;
import top.continew.admin.merchant.onboarding.application.OnboardingFinalPreviewService;
import top.continew.admin.merchant.onboarding.application.OnboardingPricingService;
import top.continew.admin.merchant.onboarding.application.OnboardingPricingView;
import top.continew.admin.merchant.onboarding.application.OperatingPlatform;
import top.continew.admin.merchant.onboarding.application.OperatingPlatformService;
import top.continew.admin.merchant.onboarding.application.SettlementAccountSaveCommand;
import top.continew.admin.merchant.onboarding.application.SettlementAccountService;
import top.continew.admin.merchant.onboarding.application.SettlementAccountVerificationPort;
import top.continew.admin.merchant.onboarding.application.SettlementAccountView;
import top.continew.admin.merchant.onboarding.application.OnboardingDraftService;
import top.continew.admin.merchant.onboarding.application.OnboardingDraftView;
import top.continew.starter.log.annotation.Log;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** Explicit-save onboarding draft API. */
@Tag(name = "商户进件草稿 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/merchant/merchants/{merchantId}/onboarding-drafts")
public class OnboardingDraftController {

    private final OnboardingDraftService onboardingDraftService;
    private final KycReuseService kycReuseService;
    private final OnboardingEvidenceService onboardingEvidenceService;
    private final OnboardingFinalPreviewService onboardingFinalPreviewService;
    private final KycProfileService kycProfileService;
    private final SettlementAccountService settlementAccountService;
    private final OnboardingPricingService onboardingPricingService;
    private final OperatingPlatformService operatingPlatformService;

    @Log(ignore = true)
    @Operation(summary = "获取最终确认预览", description = "只读返回精确保存版本的脱敏摘要和提交阻塞原因")
    @SaCheckPermission("merchant:onboarding:create")
    @GetMapping("/{applicationId}/final-preview")
    public OnboardingFinalPreview finalPreview(@PathVariable Long merchantId, @PathVariable Long applicationId) {
        return onboardingFinalPreviewService.preview(UserContextHolder.getTenantId(), UserContextHolder
            .getUserId(), merchantId, applicationId);
    }

    @Log(ignore = true)
    @Operation(summary = "创建或恢复活动草稿", description = "同一商户渠道产品重复调用返回现有活动草稿")
    @SaCheckPermission("merchant:onboarding:create")
    @PostMapping
    public OnboardingDraftView createOrLoad(@PathVariable Long merchantId,
                                            @RequestBody @Valid DraftCreateReq req,
                                            HttpServletRequest request) {
        return onboardingDraftService.createOrLoad(UserContextHolder.getTenantId(), UserContextHolder
            .getUserId(), merchantId, req.getChannelCode(), req.getProductCode(), JakartaServletUtil
                .getClientIP(request));
    }

    @Log(ignore = true)
    @Operation(summary = "加载已保存草稿", description = "只返回服务端最后一次显式保存的步骤状态")
    @SaCheckPermission("merchant:onboarding:create")
    @GetMapping("/{applicationId}")
    public OnboardingDraftView load(@PathVariable Long merchantId, @PathVariable Long applicationId) {
        return onboardingDraftService.load(UserContextHolder.getTenantId(), UserContextHolder
            .getUserId(), merchantId, applicationId);
    }

    @Log(ignore = true)
    @Operation(summary = "显式保存草稿进度", description = "使用KYC业务版本执行乐观并发控制")
    @SaCheckPermission("merchant:onboarding:create")
    @PatchMapping("/{applicationId}/progress")
    public OnboardingDraftView saveProgress(@PathVariable Long merchantId,
                                            @PathVariable Long applicationId,
                                            @RequestBody @Valid DraftProgressReq req,
                                            HttpServletRequest request) {
        return onboardingDraftService.saveProgress(UserContextHolder.getTenantId(), UserContextHolder
            .getUserId(), merchantId, applicationId, req.getSavedStep(), req.getCompletedSteps(), req
                .getExpectedVersion(), JakartaServletUtil.getClientIP(request));
    }

    @Log(ignore = true)
    @Operation(summary = "查询同商户可复用KYC", description = "仅返回来源、掩码、可复用字段和需重确认字段")
    @SaCheckPermission("merchant:onboarding:create")
    @GetMapping("/{applicationId}/reuse-sources")
    public List<KycReuseSourceView> listReuseSources(@PathVariable Long merchantId, @PathVariable Long applicationId) {
        return kycReuseService.listSources(UserContextHolder.getTenantId(), UserContextHolder
            .getUserId(), merchantId, applicationId);
    }

    @Log(ignore = true)
    @Operation(summary = "复用历史KYC字段", description = "执行同商户校验、字段白名单、渠道排除和有效期重校验")
    @SaCheckPermission("merchant:onboarding:create")
    @PostMapping("/{applicationId}/reuse")
    public KycReuseResult reuse(@PathVariable Long merchantId,
                                @PathVariable Long applicationId,
                                @RequestBody @Valid KycReuseReq req,
                                HttpServletRequest request) {
        return kycReuseService.reuse(UserContextHolder.getTenantId(), UserContextHolder
            .getUserId(), merchantId, applicationId, req.getSourceKycVersionId(), req.getFields(), req
                .getExpectedVersion(), JakartaServletUtil.getClientIP(request));
    }

    @Log(ignore = true)
    @Operation(summary = "查询草稿材料完整性", description = "按创建草稿时快照的要求版本返回必传、可选、扫描和校验状态")
    @SaCheckPermission("merchant:onboarding:create")
    @GetMapping("/{applicationId}/evidence")
    public OnboardingEvidenceSummary evidence(@PathVariable Long merchantId, @PathVariable Long applicationId) {
        return onboardingEvidenceService.summary(UserContextHolder.getTenantId(), UserContextHolder
            .getUserId(), merchantId, applicationId);
    }

    @Log(ignore = true)
    @Operation(summary = "保存KYC主体与人员股东资料", description = "敏感标识、手机号、地址、人员和股东结构在服务端加密落库")
    @SaCheckPermission("merchant:onboarding:create")
    @PatchMapping("/{applicationId}/profile")
    public KycProfileView saveProfile(@PathVariable Long merchantId,
                                      @PathVariable Long applicationId,
                                      @RequestBody @Valid KycProfileReq req,
                                      HttpServletRequest request) {
        return kycProfileService.save(new KycProfileSaveCommand(UserContextHolder.getTenantId(), UserContextHolder
            .getUserId(), merchantId, applicationId, req.getLegalName(), req.getLegalIdentifier(), req
                .getLicenseIssueDate(), req.getLicenseExpiryDate(), req
                    .getBusinessScope(), new KycProfileSaveCommand.Address(req.getAddress().getRegisteredAddress(), req
                        .getAddress()
                        .getOperatingRegion(), req.getAddress().getOperatingAddress()), req.getPersons()
                            .stream()
                            .map(person -> new KycProfileSaveCommand.Person(person.getRole(), person.getName(), person
                                .getIdentityNumber(), person.getMobile(), person.getDocumentValidFrom(), person
                                    .getDocumentValidTo()))
                            .toList(), req.getShareholders()
                                .stream()
                                .map(shareholder -> new KycProfileSaveCommand.Shareholder(shareholder
                                    .getType(), shareholder.getName(), shareholder.getIdentifier(), shareholder
                                        .getOwnershipPercent()))
                                .toList(), req.getExpectedVersion(), JakartaServletUtil.getClientIP(request)));
    }

    @Log(ignore = true)
    @Operation(summary = "保存结算账户", description = "账户号独立加密，户名和银行信息整体加密并通过可插拔端口验证")
    @SaCheckPermission("merchant:onboarding:create")
    @PatchMapping("/{applicationId}/settlement-account")
    public SettlementAccountView saveSettlementAccount(@PathVariable Long merchantId,
                                                       @PathVariable Long applicationId,
                                                       @RequestBody @Valid SettlementAccountReq req,
                                                       HttpServletRequest request) {
        return settlementAccountService.save(new SettlementAccountSaveCommand(UserContextHolder
            .getTenantId(), UserContextHolder.getUserId(), merchantId, applicationId, req.getMode(), req
                .getAccountHolderName(), req.getBankCode(), req.getBankBranchName(), req.getAccountNumber(), req
                    .getExpectedVersion(), JakartaServletUtil.getClientIP(request)));
    }

    @Log(ignore = true)
    @Operation(summary = "选择草稿定价版本", description = "保存精确定价版本并按父代理商当前生效边界重新校验")
    @SaCheckPermission("merchant:onboarding:create")
    @PatchMapping("/{applicationId}/pricing")
    public OnboardingPricingView selectPricing(@PathVariable Long merchantId,
                                               @PathVariable Long applicationId,
                                               @RequestBody @Valid PricingSelectionReq req,
                                               HttpServletRequest request) {
        return onboardingPricingService.select(UserContextHolder.getTenantId(), UserContextHolder
            .getUserId(), merchantId, applicationId, req.getPricingVersionId(), req
                .getExpectedVersion(), JakartaServletUtil.getClientIP(request));
    }

    @Log(ignore = true)
    @Operation(summary = "查询经营平台记录")
    @SaCheckPermission("merchant:onboarding:create")
    @GetMapping("/{applicationId}/platforms")
    public List<OperatingPlatform> listPlatforms(@PathVariable Long merchantId, @PathVariable Long applicationId) {
        return operatingPlatformService.list(UserContextHolder.getTenantId(), UserContextHolder
            .getUserId(), merchantId, applicationId);
    }

    @Log(ignore = true)
    @Operation(summary = "新增经营平台记录")
    @SaCheckPermission("merchant:onboarding:create")
    @PostMapping("/{applicationId}/platforms")
    public OperatingPlatform createPlatform(@PathVariable Long merchantId,
                                            @PathVariable Long applicationId,
                                            @RequestBody @Valid PlatformCreateReq req,
                                            HttpServletRequest request) {
        return operatingPlatformService.create(UserContextHolder.getTenantId(), UserContextHolder
            .getUserId(), merchantId, applicationId, req.getPlatformCode(), req.getStoreName(), req.getStoreUrl(), req
                .getStoreIdentifier(), req.getCertificationStatus(), JakartaServletUtil.getClientIP(request));
    }

    @Log(ignore = true)
    @Operation(summary = "修改经营平台记录", description = "平台编码不可通过普通修改操作变更")
    @SaCheckPermission("merchant:onboarding:create")
    @PatchMapping("/{applicationId}/platforms/{platformId}")
    public OperatingPlatform updatePlatform(@PathVariable Long merchantId,
                                            @PathVariable Long applicationId,
                                            @PathVariable Long platformId,
                                            @RequestBody @Valid PlatformUpdateReq req,
                                            HttpServletRequest request) {
        return operatingPlatformService.update(UserContextHolder.getTenantId(), UserContextHolder
            .getUserId(), merchantId, applicationId, platformId, req.getStoreName(), req.getStoreUrl(), req
                .getStoreIdentifier(), req.getCertificationStatus(), req.getExpectedVersion(), JakartaServletUtil
                    .getClientIP(request));
    }

    @Log(ignore = true)
    @Operation(summary = "关联经营平台证明附件", description = "附件必须属于同一KYC版本且证明类型匹配")
    @SaCheckPermission("merchant:onboarding:create")
    @PostMapping("/{applicationId}/platforms/{platformId}/proofs")
    public OperatingPlatform linkPlatformProof(@PathVariable Long merchantId,
                                               @PathVariable Long applicationId,
                                               @PathVariable Long platformId,
                                               @RequestBody @Valid PlatformProofReq req,
                                               HttpServletRequest request) {
        return operatingPlatformService.linkProof(UserContextHolder.getTenantId(), UserContextHolder
            .getUserId(), merchantId, applicationId, platformId, req.getAttachmentId(), req
                .getEvidenceType(), JakartaServletUtil.getClientIP(request));
    }

    @Getter
    @Setter
    public static class DraftCreateReq {
        @NotBlank
        @Size(max = 64)
        private String channelCode;
        @NotBlank
        @Size(max = 64)
        private String productCode;
    }

    @Getter
    @Setter
    public static class DraftProgressReq {
        @NotNull
        @Min(1)
        @Max(5)
        private Integer savedStep;
        @NotNull
        @Size(max = 5)
        private List<@Min(1) @Max(5) Integer> completedSteps;
        @NotNull
        @PositiveOrZero
        private Long expectedVersion;
    }

    @Getter
    @Setter
    public static class KycReuseReq {
        @NotNull
        @Positive
        private Long sourceKycVersionId;
        @NotNull
        @Size(min = 1, max = 4)
        private Set<KycReuseField> fields;
        @NotNull
        @PositiveOrZero
        private Long expectedVersion;
    }

    @Getter
    @Setter
    public static class KycProfileReq {
        @NotBlank
        @Size(max = 200)
        private String legalName;
        @NotBlank
        @Size(max = 64)
        private String legalIdentifier;
        @NotNull
        private LocalDate licenseIssueDate;
        @NotNull
        private LocalDate licenseExpiryDate;
        @NotBlank
        @Size(max = 2000)
        private String businessScope;
        @NotNull
        @Valid
        private AddressReq address;
        @NotEmpty
        @Size(max = 50)
        private List<@Valid PersonReq> persons;
        @NotNull
        @Size(max = 100)
        private List<@Valid ShareholderReq> shareholders;
        @NotNull
        @PositiveOrZero
        private Long expectedVersion;
    }

    @Getter
    @Setter
    public static class AddressReq {
        @NotBlank
        @Size(max = 255)
        private String registeredAddress;
        @NotBlank
        @Size(max = 100)
        private String operatingRegion;
        @NotBlank
        @Size(max = 255)
        private String operatingAddress;
    }

    @Getter
    @Setter
    public static class PersonReq {
        @NotNull
        private KycProfileSaveCommand.PersonRole role;
        @NotBlank
        @Size(max = 100)
        private String name;
        @NotBlank
        @Size(max = 64)
        private String identityNumber;
        @NotBlank
        @Size(max = 32)
        private String mobile;
        @NotNull
        private LocalDate documentValidFrom;
        @NotNull
        private LocalDate documentValidTo;
    }

    @Getter
    @Setter
    public static class ShareholderReq {
        @NotNull
        private KycProfileSaveCommand.ShareholderType type;
        @NotBlank
        @Size(max = 200)
        private String name;
        @NotBlank
        @Size(max = 64)
        private String identifier;
        @NotNull
        private BigDecimal ownershipPercent;
    }

    @Getter
    @Setter
    public static class SettlementAccountReq {
        @NotNull
        private SettlementAccountVerificationPort.SettlementMode mode;
        @NotBlank
        @Size(max = 200)
        private String accountHolderName;
        @NotBlank
        @Size(max = 64)
        private String bankCode;
        @NotBlank
        @Size(max = 200)
        private String bankBranchName;
        @NotBlank
        @Size(max = 64)
        private String accountNumber;
        @NotNull
        @PositiveOrZero
        private Long expectedVersion;
    }

    @Getter
    @Setter
    public static class PricingSelectionReq {
        @NotNull
        @Positive
        private Long pricingVersionId;
        @NotNull
        @PositiveOrZero
        private Long expectedVersion;
    }

    @Getter
    @Setter
    public static class PlatformCreateReq {
        @NotBlank
        @Size(max = 64)
        private String platformCode;
        @NotBlank
        @Size(max = 200)
        private String storeName;
        @Size(max = 1000)
        private String storeUrl;
        @NotBlank
        @Size(max = 128)
        private String storeIdentifier;
        @NotNull
        private OperatingPlatform.CertificationStatus certificationStatus;
    }

    @Getter
    @Setter
    public static class PlatformUpdateReq {
        @NotBlank
        @Size(max = 200)
        private String storeName;
        @Size(max = 1000)
        private String storeUrl;
        @NotBlank
        @Size(max = 128)
        private String storeIdentifier;
        @NotNull
        private OperatingPlatform.CertificationStatus certificationStatus;
        @NotNull
        @PositiveOrZero
        private Long expectedVersion;
    }

    @Getter
    @Setter
    public static class PlatformProofReq {
        @NotNull
        @Positive
        private Long attachmentId;
        @NotBlank
        @Size(max = 64)
        private String evidenceType;
    }
}
