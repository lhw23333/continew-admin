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

package top.continew.admin.merchant.review.application;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Persistence port for onboarding domain review state and immutable records. */
public interface OnboardingReviewRepository {

    Optional<OnboardingReviewContext> findContext(Long tenantId, Long applicationId);

    boolean updateApplicationStatus(Long tenantId,
                                    Long applicationId,
                                    String expectedStatus,
                                    String targetStatus,
                                    Long expectedVersion,
                                    Long actorUserId,
                                    LocalDateTime updateTime);

    void insert(ReviewRecordDraft draft);

    List<ReviewRecordEvidence> listEvidence(Long tenantId, String processInstanceId);
}
