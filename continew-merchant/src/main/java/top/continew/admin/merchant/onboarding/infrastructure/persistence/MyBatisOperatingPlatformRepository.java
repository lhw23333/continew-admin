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

package top.continew.admin.merchant.onboarding.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import top.continew.admin.merchant.kyc.attachment.KycAttachmentRepository;
import top.continew.admin.merchant.master.domain.MerchantDomainException;
import top.continew.admin.merchant.onboarding.application.OperatingPlatform;
import top.continew.admin.merchant.onboarding.application.OperatingPlatformRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** MyBatis operating-platform repository with proof attachments resolved independently per platform. */
@Repository
@RequiredArgsConstructor
public class MyBatisOperatingPlatformRepository implements OperatingPlatformRepository {

    private final OperatingPlatformMapper platformMapper;
    private final OperatingPlatformAttachmentMapper linkMapper;
    private final KycAttachmentRepository attachmentRepository;

    @Override
    public List<OperatingPlatform> list(Long tenantId, Long kycVersionId) {
        return platformMapper.lambdaQuery()
            .eq(OperatingPlatformDO::getTenantId, tenantId)
            .eq(OperatingPlatformDO::getKycVersionId, kycVersionId)
            .eq(OperatingPlatformDO::getDeleted, 0L)
            .orderByAsc(OperatingPlatformDO::getCreateTime)
            .orderByAsc(OperatingPlatformDO::getId)
            .list()
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public Optional<OperatingPlatform> findById(Long tenantId, Long kycVersionId, Long platformId) {
        return Optional.ofNullable(platformMapper.lambdaQuery()
            .eq(OperatingPlatformDO::getTenantId, tenantId)
            .eq(OperatingPlatformDO::getKycVersionId, kycVersionId)
            .eq(OperatingPlatformDO::getId, platformId)
            .eq(OperatingPlatformDO::getDeleted, 0L)
            .one()).map(this::toDomain);
    }

    @Override
    public OperatingPlatform insert(Long id,
                                    Long tenantId,
                                    Long kycVersionId,
                                    String platformCode,
                                    String storeName,
                                    String storeUrl,
                                    String storeIdentifier,
                                    OperatingPlatform.CertificationStatus certificationStatus,
                                    Long createUser,
                                    LocalDateTime createTime) {
        OperatingPlatformDO row = new OperatingPlatformDO();
        row.setId(id);
        row.setTenantId(tenantId);
        row.setKycVersionId(kycVersionId);
        row.setPlatformCode(platformCode);
        row.setStoreName(storeName);
        row.setStoreUrl(storeUrl);
        row.setStoreIdentifier(storeIdentifier);
        row.setCertificationStatus(certificationStatus);
        row.setRowVersion(0L);
        row.setCreateUser(createUser);
        row.setCreateTime(createTime);
        row.setDeleted(0L);
        try {
            if (platformMapper.insert(row) != 1) {
                throw new MerchantDomainException("Operating platform persistence failed");
            }
        } catch (DataIntegrityViolationException ex) {
            throw new MerchantDomainException("Operating platform store already exists in this KYC version");
        }
        return toDomain(row);
    }

    @Override
    public boolean update(Long tenantId,
                          Long kycVersionId,
                          Long platformId,
                          String storeName,
                          String storeUrl,
                          String storeIdentifier,
                          OperatingPlatform.CertificationStatus certificationStatus,
                          Long expectedVersion,
                          LocalDateTime updateTime) {
        return platformMapper.lambdaUpdate()
            .eq(OperatingPlatformDO::getTenantId, tenantId)
            .eq(OperatingPlatformDO::getKycVersionId, kycVersionId)
            .eq(OperatingPlatformDO::getId, platformId)
            .eq(OperatingPlatformDO::getRowVersion, expectedVersion)
            .eq(OperatingPlatformDO::getDeleted, 0L)
            .set(OperatingPlatformDO::getStoreName, storeName)
            .set(OperatingPlatformDO::getStoreUrl, storeUrl)
            .set(OperatingPlatformDO::getStoreIdentifier, storeIdentifier)
            .set(OperatingPlatformDO::getCertificationStatus, certificationStatus)
            .set(OperatingPlatformDO::getRowVersion, expectedVersion + 1)
            .set(OperatingPlatformDO::getUpdateTime, updateTime)
            .update();
    }

    @Override
    public void linkProof(Long id,
                          Long tenantId,
                          Long kycVersionId,
                          Long platformId,
                          Long attachmentId,
                          String evidenceType,
                          Long createUser,
                          LocalDateTime createTime) {
        OperatingPlatformAttachmentDO row = new OperatingPlatformAttachmentDO();
        row.setId(id);
        row.setTenantId(tenantId);
        row.setKycVersionId(kycVersionId);
        row.setPlatformId(platformId);
        row.setAttachmentId(attachmentId);
        row.setEvidenceType(evidenceType);
        row.setCreateUser(createUser);
        row.setCreateTime(createTime);
        row.setDeleted(0L);
        try {
            if (linkMapper.insert(row) != 1) {
                throw new MerchantDomainException("Operating platform proof link failed");
            }
        } catch (DataIntegrityViolationException ex) {
            throw new MerchantDomainException("Attachment is already linked to this operating platform");
        }
    }

    private OperatingPlatform toDomain(OperatingPlatformDO row) {
        List<OperatingPlatform.ProofAttachment> proofs = linkMapper.lambdaQuery()
            .eq(OperatingPlatformAttachmentDO::getTenantId, row.getTenantId())
            .eq(OperatingPlatformAttachmentDO::getKycVersionId, row.getKycVersionId())
            .eq(OperatingPlatformAttachmentDO::getPlatformId, row.getId())
            .eq(OperatingPlatformAttachmentDO::getDeleted, 0L)
            .orderByAsc(OperatingPlatformAttachmentDO::getId)
            .list()
            .stream()
            .flatMap(link -> attachmentRepository.findById(row.getTenantId(), link.getAttachmentId())
                .stream()
                .map(attachment -> new OperatingPlatform.ProofAttachment(attachment.id(), link
                    .getEvidenceType(), attachment.originalName(), attachment.scanStatus().name(), attachment
                        .validationStatus()
                        .name())))
            .toList();
        return new OperatingPlatform(row.getId(), row.getKycVersionId(), row.getPlatformCode(), row.getStoreName(), row
            .getStoreUrl(), row.getStoreIdentifier(), row.getCertificationStatus(), row.getRowVersion(), row
                .getCreateTime(), row.getUpdateTime(), proofs);
    }
}
