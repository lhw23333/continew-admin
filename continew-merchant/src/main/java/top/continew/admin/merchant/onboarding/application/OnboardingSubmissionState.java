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

import java.time.LocalDateTime;

/** Locked application and exact KYC-version state used by final submission. */
public record OnboardingSubmissionState(Long tenantId, Long applicationId, String applicationNo, Long merchantId,
                                        Long owningAgentId, String channelCode, String productCode,
                                        String channelConfigVersion, String requirementVersion,
                                        String applicationStatus, String idempotencyKey, Long submittedBy,
                                        LocalDateTime submittedTime, Long applicationRowVersion, Long kycVersionId,
                                        Integer kycVersionNo, String kycStatus, Long kycRowVersion,
                                        LocalDateTime frozenTime) {
}
