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

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.junit.jupiter.api.Test;
import top.continew.admin.merchant.onboarding.application.ProcessReferenceException;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ZxingProcessQrCodeAdapterTest {

    @Test
    void encodedPngDecodesToTheExactSignedProcessUrl() throws Exception {
        String content = "https://app.example/onboarding/action?token=abc.DEF_123";
        ZxingProcessQrCodeAdapter adapter = new ZxingProcessQrCodeAdapter(240);

        byte[] png = Base64.getDecoder().decode(adapter.encodePngBase64(content));
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(ImageIO
            .read(new ByteArrayInputStream(png)))));

        assertEquals(content, new MultiFormatReader().decode(bitmap).getText());
        assertThrows(ProcessReferenceException.class, () -> adapter.encodePngBase64(" "));
    }
}
