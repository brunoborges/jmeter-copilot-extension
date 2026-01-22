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

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.github.copilot.sdk.CopilotClient;
import com.github.copilot.sdk.CopilotSession;
import com.github.copilot.sdk.events.AbstractSessionEvent;
import com.github.copilot.sdk.events.AssistantMessageDeltaEvent;
import com.github.copilot.sdk.events.AssistantMessageEvent;
import com.github.copilot.sdk.events.SessionErrorEvent;
import com.github.copilot.sdk.json.MessageOptions;
import com.github.copilot.sdk.json.SessionConfig;

/**
 * Service that wraps the Copilot SDK to provide chat functionality
 * specifically for JMeter test plan generation.
 */
public class CopilotChatService implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(CopilotChatService.class.getName());

    private static final String JMETER_SYSTEM_PROMPT = """
        You are an expert Apache JMeter test plan generator. Your role is to help users create
        JMeter test plans by generating valid JMeter XML (.jmx) format.

        When the user asks you to create a test plan or any test component, you should:
        1. Generate valid JMeter XML that can be loaded directly into JMeter
        2. Use proper JMeter element types (TestPlan, ThreadGroup, HTTPSamplerProxy, etc.)
        3. Include all required properties and attributes
        4. Wrap the XML in a code block with ```xml markers

        Common JMeter elements you can create:
        - Test Plans with ThreadGroups
        - HTTP Request Samplers
        - Timers (Constant, Gaussian Random, Uniform Random)
        - Assertions (Response, Duration, Size, JSON, XPath)
        - Listeners (View Results Tree, Summary Report, Aggregate Report)
        - Config Elements (HTTP Header Manager, User Defined Variables, CSV Data Set)
        - Pre/Post Processors (JSR223, Regular Expression Extractor, JSON Extractor)
        - Controllers (Loop, If, While, Transaction, Random)

        IMPORTANT GUIDELINES:
        - When adding listeners like View Results Tree or Summary Report, do NOT set a filename
          property. Leave the filename empty so results are displayed in the GUI only.
        - For ResultCollector elements, set <stringProp name="filename"></stringProp> (empty value)
        - This prevents file conflict warnings when running tests in the GUI

        Always ensure the generated XML is complete and valid. Include the XML declaration
        and proper jmeterTestPlan wrapper with version attributes.
        """;

    private final CopilotClient client;
    private CopilotSession session;
    private final ConversationHistory conversationHistory;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private Consumer<String> streamingHandler;
    private Consumer<ChatMessage> messageHandler;
    private Closeable eventSubscription;
    private String model = "claude-sonnet-4"; // Default model

    /**
     * Creates a new CopilotChatService with the default CopilotClient.
     */
    public CopilotChatService() {
        this(new CopilotClient());
    }

    /**
     * Creates a new CopilotChatService with a custom CopilotClient.
     * Useful for testing with mocked clients.
     */
    public CopilotChatService(CopilotClient client) {
        this.client = client;
        this.conversationHistory = new ConversationHistory();
    }

    /**
     * Connects to the Copilot service and creates a session.
     *
     * @return CompletableFuture that completes when connected
     */
    public CompletableFuture<Void> connect() {
        return client.start()
            .thenCompose(v -> createSession())
            .thenAccept(s -> {
                this.session = s;
                connected.set(true);
                subscribeToEvents();
            });
    }

    private CompletableFuture<CopilotSession> createSession() {
        SessionConfig config = new SessionConfig()
            .setStreaming(true)
            .setModel(model)
            .setSystemMessage(new com.github.copilot.sdk.json.SystemMessageConfig()
                .setMode(com.github.copilot.sdk.SystemMessageMode.APPEND)
                .setContent(JMETER_SYSTEM_PROMPT));

        return client.createSession(config);
    }

    /**
     * Sets the AI model to use for the session.
     * Must be called before connect() to take effect.
     *
     * @param model The model name to use
     */
    public void setModel(String model) {
        this.model = model;
    }

    /**
     * Returns the currently configured AI model name.
     *
     * @return The model name
     */
    public String getModel() {
        return model;
    }

    /**
     * Returns all available AI models from the Copilot API.
     *
     * @return List of available model names
     */
    public java.util.List<String> getAvailableModels() {
        try {
            return client.listModels()
                .join()
                .stream()
                .map(modelInfo -> modelInfo.getId())
                .toList();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to fetch models from API, using default", e);
            return java.util.List.of("claude-sonnet-4", "gpt-4.1");
        }
    }

    private void subscribeToEvents() {
        if (session != null) {
            eventSubscription = session.on(this::handleEvent);
        }
    }

    private void handleEvent(AbstractSessionEvent event) {
        try {
            if (event instanceof AssistantMessageDeltaEvent deltaEvent) {
                String delta = deltaEvent.getData().getDeltaContent();
                if (delta != null && streamingHandler != null) {
                    streamingHandler.accept(delta);
                }
            } else if (event instanceof AssistantMessageEvent messageEvent) {
                String content = messageEvent.getData().getContent();
                if (content != null && !content.isBlank()) {
                    ChatMessage message = new ChatMessage(ChatMessage.Role.ASSISTANT, content);
                    conversationHistory.addMessage(message);
                    if (messageHandler != null) {
                        messageHandler.accept(message);
                    }
                }
            } else if (event instanceof SessionErrorEvent errorEvent) {
                LOG.log(Level.WARNING, "Session error: {0}",
                    errorEvent.getData() != null ? errorEvent.getData().getMessage() : "Unknown error");
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error handling event", e);
        }
    }

    /**
     * Sends a message to Copilot and returns immediately.
     * Use the streaming handler to receive response chunks.
     *
     * @param prompt The user's message
     * @return CompletableFuture with the message ID
     */
    public CompletableFuture<String> sendMessage(String prompt) {
        if (!connected.get() || session == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Not connected. Call connect() first."));
        }

        ChatMessage userMessage = new ChatMessage(ChatMessage.Role.USER, prompt);
        conversationHistory.addMessage(userMessage);

        return session.send(new MessageOptions().setPrompt(prompt));
    }

    /**
     * Sends a message and waits for the complete response.
     *
     * @param prompt The user's message
     * @return CompletableFuture with the assistant's response
     */
    public CompletableFuture<String> sendMessageAndWait(String prompt) {
        if (!connected.get() || session == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Not connected. Call connect() first."));
        }

        ChatMessage userMessage = new ChatMessage(ChatMessage.Role.USER, prompt);
        conversationHistory.addMessage(userMessage);

        return session.sendAndWait(new MessageOptions().setPrompt(prompt))
            .thenApply(response -> {
                if (response != null && response.getData() != null) {
                    return response.getData().getContent();
                }
                return null;
            });
    }

    /**
     * Sets a handler for streaming response chunks.
     *
     * @param handler Consumer that receives each chunk of the response
     */
    public void setStreamingHandler(Consumer<String> handler) {
        this.streamingHandler = handler;
    }

    /**
     * Sets a handler for complete messages.
     *
     * @param handler Consumer that receives complete ChatMessage objects
     */
    public void setMessageHandler(Consumer<ChatMessage> handler) {
        this.messageHandler = handler;
    }

    /**
     * Returns the conversation history.
     */
    public ConversationHistory getConversationHistory() {
        return conversationHistory;
    }

    /**
     * Clears the conversation and starts a new session.
     *
     * @return CompletableFuture that completes when the new session is ready
     */
    public CompletableFuture<Void> clearConversation() {
        conversationHistory.clear();

        if (session != null) {
            try {
                if (eventSubscription != null) {
                    eventSubscription.close();
                }
                session.close();
            } catch (Exception e) {
                LOG.log(Level.FINE, "Error closing session", e);
            }
            session = null;
        }

        // Only create a new session if we're connected
        if (connected.get()) {
            return createSession()
                .thenAccept(s -> {
                    this.session = s;
                    subscribeToEvents();
                })
                .exceptionally(ex -> {
                    LOG.log(Level.WARNING, "Failed to create new session after clear", ex);
                    return null;
                });
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Returns whether the service is connected.
     */
    public boolean isConnected() {
        return connected.get();
    }

    /**
     * Aborts the current request.
     */
    public CompletableFuture<Void> abort() {
        if (session != null) {
            return session.abort();
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void close() {
        connected.set(false);

        if (eventSubscription != null) {
            try {
                eventSubscription.close();
            } catch (IOException e) {
                LOG.log(Level.FINE, "Error closing event subscription", e);
            }
        }

        if (session != null) {
            try {
                session.close();
            } catch (Exception e) {
                LOG.log(Level.FINE, "Error closing session", e);
            }
        }

        try {
            client.close();
        } catch (Exception e) {
            LOG.log(Level.FINE, "Error closing client", e);
        }
    }
}
