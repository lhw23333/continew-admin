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

package top.continew.admin.merchant.master.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import top.continew.admin.merchant.master.application.MerchantRepository;
import top.continew.admin.merchant.master.domain.Merchant;
import top.continew.admin.merchant.master.domain.MerchantDomainException;
import top.continew.admin.merchant.security.value.EncryptedMobileNumber;

import java.util.Optional;

/** MyBatis merchant repository with mandatory tenant predicates. */
@Repository
@RequiredArgsConstructor
public class MyBatisMerchantRepository implements MerchantRepository {

    private final MerchantMapper mapper;

    @Override
    public Optional<Merchant> findById(Long tenantId, Long merchantId) {
        return Optional.ofNullable(mapper.lambdaQuery()
            .eq(MerchantDO::getTenantId, tenantId)
            .eq(MerchantDO::getId, merchantId)
            .eq(MerchantDO::getDeleted, 0L)
            .one()).map(this::toDomain);
    }

    @Override
    public boolean existsById(Long tenantId, Long merchantId) {
        return mapper.lambdaQuery()
            .eq(MerchantDO::getTenantId, tenantId)
            .eq(MerchantDO::getId, merchantId)
            .eq(MerchantDO::getDeleted, 0L)
            .exists();
    }

    @Override
    public void insert(Merchant merchant) {
        if (mapper.insert(toDataObject(merchant)) != 1) {
            throw new MerchantDomainException("Merchant persistence failed");
        }
    }

    @Override
    public boolean updateLifecycle(Merchant merchant, Long expectedVersion) {
        return mapper.lambdaUpdate()
            .eq(MerchantDO::getTenantId, merchant.tenantId())
            .eq(MerchantDO::getId, merchant.id())
            .eq(MerchantDO::getRowVersion, expectedVersion)
            .eq(MerchantDO::getDeleted, 0L)
            .set(MerchantDO::getStatus, merchant.status())
            .set(MerchantDO::getDisabledReason, merchant.disabledReason())
            .set(MerchantDO::getRowVersion, merchant.rowVersion())
            .set(MerchantDO::getUpdateTime, merchant.updateTime())
            .update();
    }

    private Merchant toDomain(MerchantDO dataObject) {
        return new Merchant(dataObject.getId(), dataObject.getTenantId(), dataObject.getOwningAgentId(), dataObject
            .getMerchantNo(), dataObject.getMerchantType(), dataObject.getLegalName(), dataObject
                .getShortName(), dataObject.getLegalSubjectHash(), dataObject.getOperatorUserId(), dataObject
                    .getReviewerUserId(), dataObject.getContactName(), restoreMobile(dataObject), dataObject
                        .getIndustry(), dataObject.getProductDescription(), dataObject.getStatus(), dataObject
                            .getDisabledReason(), dataObject.getCertifiedKycVersionId(), dataObject
                                .getRowVersion(), dataObject.getCreateTime(), dataObject.getUpdateTime());
    }

    private MerchantDO toDataObject(Merchant merchant) {
        MerchantDO dataObject = new MerchantDO();
        dataObject.setId(merchant.id());
        dataObject.setTenantId(merchant.tenantId());
        dataObject.setOwningAgentId(merchant.owningAgentId());
        dataObject.setMerchantNo(merchant.merchantNo());
        dataObject.setMerchantType(merchant.merchantType());
        dataObject.setLegalName(merchant.legalName());
        dataObject.setShortName(merchant.shortName());
        dataObject.setLegalSubjectHash(merchant.legalSubjectHash());
        dataObject.setOperatorUserId(merchant.operatorUserId());
        dataObject.setReviewerUserId(merchant.reviewerUserId());
        dataObject.setContactName(merchant.contactName());
        if (merchant.contactMobile() != null) {
            dataObject.setContactMobileCiphertext(merchant.contactMobile().ciphertext());
            dataObject.setContactMobileHash(merchant.contactMobile().normalizedHash());
            dataObject.setContactMobileHashKeyVersion(merchant.contactMobile().hashKeyVersion());
            dataObject.setContactMobileMasked(merchant.contactMobile().maskedValue());
            dataObject.setContactMobileKeyVersion(merchant.contactMobile().keyVersion());
        }
        dataObject.setIndustry(merchant.industry());
        dataObject.setProductDescription(merchant.productDescription());
        dataObject.setStatus(merchant.status());
        dataObject.setDisabledReason(merchant.disabledReason());
        dataObject.setCertifiedKycVersionId(merchant.certifiedKycVersionId());
        dataObject.setRowVersion(merchant.rowVersion());
        dataObject.setCreateTime(merchant.createTime());
        dataObject.setUpdateTime(merchant.updateTime());
        dataObject.setDeleted(0L);
        return dataObject;
    }

    private EncryptedMobileNumber restoreMobile(MerchantDO dataObject) {
        if (dataObject.getContactMobileCiphertext() == null) {
            return null;
        }
        try {
            return EncryptedMobileNumber.restore(dataObject.getContactMobileCiphertext(), dataObject
                .getContactMobileKeyVersion(), dataObject.getContactMobileHash(), dataObject
                    .getContactMobileHashKeyVersion(), dataObject.getContactMobileMasked());
        } catch (IllegalArgumentException ex) {
            throw new MerchantDomainException("Stored merchant mobile protection metadata is incomplete");
        }
    }
}
