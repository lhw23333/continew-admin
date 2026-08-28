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

package top.continew.admin.system.config.file;

import cn.hutool.core.lang.Dict;
import org.dromara.x.file.storage.core.FileInfo;
import org.junit.jupiter.api.Test;
import top.continew.admin.system.mapper.FileMapper;
import top.continew.admin.system.mapper.StorageMapper;


import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FileRecorderImplTest {

    @Test
    void shouldSkipGenericRecordingForUnmanagedStoragePlatform() {
        FileMapper fileMapper = mock(FileMapper.class);
        StorageMapper storageMapper = mock(StorageMapper.class);
        FileInfo fileInfo = mock(FileInfo.class);
        when(fileInfo.getAttr()).thenReturn(new Dict());
        when(fileInfo.getPlatform()).thenReturn("kyc-private");

        FileRecorderImpl recorder = new FileRecorderImpl(fileMapper, storageMapper);

        assertTrue(recorder.save(fileInfo));
        verifyNoInteractions(fileMapper, storageMapper);
    }
}
