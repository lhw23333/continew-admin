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
import org.junit.jupiter.api.Test;
import top.continew.admin.merchant.kyc.attachment.AttachmentContentInspectionPort;
import top.continew.admin.merchant.kyc.attachment.KycAttachmentException;
import top.continew.admin.merchant.kyc.attachment.KycAttachmentScanStatus;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class KycAttachmentAdaptersTest {

    @Test
    void tikaDetectsReadableImageAndPdfContent() throws Exception {
        TikaAttachmentContentInspectionAdapter adapter = new TikaAttachmentContentInspectionAdapter();
        ByteArrayOutputStream pngOutput = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), "png", pngOutput);
        byte[] png = pngOutput.toByteArray();
        byte[] pdf = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF".getBytes(StandardCharsets.US_ASCII);

        AttachmentContentInspectionPort.InspectionResult pngResult = adapter.inspect(png, "image.png");
        AttachmentContentInspectionPort.InspectionResult pdfResult = adapter.inspect(pdf, "document.pdf");

        assertEquals("image/png", pngResult.detectedMime());
        assertTrue(pngResult.readable());
        assertEquals("application/pdf", pdfResult.detectedMime());
        assertTrue(pdfResult.readable());
    }

    @Test
    void noScannerAndUnacknowledgedBucketFailClosed() {
        NoMalwareScannerAdapter scanner = new NoMalwareScannerAdapter();
        SyntheticCleanMalwareScannerAdapter syntheticScanner = new SyntheticCleanMalwareScannerAdapter();
        assertEquals(KycAttachmentScanStatus.CLEAN, syntheticScanner.scan(new byte[] {1}, "image/png", "a".repeat(64))
            .status());
        assertEquals(KycAttachmentScanStatus.UNAVAILABLE, scanner.scan(new byte[] {1}, "image/png", "a".repeat(64))
            .status());

        FileStorageService fileStorageService = mock(FileStorageService.class);
        XFilePrivateObjectStorageAdapter storage = new XFilePrivateObjectStorageAdapter(fileStorageService, "kyc-private", false);

        assertThrows(KycAttachmentException.class, () -> storage.store(1L, 2L, "image.png", "image/png", new byte[] {
            1}, true));
        verifyNoInteractions(fileStorageService);
        assertFalse(scanner.scan(new byte[] {1}, "image/png", "a".repeat(64))
            .status()
            .equals(KycAttachmentScanStatus.CLEAN));
    }
}
