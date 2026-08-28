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

package top.continew.admin.config.kyc;

import org.dromara.x.file.storage.core.FileStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.continew.admin.merchant.kyc.attachment.KycAttachmentPolicy;
import top.continew.admin.merchant.kyc.attachment.MalwareScannerPort;
import top.continew.admin.merchant.kyc.attachment.PrivateObjectStoragePort;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

/** Phase-one private KYC attachment policy and adapters. */
@Configuration
public class KycAttachmentConfiguration {

    @Bean
    @ConditionalOnMissingBean(KycAttachmentPolicy.class)
    public KycAttachmentPolicy kycAttachmentPolicy(@Value("${merchant.kyc.max-size-bytes:10485760}") long maxSizeBytes,
                                                   @Value("${merchant.kyc.max-per-evidence-type:5}") int maxPerEvidenceType,
                                                   @Value("${merchant.kyc.max-per-version:20}") int maxPerVersion,
                                                   @Value("${merchant.kyc.access-expiry:PT5M}") Duration accessExpiry) {
        return new KycAttachmentPolicy(maxSizeBytes, maxPerEvidenceType, maxPerVersion, accessExpiry, Set
            .of("jpg", "jpeg", "png", "pdf"), Map
                .of("jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png", "pdf", "application/pdf"));
    }

    @Bean
    @ConditionalOnMissingBean(MalwareScannerPort.class)
    public MalwareScannerPort malwareScannerPort(@Value("${merchant.kyc.synthetic-clean-scanner-enabled:false}") boolean syntheticCleanScannerEnabled) {
        return syntheticCleanScannerEnabled
            ? new SyntheticCleanMalwareScannerAdapter()
            : new NoMalwareScannerAdapter();
    }

    @Bean
    @ConditionalOnMissingBean(PrivateObjectStoragePort.class)
    public PrivateObjectStoragePort privateObjectStoragePort(FileStorageService fileStorageService,
                                                             @Value("${merchant.kyc.storage-code:}") String storageCode,
                                                             @Value("${merchant.kyc.private-bucket-acknowledged:false}") boolean privateBucketAcknowledged) {
        return new XFilePrivateObjectStorageAdapter(fileStorageService, storageCode, privateBucketAcknowledged);
    }
}
