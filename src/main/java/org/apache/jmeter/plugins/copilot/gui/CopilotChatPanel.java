/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jmeter.plugins.copilot.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import org.apache.jmeter.plugins.copilot.service.CopilotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chat panel that provides the UI for interacting with GitHub Copilot.
 * Users can type their test requirements and Copilot will generate
 * the appropriate JMeter test elements.
 */
public class CopilotChatPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(CopilotChatPanel.class);

    private static final String[] AVAILABLE_MODELS = {
        "claude-sonnet-4.5",
        "gpt-4.1",
        "claude-4-opus",
        "gpt-4.1-mini"
    };

    private JTextArea chatHistoryArea;
    private JTextField inputField;
    private JButton sendButton;
    private JButton clearButton;
    private JComboBox<String> modelSelector;
    private JLabel statusLabel;

    private CopilotService copilotService;
    private Consumer<String> onTestPlanGenerated;
    private volatile boolean isProcessing = false;

    /**
     * Creates a new CopilotChatPanel.
     *
     * @param onTestPlanGenerated Callback invoked when a test plan is generated
     */
    public CopilotChatPanel(Consumer<String> onTestPlanGenerated) {
        this.onTestPlanGenerated = onTestPlanGenerated;
        this.copilotService = new CopilotService();
        initUI();
    }

    /**
     * Initialize the UI components.
     */
    private void initUI() {
        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(10, 10, 10, 10));

        // Header panel with model selector and status
        add(createHeaderPanel(), BorderLayout.NORTH);

        // Chat history area
        add(createChatHistoryPanel(), BorderLayout.CENTER);

        // Input panel
        add(createInputPanel(), BorderLayout.SOUTH);

        // Add welcome message
        displayWelcomeMessage();
    }

    /**
     * Create the header panel with model selector and status.
     */
    private JPanel createHeaderPanel() {
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBorder(new EmptyBorder(0, 0, 10, 0));

        // Model selector
        JPanel modelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        modelPanel.add(new JLabel("AI Model: "));
        modelSelector = new JComboBox<>(AVAILABLE_MODELS);
        modelSelector.setSelectedItem("claude-sonnet-4.5");
        modelPanel.add(modelSelector);

        // Status label
        statusLabel = new JLabel("Ready");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.ITALIC));
        statusLabel.setForeground(Color.GRAY);

        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        statusPanel.add(statusLabel);

        headerPanel.add(modelPanel, BorderLayout.WEST);
        headerPanel.add(statusPanel, BorderLayout.EAST);

        return headerPanel;
    }

    /**
     * Create the chat history panel.
     */
    private JPanel createChatHistoryPanel() {
        JPanel chatPanel = new JPanel(new BorderLayout());
        chatPanel.setBorder(BorderFactory.createTitledBorder("Copilot Chat"));

        chatHistoryArea = new JTextArea();
        chatHistoryArea.setEditable(false);
        chatHistoryArea.setLineWrap(true);
        chatHistoryArea.setWrapStyleWord(true);
        chatHistoryArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        chatHistoryArea.setBackground(new Color(250, 250, 250));

        JScrollPane scrollPane = new JScrollPane(chatHistoryArea);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        chatPanel.add(scrollPane, BorderLayout.CENTER);
        return chatPanel;
    }

    /**
     * Create the input panel with text field and buttons.
     */
    private JPanel createInputPanel() {
        JPanel inputPanel = new JPanel(new BorderLayout(10, 0));
        inputPanel.setBorder(new EmptyBorder(10, 0, 0, 0));

        // Input field
        inputField = new JTextField();
        inputField.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        inputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && !isProcessing) {
                    sendMessage();
                }
            }
        });

        // Buttons panel
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));

        sendButton = new JButton("Send");
        sendButton.addActionListener(this::onSendClicked);

        clearButton = new JButton("Clear");
        clearButton.addActionListener(e -> clearChat());

        buttonPanel.add(sendButton);
        buttonPanel.add(Box.createRigidArea(new Dimension(5, 0)));
        buttonPanel.add(clearButton);

        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(buttonPanel, BorderLayout.EAST);

        // Prompt hint
        JLabel hintLabel = new JLabel(
            "<html><i>Describe the test you want to create. Example: " +
            "\"Create an HTTP request to test the login endpoint at /api/login with POST method\"</i></html>"
        );
        hintLabel.setFont(hintLabel.getFont().deriveFont(11f));
        hintLabel.setForeground(Color.GRAY);

        JPanel wrapperPanel = new JPanel(new BorderLayout());
        wrapperPanel.add(inputPanel, BorderLayout.CENTER);
        wrapperPanel.add(hintLabel, BorderLayout.SOUTH);

        return wrapperPanel;
    }

    /**
     * Display the welcome message in the chat.
     */
    private void displayWelcomeMessage() {
        appendToChat("Copilot", """
            Welcome to JMeter Copilot! 🚀
            
            I can help you create JMeter test plans through natural language.
            Just describe what you want to test, and I'll generate the appropriate
            JMeter elements for you.
            
            Examples of what you can ask:
            • "Create an HTTP GET request to https://api.example.com/users"
            • "Add a Thread Group with 10 users and 5 iterations"
            • "Create a load test for a REST API with authentication"
            • "Add response assertions to verify status code 200"
            
            What would you like to test today?
            """);
    }

    /**
     * Handle send button click.
     */
    private void onSendClicked(ActionEvent e) {
        if (!isProcessing) {
            sendMessage();
        }
    }

    /**
     * Send the message to Copilot and handle the response.
     */
    private void sendMessage() {
        String message = inputField.getText().trim();
        if (message.isEmpty()) {
            return;
        }

        // Display user message
        appendToChat("You", message);
        inputField.setText("");

        // Disable input while processing
        setProcessing(true);
        updateStatus("Connecting to Copilot...");

        // Set up progress callback
        copilotService.setProgressCallback(status -> {
            SwingUtilities.invokeLater(() -> updateStatus(status));
        });

        // Send to Copilot asynchronously
        copilotService.sendMessageAsync(message, getSelectedModel())
            .thenAccept(response -> {
                SwingUtilities.invokeLater(() -> {
                    appendToChat("Copilot", response.getMessage());
                    
                    // If a test plan was generated, save to file and notify user
                    if (response.hasGeneratedTestPlan()) {
                        try {
                            File savedFile = saveTestPlanToFile(response.getTestPlanXml());
                            appendToChat("System", 
                                "📄 Test plan XML saved to:\n" + savedFile.getAbsolutePath() + 
                                "\n\nYou can open this file in JMeter using File > Open.");
                            
                            // Try to integrate into current test plan
                            onTestPlanGenerated.accept(response.getTestPlanXml());
                            appendToChat("System", "✅ Test elements have been added to your test plan!");
                        } catch (Exception e) {
                            log.error("Failed to save or integrate test plan", e);
                            appendToChat("Error", "Failed to integrate test plan: " + e.getMessage() + 
                                "\nYou can still open the saved file manually.");
                        }
                    }
                    
                    setProcessing(false);
                });
            })
            .exceptionally(ex -> {
                SwingUtilities.invokeLater(() -> {
                    log.error("Error from Copilot", ex);
                    displayError(ex.getMessage());
                    setProcessing(false);
                });
                return null;
            });
    }

    /**
     * Save the generated test plan XML to a file in the temp directory.
     */
    private File saveTestPlanToFile(String xml) throws Exception {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = "jmeter_copilot_" + timestamp + ".jmx";
        
        File tempDir = new File(System.getProperty("java.io.tmpdir"), "jmeter-copilot");
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }
        
        File outputFile = new File(tempDir, filename);
        Files.writeString(outputFile.toPath(), xml, StandardCharsets.UTF_8);
        
        System.out.println("[JMeter Copilot] Saved test plan to: " + outputFile.getAbsolutePath());
        return outputFile;
    }

    /**
     * Append a message to the chat history.
     */
    private void appendToChat(String sender, String message) {
        String timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String formattedMessage = String.format("[%s] %s:\n%s\n\n", timestamp, sender, message);
        chatHistoryArea.append(formattedMessage);
        
        // Scroll to bottom
        chatHistoryArea.setCaretPosition(chatHistoryArea.getDocument().getLength());
    }

    /**
     * Display an error message in the chat.
     */
    public void displayError(String error) {
        appendToChat("Error", "⚠️ " + error);
    }

    /**
     * Set the processing state.
     */
    private void setProcessing(boolean processing) {
        this.isProcessing = processing;
        inputField.setEnabled(!processing);
        sendButton.setEnabled(!processing);
        modelSelector.setEnabled(!processing);
        if (!processing) {
            statusLabel.setText("Ready");
            statusLabel.setForeground(Color.GRAY);
        }
    }

    /**
     * Update the status label with a message.
     */
    private void updateStatus(String status) {
        statusLabel.setText(status);
        statusLabel.setForeground(Color.BLUE);
        log.info("Status: {}", status);
    }

    /**
     * Clear the chat history.
     */
    public void clearChat() {
        chatHistoryArea.setText("");
        displayWelcomeMessage();
        copilotService.resetSession();
    }

    /**
     * Get the selected AI model.
     */
    public String getSelectedModel() {
        return (String) modelSelector.getSelectedItem();
    }

    /**
     * Set the selected AI model.
     */
    public void setSelectedModel(String model) {
        modelSelector.setSelectedItem(model);
    }

    /**
     * Shutdown and cleanup resources.
     */
    public void shutdown() {
        if (copilotService != null) {
            copilotService.shutdown();
        }
    }
}
