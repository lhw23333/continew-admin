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

package top.continew.admin.merchant.agent.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.continew.starter.data.mapper.BaseMapper;

/** KYC draft-default snapshot mapper. */
@Mapper
public interface KycDraftDefaultSnapshotMapper extends BaseMapper<KycDraftDefaultSnapshotDO> {

    @Select("""
        SELECT k.id AS kyc_version_id,
               k.merchant_id AS merchant_id,
               m.owning_agent_id AS owning_agent_id,
               k.status AS status
        FROM biz_kyc_version k
        JOIN biz_merchant m
          ON m.tenant_id = k.tenant_id
         AND m.id = k.merchant_id
         AND m.deleted = 0
        WHERE k.tenant_id = #{tenantId}
          AND k.id = #{kycVersionId}
          AND k.deleted = 0
        """)
    KycDraftDefaultContextDO selectDraftContext(@Param("tenantId") Long tenantId,
                                                @Param("kycVersionId") Long kycVersionId);
}
