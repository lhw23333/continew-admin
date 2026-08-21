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

package top.continew.admin.config.log;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import top.continew.admin.system.mapper.LogMapper;
import top.continew.admin.system.model.entity.LogDO;
import top.continew.admin.system.service.UserService;
import top.continew.starter.log.model.LogRecord;
import top.continew.starter.log.model.LogRequest;
import top.continew.starter.log.model.LogResponse;
import top.continew.starter.trace.autoconfigure.TraceProperties;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LogDaoLocalImplTest {

    @Test
    void sanitizesImmediatelyBeforeBuildingPersistenceEntity() {
        TraceProperties traceProperties = mock(TraceProperties.class);
        when(traceProperties.getTraceIdName()).thenReturn("X-Trace-Id");
        LogDaoLocalImpl logDao = new LogDaoLocalImpl(mock(UserService.class), mock(LogMapper.class), traceProperties, new SensitiveEndpointLogSanitizer(new ObjectMapper()));
        LogRequest request = new LogRequest(Set.of());
        request.setMethod("POST");
        request.setUrl(URI.create("https://localhost/merchant/masters/2001/sensitive/reveal"));
        request.setHeaders(Map.of("Authorization", "Bearer secret-token", "X-Tenant-Id", "1001"));
        request.setBody("{\"password\":\"rsa-proof\",\"reason\":\"13800138000\"}");
        request.setIp("127.0.0.1");
        request.setOs("Windows");
        LogResponse response = new LogResponse(Set.of());
        response.setStatus(200);
        response.setHeaders(Map.of("X-Trace-Id", "trace-1", "Set-Cookie", "session=secret"));
        response.setBody("{\"code\":0,\"msg\":\"ok\",\"data\":{\"value\":\"13800138000\"}}");
        LogRecord record = new LogRecord(Instant.parse("2026-08-20T04:00:00Z"), request, response, Duration
            .ofMillis(25));

        LogDO persisted = logDao.toDataObject(record);

        assertNull(persisted.getRequestBody());
        assertNull(persisted.getResponseBody());
        assertFalse(persisted.getRequestHeaders().contains("secret-token"));
        assertFalse(persisted.getResponseHeaders().contains("session=secret"));
        assertEquals("trace-1", persisted.getTraceId());
        assertEquals(200, persisted.getStatusCode());
        assertEquals(25L, persisted.getTimeTaken());
    }
}
