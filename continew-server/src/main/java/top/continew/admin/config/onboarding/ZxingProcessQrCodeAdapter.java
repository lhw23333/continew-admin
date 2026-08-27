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

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import top.continew.admin.merchant.onboarding.application.ProcessQrCodePort;
import top.continew.admin.merchant.onboarding.application.ProcessReferenceException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;

/** ZXing-backed PNG QR encoder with fixed dimensions and medium error correction. */
@Component
public class ZxingProcessQrCodeAdapter implements ProcessQrCodePort {

    private static final int MAX_CONTENT_LENGTH = 4096;

    private final int size;

    public ZxingProcessQrCodeAdapter(@Value("${merchant.onboarding.process-reference-qr-size:320}") int size) {
        if (size < 160 || size > 1024) {
            throw new IllegalArgumentException("Process QR size must be between 160 and 1024");
        }
        this.size = size;
    }

    @Override
    public String encodePngBase64(String content) {
        if (content == null || content.isBlank() || content.length() > MAX_CONTENT_LENGTH || content
            .getBytes(StandardCharsets.UTF_8).length > MAX_CONTENT_LENGTH) {
            throw new ProcessReferenceException(ProcessReferenceException.Code.QR_GENERATION_FAILED);
        }
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 2);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints);
            MatrixToImageWriter.writeToStream(matrix, "PNG", output);
            return Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (WriterException | IOException ex) {
            throw new ProcessReferenceException(ProcessReferenceException.Code.QR_GENERATION_FAILED, ex);
        }
    }
}
