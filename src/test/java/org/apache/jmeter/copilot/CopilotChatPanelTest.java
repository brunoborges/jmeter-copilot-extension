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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.BorderLayout;
import java.util.concurrent.CompletableFuture;

import javax.swing.JButton;
import javax.swing.JTextArea;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests for CopilotChatPanel class.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CopilotChatPanel Tests")
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class CopilotChatPanelTest {

    @Mock
    private CopilotChatService mockChatService;

    @Mock
    private JMeterXmlParser mockXmlParser;

    private CopilotChatPanel panel;

    @BeforeEach
    void setUp() {
        // Create panel with mocked services
        when(mockChatService.getAvailableModels()).thenReturn(java.util.List.of("claude-sonnet-4", "gpt-4.1"));
        when(mockChatService.getModel()).thenReturn("claude-sonnet-4");
        when(mockChatService.getConversationHistory()).thenReturn(new ConversationHistory());
        when(mockChatService.setModelOnActiveSession(any())).thenReturn(CompletableFuture.completedFuture(null));
        panel = new CopilotChatPanel(mockChatService, mockXmlParser);
    }

    @Test
    @DisplayName("should create panel with correct layout")
    void shouldCreatePanelWithCorrectLayout() {
        assertThat(panel.getLayout()).isInstanceOf(BorderLayout.class);
    }

    @Test
    @DisplayName("should have preferred width of 400")
    void shouldHavePreferredWidth() {
        assertThat(panel.getPreferredSize().width).isEqualTo(400);
    }

    @Test
    @DisplayName("should have input area")
    void shouldHaveInputArea() {
        JTextArea inputArea = panel.getInputArea();
        assertThat(inputArea).isNotNull();
    }

    @Test
    @DisplayName("should have send button")
    void shouldHaveSendButton() {
        JButton sendButton = panel.getSendButton();
        assertThat(sendButton).isNotNull();
        assertThat(sendButton.getText()).isEqualTo("Send");
    }

    @Test
    @DisplayName("should have load XML button")
    void shouldHaveLoadXmlButton() {
        JButton loadButton = panel.getLoadXmlButton();
        assertThat(loadButton).isNotNull();
        assertThat(loadButton.getText()).isEqualTo("Load to Test Plan");
        assertThat(loadButton.isEnabled()).isFalse(); // Initially disabled
    }

    @Test
    @DisplayName("should not be connected initially")
    void shouldNotBeConnectedInitially() {
        when(mockChatService.isConnected()).thenReturn(false);
        assertThat(panel.isConnected()).isFalse();
    }

    @Test
    @DisplayName("should not be processing initially")
    void shouldNotBeProcessingInitially() {
        assertThat(panel.isProcessing()).isFalse();
    }

    @Test
    @DisplayName("should connect when connect is called")
    void shouldConnectWhenConnectIsCalled() {
        when(mockChatService.connect()).thenReturn(CompletableFuture.completedFuture(null));

        panel.connect();

        verify(mockChatService).connect();
    }

    @Test
    @DisplayName("should disconnect when disconnect is called")
    void shouldDisconnectWhenDisconnectIsCalled() {
        panel.disconnect();

        verify(mockChatService).close();
    }

    @Test
    @DisplayName("should return chat service")
    void shouldReturnChatService() {
        assertThat(panel.getChatService()).isSameAs(mockChatService);
    }

    @Test
    @DisplayName("should set streaming handler on chat service")
    void shouldSetStreamingHandlerOnChatService() {
        verify(mockChatService).setStreamingHandler(any());
    }

    @Test
    @DisplayName("should set message handler on chat service")
    void shouldSetMessageHandlerOnChatService() {
        verify(mockChatService).setMessageHandler(any());
    }

    @Test
    @DisplayName("should have placeholder text in input area")
    void shouldHavePlaceholderTextInInputArea() {
        String text = panel.getInputArea().getText();
        assertThat(text).contains("Describe");
    }

    @Test
    @DisplayName("should change model without reconnect when connected")
    void shouldChangeModelWithoutReconnectWhenConnected() {
        when(mockChatService.isConnected()).thenReturn(true);
        panel.getModelSelector().setSelectedItem("gpt-4.1");

        verify(mockChatService).setModelOnActiveSession("gpt-4.1");
        verify(mockChatService, never()).connect();
    }

    @Test
    @DisplayName("should reconnect when model changes while disconnected")
    void shouldReconnectWhenModelChangesWhileDisconnected() {
        when(mockChatService.isConnected()).thenReturn(false);
        when(mockChatService.connect()).thenReturn(CompletableFuture.completedFuture(null));
        panel.getModelSelector().setSelectedItem("gpt-4.1");

        verify(mockChatService).setModel("gpt-4.1");
        verify(mockChatService).connect();
        verify(mockChatService, never()).setModelOnActiveSession(any());
    }
}
