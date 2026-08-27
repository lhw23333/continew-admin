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

package top.continew.admin.config.onboarding;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.continew.admin.merchant.onboarding.application.OnboardingProcessReferencePolicy;
import top.continew.admin.merchant.onboarding.application.OnboardingProcessReferenceTokenService;
import top.continew.admin.merchant.security.crypto.VersionedKeyProvider;

import java.net.URI;
import java.time.Duration;

/** Process action-link signing and bounded public-route policy. */
@Configuration
public class OnboardingProcessReferenceConfiguration {

    @Bean
    @ConditionalOnMissingBean(OnboardingProcessReferenceTokenService.class)
    public OnboardingProcessReferenceTokenService onboardingProcessReferenceTokenService(VersionedKeyProvider keyProvider) {
        return new OnboardingProcessReferenceTokenService(keyProvider);
    }

    @Bean
    @ConditionalOnMissingBean(OnboardingProcessReferencePolicy.class)
    public OnboardingProcessReferencePolicy onboardingProcessReferencePolicy(@Value("${merchant.onboarding.process-reference-base-url:https://localhost/onboarding/action}") URI baseUri,
                                                                             @Value("${merchant.onboarding.process-reference-validity:PT10M}") Duration validity) {
        return new OnboardingProcessReferencePolicy(baseUri, validity);
    }
}
