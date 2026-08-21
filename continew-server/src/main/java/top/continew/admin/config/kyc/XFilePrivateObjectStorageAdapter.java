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

import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.FileStorageService;
import org.dromara.x.file.storage.core.presigned.GeneratePresignedUrlResult;
import top.continew.admin.merchant.kyc.attachment.KycAttachmentException;
import top.continew.admin.merchant.kyc.attachment.PrivateObjectStoragePort;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/** Dedicated private S3/MinIO object adapter using opaque references and bounded presigned GET URLs. */
public class XFilePrivateObjectStorageAdapter implements PrivateObjectStoragePort {

    private static final String DELIMITER = "|";

    private final FileStorageService fileStorageService;
    private final String storageCode;
    private final boolean privateBucketAcknowledged;

    public XFilePrivateObjectStorageAdapter(FileStorageService fileStorageService,
                                            String storageCode,
                                            boolean privateBucketAcknowledged) {
        this.fileStorageService = fileStorageService;
        this.storageCode = storageCode == null ? "" : storageCode.trim();
        this.privateBucketAcknowledged = privateBucketAcknowledged;
    }

    @Override
    public StoredObject store(Long tenantId,
                              Long kycVersionId,
                              String originalName,
                              String detectedMime,
                              byte[] content,
                              boolean quarantine) {
        requireConfigured();
        String extension = extensionOf(originalName);
        String path = "kyc/%s/%d/%d/".formatted(quarantine ? "quarantine" : "objects", tenantId, kycVersionId);
        String saveFilename = UUID.randomUUID().toString().replace("-", "") + '.' + extension;
        FileInfo fileInfo;
        try {
            fileInfo = fileStorageService.of(content, originalName, detectedMime)
                .setPlatform(storageCode)
                .setPath(path)
                .setSaveFilename(saveFilename)
                .setHashCalculatorSha256(true)
                .upload();
        } catch (RuntimeException ex) {
            throw new KycAttachmentException("Private KYC object upload failed", ex);
        }
        if (fileInfo == null || fileInfo.getFilename() == null || fileInfo.getFilename().isBlank()) {
            if (fileInfo != null) {
                try {
                    fileStorageService.delete(fileInfo);
                } catch (RuntimeException ignored) {
                    // Operations monitoring handles residual quarantine objects.
                }
            }
            throw new KycAttachmentException("Private KYC object reference is unavailable");
        }
        String platform = fileInfo.getPlatform() == null ? storageCode : fileInfo.getPlatform();
        return new StoredObject(encode(platform, fileInfo.getPath(), fileInfo.getFilename()));
    }

    @Override
    public TemporaryAccess createTemporaryAccess(String storageObjectId, Duration expiry) {
        ObjectReference reference = decode(storageObjectId);
        if (!fileStorageService.isSupportPresignedUrl(reference.platform())) {
            throw new KycAttachmentException("Configured KYC storage does not support temporary access");
        }
        Instant expiresAt = Instant.now().plus(expiry);
        GeneratePresignedUrlResult result;
        try {
            result = fileStorageService.generatePresignedUrl()
                .setPlatform(reference.platform())
                .setPath(reference.path())
                .setFilename(reference.filename())
                .setExpiration(Date.from(expiresAt))
                .generatePresignedUrl();
        } catch (RuntimeException ex) {
            throw new KycAttachmentException("Temporary KYC object access generation failed", ex);
        }
        if (result == null || result.getUrl() == null || result.getUrl().isBlank()) {
            throw new KycAttachmentException("Temporary KYC object access is unavailable");
        }
        return new TemporaryAccess(result.getUrl(), LocalDateTime.ofInstant(expiresAt, ZoneId.systemDefault()));
    }

    @Override
    public void delete(String storageObjectId) {
        ObjectReference reference = decode(storageObjectId);
        FileInfo fileInfo = new FileInfo();
        fileInfo.setPlatform(reference.platform());
        fileInfo.setPath(reference.path());
        fileInfo.setFilename(reference.filename());
        fileInfo.setUrl(reference.path() + reference.filename());
        try {
            fileStorageService.delete(fileInfo);
        } catch (RuntimeException ignored) {
            // Best-effort cleanup after metadata persistence failure; operations monitoring handles residual quarantine.
        }
    }

    private void requireConfigured() {
        if (storageCode.isBlank() || !privateBucketAcknowledged) {
            throw new KycAttachmentException("Private KYC storage is not configured");
        }
        if (!fileStorageService.isSupportPresignedUrl(storageCode)) {
            throw new KycAttachmentException("Configured KYC storage must support presigned access");
        }
    }

    private String extensionOf(String originalName) {
        int separator = originalName.lastIndexOf('.');
        if (separator < 0 || separator == originalName.length() - 1) {
            throw new KycAttachmentException("KYC attachment extension is required");
        }
        return originalName.substring(separator + 1).toLowerCase(Locale.ROOT);
    }

    private String encode(String platform, String path, String filename) {
        String objectId = platform + DELIMITER + (path == null ? "" : path) + DELIMITER + filename;
        if (objectId.length() > 255 || objectId.contains("\n") || objectId.contains("\r")) {
            throw new KycAttachmentException("Private KYC object reference is invalid");
        }
        return objectId;
    }

    private ObjectReference decode(String storageObjectId) {
        if (storageObjectId == null) {
            throw new KycAttachmentException("Private KYC object reference is invalid");
        }
        String[] parts = storageObjectId.split("\\|", 3);
        if (parts.length != 3 || parts[0].isBlank() || parts[2].isBlank()) {
            throw new KycAttachmentException("Private KYC object reference is invalid");
        }
        return new ObjectReference(parts[0], parts[1], parts[2]);
    }

    private record ObjectReference(String platform, String path, String filename) {
    }
}
