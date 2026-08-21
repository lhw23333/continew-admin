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

import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import top.continew.admin.merchant.kyc.attachment.AttachmentContentInspectionPort;
import top.continew.admin.merchant.kyc.attachment.KycAttachmentException;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Apache Tika MIME detection plus basic image/PDF readability checks. */
@Component
public class TikaAttachmentContentInspectionAdapter implements AttachmentContentInspectionPort {

    private final Tika tika = new Tika();

    @Override
    public InspectionResult inspect(byte[] content, String originalName) {
        try {
            String detectedMime = tika.detect(content, originalName);
            return new InspectionResult(detectedMime, isReadable(content, detectedMime));
        } catch (IOException ex) {
            throw new KycAttachmentException("KYC attachment content inspection failed", ex);
        }
    }

    private boolean isReadable(byte[] content, String detectedMime) throws IOException {
        if (detectedMime != null && detectedMime.startsWith("image/")) {
            return ImageIO.read(new ByteArrayInputStream(content)) != null;
        }
        if ("application/pdf".equalsIgnoreCase(detectedMime)) {
            if (content.length < 8) {
                return false;
            }
            String prefix = new String(content, 0, Math.min(content.length, 8), StandardCharsets.US_ASCII);
            String suffix = new String(content, Math.max(0, content.length - Math.min(content.length, 1024)), Math
                .min(content.length, 1024), StandardCharsets.US_ASCII);
            return prefix.startsWith("%PDF-") && suffix.contains("%%EOF");
        }
        return false;
    }
}
