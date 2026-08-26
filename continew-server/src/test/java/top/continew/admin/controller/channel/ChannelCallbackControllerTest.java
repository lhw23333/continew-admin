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

package top.continew.admin.controller.channel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import top.continew.admin.channel.api.ChannelCallbackException;
import top.continew.admin.channel.api.ChannelEventProcessingException;
import top.continew.admin.channel.dto.RawChannelCallback;
import top.continew.admin.channel.dto.VerifiedChannelCallback;
import top.continew.admin.channel.service.ChannelCallbackVerifier;
import top.continew.admin.channel.service.ChannelEventProcessor;
import top.continew.admin.config.channel.ChannelCallbackTenantExecutor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChannelCallbackControllerTest {
    private ChannelCallbackVerifier verifier;
    private ChannelEventProcessor eventProcessor;
    private ChannelCallbackTenantExecutor tenantExecutor;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        verifier = mock(ChannelCallbackVerifier.class);
        eventProcessor = mock(ChannelEventProcessor.class);
        tenantExecutor = mock(ChannelCallbackTenantExecutor.class);
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return null;
        }).when(tenantExecutor).execute(anyLong(), any());
        mockMvc = MockMvcBuilders
            .standaloneSetup(new ChannelCallbackController(verifier, eventProcessor, tenantExecutor))
            .build();
    }

    @Test
    void verifiedCallbackReturnsAcceptedWithoutEchoingSensitiveHeadersOrBody() throws Exception {
        byte[] payload = "{\"legalIdentifier\":\"91350211M000100Y43\"}"
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        VerifiedChannelCallback verifiedCallback = mock(VerifiedChannelCallback.class);
        when(verifier.verify(any())).thenReturn(verifiedCallback);

        mockMvc.perform(post("/channel/callbacks/943/SYNTHETIC/ONBOARDING/CFG-943")
            .header(ChannelCallbackController.TIMESTAMP_HEADER, "1787544000000")
            .header(ChannelCallbackController.NONCE_HEADER, "nonce-943-callback")
            .header(ChannelCallbackController.KEY_VERSION_HEADER, "ref-1234567890abcdef")
            .header(ChannelCallbackController.SIGNATURE_HEADER, "a".repeat(43))
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload)).andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("ACCEPTED"));

        ArgumentCaptor<RawChannelCallback> callback = ArgumentCaptor.forClass(RawChannelCallback.class);
        verify(verifier).verify(callback.capture());
        assertEquals(943L, callback.getValue().tenantId());
        assertEquals("SYNTHETIC", callback.getValue().product().channelCode());
        assertArrayEquals(payload, callback.getValue().payload());
        verify(eventProcessor).process(verifiedCallback);
    }

    @Test
    void invalidCallbackReturnsGenericBadRequest() throws Exception {
        doThrow(new ChannelCallbackException(ChannelCallbackException.Code.SIGNATURE_INVALID)).when(verifier)
            .verify(any());

        mockMvc.perform(post("/channel/callbacks/943/SYNTHETIC/ONBOARDING/CFG-943")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void auditFailureReturnsGenericServiceUnavailable() throws Exception {
        doThrow(new ChannelCallbackException(ChannelCallbackException.Code.AUDIT_FAILED)).when(verifier).verify(any());

        mockMvc.perform(post("/channel/callbacks/943/SYNTHETIC/ONBOARDING/CFG-943")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void eventPersistenceFailureReturnsGenericServiceUnavailable() throws Exception {
        VerifiedChannelCallback verifiedCallback = mock(VerifiedChannelCallback.class);
        when(verifier.verify(any())).thenReturn(verifiedCallback);
        doThrow(new ChannelEventProcessingException(ChannelEventProcessingException.Code.PERSISTENCE_FAILED))
            .when(eventProcessor)
            .process(verifiedCallback);

        mockMvc.perform(post("/channel/callbacks/943/SYNTHETIC/ONBOARDING/CFG-943")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.status").value("REJECTED"));
    }
}
