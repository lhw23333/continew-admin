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

package top.continew.admin.merchant.master.application;

import top.continew.admin.merchant.master.domain.MerchantStatus;
import top.continew.admin.merchant.master.domain.MerchantType;

import java.time.LocalDateTime;

/** Internal masked query projection shared by list and detail assembly. */
public record MerchantQueryRecord(Long id, Long owningAgentId, String merchantNo, MerchantType merchantType,
                                  String legalName, String shortName, String legalRepresentativeName,
                                  Long operatorUserId, String operatorUsername, Long reviewerUserId,
                                  String reviewerUsername, String contactName, String contactMobileMasked,
                                  String reviewerMobileMasked, String industry, String productDescription,
                                  MerchantStatus status, String disabledReason, Long certifiedKycVersionId,
                                  Long rowVersion, String owningAgentNo, String owningAgentName,
                                  LocalDateTime createTime, LocalDateTime updateTime) {
}
