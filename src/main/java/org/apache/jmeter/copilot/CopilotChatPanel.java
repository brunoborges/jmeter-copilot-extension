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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.text.DefaultEditorKit;

import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.action.ActionRouter;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jorphan.collections.HashTree;

/**
 * The main Copilot Chat panel that integrates into JMeter's UI.
 * Provides a chat interface for users to describe test plans and
 * receive AI-generated JMeter XML configurations.
 */
public class CopilotChatPanel extends JPanel {

    private static final Logger LOG = Logger.getLogger(CopilotChatPanel.class.getName());
    private static final int PREFERRED_WIDTH = 400;

    private final CopilotChatService chatService;
    private final JMeterXmlParser xmlParser;
    private final ChatMessageRenderer messageRenderer;

    // UI Components
    private JEditorPane messagesPane;
    private JTextArea inputArea;
    private JButton sendButton;
    private JButton loadXmlButton;
    private JButton clearButton;
    private JButton abortButton;
    private JButton showXmlButton;
    private JLabel statusLabel;
    private JScrollPane messagesScrollPane;
    private JComboBox<String> modelSelector;

    // State
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);
    private final StringBuilder streamingContent = new StringBuilder();
    private String lastGeneratedXml = null;
    private String lastJmxFilePath = null;
    private Consumer<HashTree> onLoadTestPlan;
    private JProgressBar progressBar;
    private JLabel progressLabel;

    /**
     * Creates a new CopilotChatPanel with default services.
     */
    public CopilotChatPanel() {
        this(new CopilotChatService(), new JMeterXmlParser());
    }

    /**
     * Creates a new CopilotChatPanel with custom services.
     * Useful for testing.
     */
    public CopilotChatPanel(CopilotChatService chatService, JMeterXmlParser xmlParser) {
        this.chatService = chatService;
        this.xmlParser = xmlParser;
        this.messageRenderer = new ChatMessageRenderer();

        initializeUI();
        setupEventHandlers();
    }

    private void initializeUI() {
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(PREFERRED_WIDTH, 600));
        setBorder(new EmptyBorder(5, 5, 5, 5));

        // Header panel
        JPanel headerPanel = createHeaderPanel();
        add(headerPanel, BorderLayout.NORTH);

        // Messages area
        messagesPane = messageRenderer.createMessagePane();
        messagesScrollPane = new JScrollPane(messagesPane);
        messagesScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        messagesScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        add(messagesScrollPane, BorderLayout.CENTER);

        // Input panel
        JPanel inputPanel = createInputPanel();
        add(inputPanel, BorderLayout.SOUTH);

        // Set initial state
        updateConnectionStatus(false);
    }

    private JPanel createHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(0, 0, 10, 0));

        // Title
        JLabel titleLabel = new JLabel("Copilot Chat");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        panel.add(titleLabel, BorderLayout.WEST);

        // Center panel with model selector
        JPanel centerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        JLabel modelLabel = new JLabel("Model:");
        centerPanel.add(modelLabel);

        modelSelector = new JComboBox<>(CopilotChatService.AVAILABLE_MODELS);
        modelSelector.setSelectedItem(chatService.getModel());
        modelSelector.setToolTipText("Select AI model");
        modelSelector.addActionListener(e -> {
            String selectedModel = (String) modelSelector.getSelectedItem();
            if (selectedModel != null) {
                chatService.setModel(selectedModel);
            }
        });
        centerPanel.add(modelSelector);
        panel.add(centerPanel, BorderLayout.CENTER);

        // Status and buttons
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));

        statusLabel = new JLabel("Disconnected");
        statusLabel.setForeground(Color.GRAY);
        rightPanel.add(statusLabel);

        clearButton = new JButton("Clear");
        clearButton.setToolTipText("Clear conversation");
        clearButton.addActionListener(e -> clearConversation());
        rightPanel.add(clearButton);

        panel.add(rightPanel, BorderLayout.EAST);

        return panel;
    }

    private JPanel createInputPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(new EmptyBorder(10, 0, 0, 0));

        // Progress panel (shown during generation)
        JPanel progressPanel = createProgressPanel();
        panel.add(progressPanel, BorderLayout.NORTH);

        // Text input
        inputArea = new JTextArea(3, 30);
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        inputArea.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color.LIGHT_GRAY),
            new EmptyBorder(8, 8, 8, 8)
        ));

        // Add platform-specific keyboard shortcuts (Cmd on macOS, Ctrl on Windows/Linux)
        setupTextAreaKeyBindings(inputArea);

        // Add right-click context menu for text operations
        JPopupMenu contextMenu = createTextContextMenu(inputArea);
        inputArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                showPopupIfTriggered(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                showPopupIfTriggered(e);
            }

            private void showPopupIfTriggered(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    inputArea.requestFocusInWindow();
                    contextMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });

        // Add key binding for Enter to send (Shift+Enter for new line)
        inputArea.getInputMap().put(
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
            "send"
        );
        inputArea.getInputMap().put(
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_DOWN_MASK),
            "send"
        );
        inputArea.getInputMap().put(
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.SHIFT_DOWN_MASK),
            "insert-break"
        );
        inputArea.getActionMap().put("send", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendMessage();
            }
        });

        JScrollPane inputScrollPane = new JScrollPane(inputArea);
        inputScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        panel.add(inputScrollPane, BorderLayout.CENTER);

        // Buttons panel
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));

        abortButton = new JButton("Stop");
        abortButton.setEnabled(false);
        abortButton.addActionListener(e -> abortGeneration());
        buttonsPanel.add(abortButton);

        showXmlButton = new JButton("Show XML");
        showXmlButton.setEnabled(false);
        showXmlButton.setToolTipText("Toggle visibility of generated XML code");
        showXmlButton.addActionListener(e -> toggleXmlVisibility());
        buttonsPanel.add(showXmlButton);

        loadXmlButton = new JButton("Load to Test Plan");
        loadXmlButton.setEnabled(false);
        loadXmlButton.setToolTipText("Load the generated XML into the test plan");
        loadXmlButton.addActionListener(e -> loadGeneratedXml());
        buttonsPanel.add(loadXmlButton);

        sendButton = new JButton("Send");
        sendButton.addActionListener(e -> sendMessage());
        buttonsPanel.add(sendButton);

        panel.add(buttonsPanel, BorderLayout.SOUTH);

        // Placeholder text
        inputArea.setText("");
        inputArea.setForeground(Color.GRAY);
        inputArea.setText("Describe the test plan you want to create...");
        inputArea.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                if (inputArea.getText().equals("Describe the test plan you want to create...")) {
                    inputArea.setText("");
                    inputArea.setForeground(Color.BLACK);
                }
            }

            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                if (inputArea.getText().isEmpty()) {
                    inputArea.setForeground(Color.GRAY);
                    inputArea.setText("Describe the test plan you want to create...");
                }
            }
        });

        return panel;
    }

    private JPanel createProgressPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 2));
        panel.setBorder(new EmptyBorder(0, 0, 5, 0));

        progressLabel = new JLabel(" ");
        progressLabel.setFont(progressLabel.getFont().deriveFont(Font.ITALIC, 11f));
        progressLabel.setForeground(new Color(70, 130, 180));
        panel.add(progressLabel, BorderLayout.NORTH);

        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);
        progressBar.setPreferredSize(new Dimension(100, 8));
        panel.add(progressBar, BorderLayout.CENTER);

        return panel;
    }

    private static JPopupMenu createTextContextMenu(JTextArea textArea) {
        JPopupMenu menu = new JPopupMenu();

        // Use platform-appropriate shortcut key (Cmd on macOS, Ctrl on Windows/Linux)
        int shortcutMask = java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

        // Cut action
        JMenuItem cutItem = new JMenuItem(new DefaultEditorKit.CutAction());
        cutItem.setText("Cut");
        cutItem.setMnemonic(KeyEvent.VK_T);
        cutItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X, shortcutMask));
        menu.add(cutItem);

        // Copy action
        JMenuItem copyItem = new JMenuItem(new DefaultEditorKit.CopyAction());
        copyItem.setText("Copy");
        copyItem.setMnemonic(KeyEvent.VK_C);
        copyItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, shortcutMask));
        menu.add(copyItem);

        // Paste action
        JMenuItem pasteItem = new JMenuItem(new DefaultEditorKit.PasteAction());
        pasteItem.setText("Paste");
        pasteItem.setMnemonic(KeyEvent.VK_P);
        pasteItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, shortcutMask));
        menu.add(pasteItem);

        menu.addSeparator();

        // Select All action
        JMenuItem selectAllItem = new JMenuItem("Select All");
        selectAllItem.setMnemonic(KeyEvent.VK_A);
        selectAllItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, shortcutMask));
        selectAllItem.addActionListener(e -> textArea.selectAll());
        menu.add(selectAllItem);

        menu.addSeparator();

        // Clear action
        JMenuItem clearItem = new JMenuItem("Clear");
        clearItem.addActionListener(e -> {
            textArea.setText("");
            textArea.setForeground(Color.BLACK);
        });
        menu.add(clearItem);

        return menu;
    }

    private static void setupTextAreaKeyBindings(JTextArea textArea) {
        // Get platform-appropriate shortcut key (Cmd on macOS, Ctrl on Windows/Linux)
        int shortcutMask = java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

        // Add key bindings for standard text operations using platform shortcut
        textArea.getInputMap().put(
            KeyStroke.getKeyStroke(KeyEvent.VK_A, shortcutMask), "select-all");
        textArea.getInputMap().put(
            KeyStroke.getKeyStroke(KeyEvent.VK_C, shortcutMask), "copy-to-clipboard");
        textArea.getInputMap().put(
            KeyStroke.getKeyStroke(KeyEvent.VK_X, shortcutMask), "cut-to-clipboard");
        textArea.getInputMap().put(
            KeyStroke.getKeyStroke(KeyEvent.VK_V, shortcutMask), "paste-from-clipboard");
    }

    private void setupEventHandlers() {
        // Set up streaming handler
        chatService.setStreamingHandler(chunk -> {
            SwingUtilities.invokeLater(() -> {
                streamingContent.append(chunk);
                updateStreamingDisplay();
            });
        });

        // Set up complete message handler
        chatService.setMessageHandler(message -> {
            SwingUtilities.invokeLater(() -> {
                streamingContent.setLength(0);
                isProcessing.set(false);
                updateUIState();
                refreshMessages();

                String content = message.getContent();
                // Check if the response contains XML inline
                if (xmlParser.containsJMeterXml(content)) {
                    lastGeneratedXml = content;
                    lastJmxFilePath = null;
                    loadXmlButton.setEnabled(true);
                    showXmlButton.setEnabled(true);
                } else {
                    // Check if the response references a .jmx file
                    xmlParser.extractJmxFilePath(content).ifPresent(path -> {
                        lastJmxFilePath = path;
                        lastGeneratedXml = null;
                        loadXmlButton.setEnabled(true);
                        showXmlButton.setEnabled(false);
                    });
                }
            });
        });
    }

    /**
     * Connects to the Copilot service.
     */
    public void connect() {
        updateConnectionStatus(false, "Connecting...");

        chatService.connect()
            .thenRun(() -> SwingUtilities.invokeLater(() -> {
                updateConnectionStatus(true);
                addSystemMessage("Connected to GitHub Copilot. Describe the test plan you want to create.");
            }))
            .exceptionally(ex -> {
                SwingUtilities.invokeLater(() -> {
                    updateConnectionStatus(false, "Connection failed");
                    addSystemMessage("Failed to connect: " + ex.getMessage());
                });
                return null;
            });
    }

    /**
     * Disconnects from the Copilot service.
     */
    public void disconnect() {
        chatService.close();
        updateConnectionStatus(false);
    }

    private void sendMessage() {
        String text = inputArea.getText().trim();
        if (text.isEmpty() || text.equals("Describe the test plan you want to create...")) {
            return;
        }

        if (isProcessing.get()) {
            return;
        }

        if (!chatService.isConnected()) {
            addSystemMessage("Not connected to Copilot. Connecting...");
            connect();
            return;
        }

        isProcessing.set(true);
        streamingContent.setLength(0);
        lastGeneratedXml = null;
        lastJmxFilePath = null;
        loadXmlButton.setEnabled(false);
        showXmlButton.setEnabled(false);
        showXmlButton.setText("Show XML");
        messageRenderer.setShowXmlExpanded(false);
        updateUIState();

        // Clear input
        inputArea.setText("");
        inputArea.setForeground(Color.BLACK);

        // Send message
        chatService.sendMessage(text)
            .exceptionally(ex -> {
                SwingUtilities.invokeLater(() -> {
                    isProcessing.set(false);
                    updateUIState();
                    addSystemMessage("Error: " + ex.getMessage());
                });
                return null;
            });

        // Refresh to show user message immediately
        refreshMessages();
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    private void abortGeneration() {
        chatService.abort()
            .thenRun(() -> SwingUtilities.invokeLater(() -> {
                isProcessing.set(false);
                streamingContent.setLength(0);
                updateUIState();
                addSystemMessage("Generation aborted.");
            }));
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    private void clearConversation() {
        // Clear UI state immediately for responsiveness
        lastGeneratedXml = null;
        lastJmxFilePath = null;
        loadXmlButton.setEnabled(false);
        showXmlButton.setEnabled(false);
        showXmlButton.setText("Show XML");
        messageRenderer.setShowXmlExpanded(false);
        streamingContent.setLength(0);

        // Clear the conversation in the service
        chatService.clearConversation()
            .whenComplete((result, ex) -> SwingUtilities.invokeLater(() -> {
                refreshMessages();
                if (ex != null) {
                    addSystemMessage("Conversation cleared. (Note: Not connected to Copilot)");
                } else {
                    addSystemMessage("Conversation cleared. Start a new conversation.");
                }
            }));
    }

    private void toggleXmlVisibility() {
        boolean expanded = messageRenderer.toggleXmlExpanded();
        showXmlButton.setText(expanded ? "Hide XML" : "Show XML");
        refreshMessages();
    }

    private void loadGeneratedXml() {
        if (lastGeneratedXml == null && lastJmxFilePath == null) {
            JOptionPane.showMessageDialog(this,
                "No XML available to load.",
                "Load Error",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Set property to avoid "file exists" warning for result collectors
        // This prevents the popup when running Copilot-generated test plans
        if (JMeterUtils.getProperty("resultcollector.action_if_file_exists") == null) {
            JMeterUtils.setProperty("resultcollector.action_if_file_exists", "APPEND");
        }

        // Show progress while loading
        progressLabel.setText("Loading test plan into JMeter...");
        progressBar.setVisible(true);

        // Parse and load in background to keep UI responsive
        SwingWorker<JMeterXmlParser.ParseResult, Void> worker = new SwingWorker<>() {
            @Override
            protected JMeterXmlParser.ParseResult doInBackground() {
                if (lastJmxFilePath != null) {
                    return xmlParser.parseXmlFile(lastJmxFilePath);
                }
                return xmlParser.parseXml(lastGeneratedXml);
            }

            @Override
            protected void done() {
                handleLoadResult();
            }

            private void handleLoadResult() {
                try {
                    JMeterXmlParser.ParseResult result = get();
                    hideProgress();
                    processParseResult(result);
                } catch (Exception e) {
                    hideProgress();
                    LOG.log(Level.WARNING, "Error loading test plan", e);
                    showError("Error loading test plan: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void hideProgress() {
        progressBar.setVisible(false);
        progressLabel.setText(" ");
    }

    private void processParseResult(JMeterXmlParser.ParseResult result) {
        if (result.isSuccess()) {
            loadTestPlanTree(result.tree());
        } else {
            showParseError(result.errorMessage());
        }
    }

    private void loadTestPlanTree(HashTree tree) {
        if (onLoadTestPlan != null) {
            onLoadTestPlan.accept(tree);
            addSystemMessage("✓ Test plan loaded successfully!");
            return;
        }
        // Try to load via GuiPackage if available
        try {
            GuiPackage guiPackage = GuiPackage.getInstance();
            if (guiPackage != null) {
                guiPackage.getTreeModel().clearTestPlan();
                guiPackage.addSubTree(tree);
                addSystemMessage("✓ Test plan loaded successfully! You can now view and edit it in the tree.");
            } else {
                showError("Could not access JMeter GUI. Please save the XML and load manually.");
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to load test plan", e);
            showError("Failed to load test plan: " + e.getMessage());
        }
    }

    private void showParseError(String errorMessage) {
        JOptionPane.showMessageDialog(this,
            "Failed to parse XML: " + errorMessage,
            "Parse Error",
            JOptionPane.ERROR_MESSAGE);
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this,
            message,
            "Load Error",
            JOptionPane.ERROR_MESSAGE);
    }

    private void addSystemMessage(String message) {
        ChatMessage systemMessage = new ChatMessage(ChatMessage.Role.SYSTEM, message);
        chatService.getConversationHistory().addMessage(systemMessage);
        refreshMessages();
    }

    private void refreshMessages() {
        String html = messageRenderer.renderMessages(
            chatService.getConversationHistory().getMessages()
        );
        messagesPane.setText(html);
        scrollToBottom();
    }

    private void updateStreamingDisplay() {
        if (streamingContent.length() > 0) {
            String existingHtml = messageRenderer.renderMessages(
                chatService.getConversationHistory().getMessages()
            );
            // Remove closing tags and append streaming content
            String streamingHtml = messageRenderer.renderStreamingMessage(streamingContent.toString());

            // Combine existing messages with streaming
            String combined = existingHtml.replace("</body></html>", "") +
                              streamingHtml.replace("<html><body>", "");
            messagesPane.setText(combined);
            scrollToBottom();
        }
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar vertical = messagesScrollPane.getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        });
    }

    private void updateUIState() {
        boolean processing = isProcessing.get();
        sendButton.setEnabled(!processing);
        abortButton.setEnabled(processing);
        inputArea.setEnabled(!processing);
        clearButton.setEnabled(!processing);

        // Update progress bar and label visibility
        progressBar.setVisible(processing);
        if (processing) {
            progressLabel.setText("Copilot is generating your test plan...");
        } else {
            progressLabel.setText(" ");
        }
    }

    private void updateConnectionStatus(boolean connected) {
        updateConnectionStatus(connected, connected ? "Connected" : "Disconnected");
    }

    private void updateConnectionStatus(boolean connected, String message) {
        statusLabel.setText(message);
        statusLabel.setForeground(connected ? new Color(0, 128, 0) : Color.GRAY);
        sendButton.setEnabled(connected);
    }

    /**
     * Sets a callback to be invoked when a test plan should be loaded.
     */
    public void setOnLoadTestPlan(Consumer<HashTree> callback) {
        this.onLoadTestPlan = callback;
    }

    /**
     * Returns whether the panel is connected to Copilot.
     */
    public boolean isConnected() {
        return chatService.isConnected();
    }

    /**
     * Returns whether a request is currently being processed.
     */
    public boolean isProcessing() {
        return isProcessing.get();
    }

    /**
     * Gets the chat service for testing purposes.
     */
    CopilotChatService getChatService() {
        return chatService;
    }

    /**
     * Gets the input area for testing purposes.
     */
    JTextArea getInputArea() {
        return inputArea;
    }

    /**
     * Gets the send button for testing purposes.
     */
    JButton getSendButton() {
        return sendButton;
    }

    /**
     * Gets the load XML button for testing purposes.
     */
    JButton getLoadXmlButton() {
        return loadXmlButton;
    }
}
