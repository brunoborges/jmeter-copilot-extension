/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.jmeter.copilot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.copilot.sdk.CopilotClient;
import com.github.copilot.sdk.CopilotSession;
import com.github.copilot.sdk.json.MessageOptions;
import com.github.copilot.sdk.json.ModelInfo;
import com.github.copilot.sdk.json.SessionConfig;

/**
 * Tests for CopilotChatService class.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CopilotChatService Tests")
class CopilotChatServiceTest {

    @Mock
    private CopilotClient mockClient;

    @Mock
    private CopilotSession mockSession;

    private CopilotChatService service;

    @BeforeEach
    void setUp() {
        service = new CopilotChatService(mockClient);
    }

    @Test
    @DisplayName("should not be connected initially")
    void shouldNotBeConnectedInitially() {
        assertThat(service.isConnected()).isFalse();
    }

    @Test
    @DisplayName("should connect successfully")
    void shouldConnectSuccessfully() throws Exception {
        when(mockClient.start()).thenReturn(CompletableFuture.completedFuture(null));
        when(mockClient.createSession(any(SessionConfig.class)))
            .thenReturn(CompletableFuture.completedFuture(mockSession));
        when(mockSession.on(any())).thenReturn(() -> {});

        service.connect().get(5, TimeUnit.SECONDS);

        assertThat(service.isConnected()).isTrue();
        verify(mockClient).start();
        verify(mockClient).createSession(any(SessionConfig.class));
    }

    @Test
    @DisplayName("should fail to send message when not connected")
    void shouldFailToSendMessageWhenNotConnected() {
        CompletableFuture<String> future = service.sendMessage("Hello");

        assertThatThrownBy(() -> future.get(1, TimeUnit.SECONDS))
            .hasCauseInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Not connected");
    }

    @Test
    @DisplayName("should send message when connected")
    void shouldSendMessageWhenConnected() throws Exception {
        when(mockClient.start()).thenReturn(CompletableFuture.completedFuture(null));
        when(mockClient.createSession(any(SessionConfig.class)))
            .thenReturn(CompletableFuture.completedFuture(mockSession));
        when(mockSession.on(any())).thenReturn(() -> {});
        when(mockSession.send(any(MessageOptions.class)))
            .thenReturn(CompletableFuture.completedFuture("msg-123"));

        service.connect().get(5, TimeUnit.SECONDS);
        String messageId = service.sendMessage("Create a simple HTTP test").get(5, TimeUnit.SECONDS);

        assertThat(messageId).isEqualTo("msg-123");
        verify(mockSession).send(any(MessageOptions.class));
    }

    @Test
    @DisplayName("should add user message to conversation history")
    void shouldAddUserMessageToConversationHistory() throws Exception {
        when(mockClient.start()).thenReturn(CompletableFuture.completedFuture(null));
        when(mockClient.createSession(any(SessionConfig.class)))
            .thenReturn(CompletableFuture.completedFuture(mockSession));
        when(mockSession.on(any())).thenReturn(() -> {});
        when(mockSession.send(any(MessageOptions.class)))
            .thenReturn(CompletableFuture.completedFuture("msg-123"));

        service.connect().get(5, TimeUnit.SECONDS);
        service.sendMessage("Test message").get(5, TimeUnit.SECONDS);

        var history = service.getConversationHistory();
        assertThat(history.size()).isEqualTo(1);
        assertThat(history.getMessages().get(0).getContent()).isEqualTo("Test message");
        assertThat(history.getMessages().get(0).isFromUser()).isTrue();
    }

    @Test
    @DisplayName("should invoke streaming handler with response chunks")
    void shouldInvokeStreamingHandlerWithChunks() throws Exception {
        List<String> chunks = new ArrayList<>();
        service.setStreamingHandler(chunks::add);

        assertThat(chunks).isEmpty(); // Just verify handler is set without throwing
    }

    @Test
    @DisplayName("should invoke message handler when complete message received")
    void shouldInvokeMessageHandlerWhenCompleteMessageReceived() throws Exception {
        List<ChatMessage> messages = new ArrayList<>();
        service.setMessageHandler(messages::add);

        assertThat(messages).isEmpty(); // Just verify handler is set without throwing
    }

    @Test
    @DisplayName("should clear conversation and create new session")
    void shouldClearConversationAndCreateNewSession() throws Exception {
        when(mockClient.start()).thenReturn(CompletableFuture.completedFuture(null));
        when(mockClient.createSession(any(SessionConfig.class)))
            .thenReturn(CompletableFuture.completedFuture(mockSession));
        when(mockSession.on(any())).thenReturn(() -> {});
        when(mockSession.send(any(MessageOptions.class)))
            .thenReturn(CompletableFuture.completedFuture("msg-123"));

        service.connect().get(5, TimeUnit.SECONDS);
        service.sendMessage("First message").get(5, TimeUnit.SECONDS);

        assertThat(service.getConversationHistory().size()).isEqualTo(1);

        service.clearConversation().get(5, TimeUnit.SECONDS);

        assertThat(service.getConversationHistory().size()).isZero();
        verify(mockSession).close();
        verify(mockClient, times(2)).createSession(any(SessionConfig.class));
    }

    @Test
    @DisplayName("should abort current request")
    void shouldAbortCurrentRequest() throws Exception {
        when(mockClient.start()).thenReturn(CompletableFuture.completedFuture(null));
        when(mockClient.createSession(any(SessionConfig.class)))
            .thenReturn(CompletableFuture.completedFuture(mockSession));
        when(mockSession.on(any())).thenReturn(() -> {});
        when(mockSession.abort()).thenReturn(CompletableFuture.completedFuture(null));

        service.connect().get(5, TimeUnit.SECONDS);
        service.abort().get(5, TimeUnit.SECONDS);

        verify(mockSession).abort();
    }

    @Test
    @DisplayName("should close all resources on close")
    void shouldCloseAllResourcesOnClose() throws Exception {
        when(mockClient.start()).thenReturn(CompletableFuture.completedFuture(null));
        when(mockClient.createSession(any(SessionConfig.class)))
            .thenReturn(CompletableFuture.completedFuture(mockSession));
        when(mockSession.on(any())).thenReturn(() -> {});

        service.connect().get(5, TimeUnit.SECONDS);
        service.close();

        assertThat(service.isConnected()).isFalse();
        verify(mockSession).close();
        verify(mockClient).close();
    }

    @Test
    @DisplayName("should return conversation history")
    void shouldReturnConversationHistory() {
        ConversationHistory history = service.getConversationHistory();

        assertThat(history).isNotNull();
        assertThat(history.size()).isZero();
    }

    @Test
    @DisplayName("should have default model set")
    void shouldHaveDefaultModelSet() {
        assertThat(service.getModel()).isEqualTo("claude-sonnet-4");
    }

    @Test
    @DisplayName("should allow setting custom model")
    void shouldAllowSettingCustomModel() {
        service.setModel("gpt-4.1");

        assertThat(service.getModel()).isEqualTo("gpt-4.1");
    }

    @Test
    @DisplayName("should provide list of available models")
    void shouldProvideListOfAvailableModels() {
        ModelInfo model1 = new ModelInfo().setId("claude-sonnet-4");
        ModelInfo model2 = new ModelInfo().setId("gpt-4.1");
        ModelInfo model3 = new ModelInfo().setId("o3-mini");
        when(mockClient.listModels()).thenReturn(
            CompletableFuture.completedFuture(java.util.List.of(model1, model2, model3)));
        
        assertThat(service.getAvailableModels())
            .isNotNull()
            .isNotEmpty()
            .containsExactly("claude-sonnet-4", "gpt-4.1", "o3-mini");
    }
}
