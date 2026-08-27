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

package top.continew.admin.merchant.limit.application;

import top.continew.admin.merchant.limit.domain.LimitAdjustment;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** Tenant-explicit request and append-only history persistence boundary. */
public interface LimitAdjustmentRepository {

    Optional<LimitAdjustment> findActive(Long tenantId, Long merchantId, String channelCode, String platformCode);

    Optional<LimitAdjustment> findById(Long tenantId, Long merchantId, Long requestId);

    Optional<BigDecimal> findCurrentEffectiveLimit(Long tenantId,
                                                   Long merchantId,
                                                   String channelCode,
                                                   String platformCode,
                                                   String currency);

    LimitAdjustment insert(LimitAdjustmentDraft draft);

    void appendHistory(LimitAdjustmentHistoryDraft draft);

    List<LimitAdjustmentHistory> listHistory(Long tenantId, Long requestId);
}
