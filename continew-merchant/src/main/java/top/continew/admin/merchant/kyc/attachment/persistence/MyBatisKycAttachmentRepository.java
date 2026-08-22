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

package top.continew.admin.merchant.kyc.attachment.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import top.continew.admin.merchant.kyc.attachment.KycAttachment;
import top.continew.admin.merchant.kyc.attachment.KycAttachmentDraft;
import top.continew.admin.merchant.kyc.attachment.KycAttachmentException;
import top.continew.admin.merchant.kyc.attachment.KycAttachmentRepository;

import java.util.Optional;
import java.util.List;

/** Tenant-explicit MyBatis KYC attachment repository. */
@Repository
@RequiredArgsConstructor
public class MyBatisKycAttachmentRepository implements KycAttachmentRepository {

    private final KycAttachmentMapper mapper;

    @Override
    public long countByKycVersion(Long tenantId, Long kycVersionId) {
        return mapper.lambdaQuery()
            .eq(KycAttachmentDO::getTenantId, tenantId)
            .eq(KycAttachmentDO::getKycVersionId, kycVersionId)
            .eq(KycAttachmentDO::getDeleted, 0L)
            .count();
    }

    @Override
    public long countByEvidenceType(Long tenantId, Long kycVersionId, String evidenceType) {
        return mapper.lambdaQuery()
            .eq(KycAttachmentDO::getTenantId, tenantId)
            .eq(KycAttachmentDO::getKycVersionId, kycVersionId)
            .eq(KycAttachmentDO::getEvidenceType, evidenceType)
            .eq(KycAttachmentDO::getDeleted, 0L)
            .count();
    }

    @Override
    public Optional<KycAttachment> findById(Long tenantId, Long attachmentId) {
        return Optional.ofNullable(mapper.lambdaQuery()
            .eq(KycAttachmentDO::getTenantId, tenantId)
            .eq(KycAttachmentDO::getId, attachmentId)
            .eq(KycAttachmentDO::getDeleted, 0L)
            .one()).map(this::toDomain);
    }

    @Override
    public List<KycAttachment> listByKycVersion(Long tenantId, Long kycVersionId) {
        return mapper.lambdaQuery()
            .eq(KycAttachmentDO::getTenantId, tenantId)
            .eq(KycAttachmentDO::getKycVersionId, kycVersionId)
            .eq(KycAttachmentDO::getDeleted, 0L)
            .orderByAsc(KycAttachmentDO::getSort)
            .orderByAsc(KycAttachmentDO::getId)
            .list()
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public KycAttachment insert(KycAttachmentDraft draft) {
        KycAttachmentDO dataObject = new KycAttachmentDO();
        dataObject.setTenantId(draft.tenantId());
        dataObject.setKycVersionId(draft.kycVersionId());
        dataObject.setEvidenceType(draft.evidenceType());
        dataObject.setStorageObjectId(draft.storageObjectId());
        dataObject.setOriginalName(draft.originalName());
        dataObject.setExtension(draft.extension());
        dataObject.setDeclaredMime(draft.declaredMime());
        dataObject.setDetectedMime(draft.detectedMime());
        dataObject.setSizeBytes(draft.sizeBytes());
        dataObject.setSha256(draft.sha256());
        dataObject.setScanStatus(draft.scanStatus());
        dataObject.setValidationStatus(draft.validationStatus());
        dataObject.setSort(draft.sort());
        dataObject.setCreateTime(draft.createTime());
        dataObject.setDeleted(0L);
        if (mapper.insert(dataObject) != 1) {
            throw new KycAttachmentException("KYC attachment metadata persistence failed");
        }
        return toDomain(dataObject);
    }

    private KycAttachment toDomain(KycAttachmentDO dataObject) {
        return new KycAttachment(dataObject.getId(), dataObject.getTenantId(), dataObject.getKycVersionId(), dataObject
            .getEvidenceType(), dataObject.getStorageObjectId(), dataObject.getOriginalName(), dataObject
                .getExtension(), dataObject.getDeclaredMime(), dataObject.getDetectedMime(), dataObject
                    .getSizeBytes(), dataObject.getSha256(), dataObject.getScanStatus(), dataObject
                        .getValidationStatus(), dataObject.getSort(), dataObject.getCreateTime());
    }
}
