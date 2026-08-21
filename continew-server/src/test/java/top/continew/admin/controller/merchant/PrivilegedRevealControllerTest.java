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

package top.continew.admin.controller.merchant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import top.continew.admin.common.context.UserContext;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.merchant.security.reveal.MerchantSensitiveField;
import top.continew.admin.merchant.security.reveal.PrivilegedRevealDeniedException;
import top.continew.admin.merchant.security.reveal.PrivilegedRevealResult;
import top.continew.admin.merchant.security.reveal.PrivilegedRevealService;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PrivilegedRevealControllerTest {

    @AfterEach
    void clearContext() {
        UserContextHolder.clearContext();
    }

    @Test
    void appliesNoStoreHeadersAndDoesNotExposeValueThroughToString() {
        PrivilegedRevealService service = mock(PrivilegedRevealService.class);
        LocalDateTime now = LocalDateTime.of(2026, 8, 20, 12, 0);
        when(service.reveal(any()))
            .thenReturn(new PrivilegedRevealResult(MerchantSensitiveField.CONTACT_MOBILE, "13800138000", now));
        PrivilegedRevealController controller = new PrivilegedRevealController(service);
        UserContext context = new UserContext();
        context.setId(3001L);
        context.setTenantId(1001L);
        UserContextHolder.setContext(context, false);
        PrivilegedRevealController.PrivilegedRevealReq req = new PrivilegedRevealController.PrivilegedRevealReq();
        req.setField(MerchantSensitiveField.CONTACT_MOBILE);
        req.setReason("Review settlement details");
        req.setPassword("rsa-proof");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        PrivilegedRevealController.PrivilegedRevealResp result = controller.reveal(2001L, req, request, response);

        assertEquals("13800138000", result.value());
        assertFalse(result.toString().contains("13800138000"));
        assertEquals("no-store, no-cache, max-age=0, must-revalidate, private", response
            .getHeader(HttpHeaders.CACHE_CONTROL));
        assertEquals("no-cache", response.getHeader(HttpHeaders.PRAGMA));
        assertEquals("no-referrer", response.getHeader("Referrer-Policy"));

    }

    @Test
    void appliesNoStoreHeadersBeforePermissionOrScopeDenial() {
        PrivilegedRevealService service = mock(PrivilegedRevealService.class);
        when(service.reveal(any())).thenThrow(new PrivilegedRevealDeniedException());
        PrivilegedRevealController controller = new PrivilegedRevealController(service);
        UserContext context = new UserContext();
        context.setId(3001L);
        context.setTenantId(1001L);
        UserContextHolder.setContext(context, false);
        PrivilegedRevealController.PrivilegedRevealReq req = new PrivilegedRevealController.PrivilegedRevealReq();
        req.setField(MerchantSensitiveField.CONTACT_MOBILE);
        req.setReason("Review settlement details");
        req.setPassword("rsa-proof");
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThrows(PrivilegedRevealDeniedException.class, () -> controller.reveal(2001L, req, request, response));

        assertEquals("no-store, no-cache, max-age=0, must-revalidate, private", response
            .getHeader(HttpHeaders.CACHE_CONTROL));
    }
}
