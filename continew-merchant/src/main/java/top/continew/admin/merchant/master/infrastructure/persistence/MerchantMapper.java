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

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.continew.admin.merchant.master.application.MerchantListQuery;
import top.continew.starter.data.mapper.BaseMapper;

import java.util.List;

/** Merchant master mapper. */
@Mapper
public interface MerchantMapper extends BaseMapper<MerchantDO> {

    IPage<MerchantQueryRow> selectScopedPage(@Param("page") Page<MerchantQueryRow> page,
                                             @Param("tenantId") Long tenantId,
                                             @Param("actorUserId") Long actorUserId,
                                             @Param("authorizedAgentIds") List<Long> authorizedAgentIds,
                                             @Param("query") MerchantListQuery query);

    MerchantQueryRow selectScopedDetail(@Param("tenantId") Long tenantId,
                                        @Param("actorUserId") Long actorUserId,
                                        @Param("authorizedAgentIds") List<Long> authorizedAgentIds,
                                        @Param("merchantId") Long merchantId);

    List<MerchantChannelQueryRow> selectLatestChannelSummaries(@Param("tenantId") Long tenantId,
                                                               @Param("merchantIds") List<Long> merchantIds);
}
