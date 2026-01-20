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
package org.apache.jmeter.plugins.copilot.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Integration tests for CopilotService.
 * These tests make REAL calls to GitHub Copilot API.
 * 
 * Prerequisites:
 * - Valid GitHub Copilot subscription
 * - Authenticated via GitHub CLI (gh auth login) or VS Code
 */
class CopilotServiceIntegrationTest {

    private static final String DEFAULT_MODEL = "claude-sonnet-4";
    private static final int TIMEOUT_SECONDS = 60;

    private CopilotService copilotService;

    @BeforeEach
    void setUp() {
        copilotService = new CopilotService();
    }

    @AfterEach
    void tearDown() {
        if (copilotService != null) {
            copilotService.shutdown();
        }
    }

    @Test
    @DisplayName("Should get a response from Copilot for a simple greeting")
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void testSimpleGreeting() throws Exception {
        // Given
        String message = "Hello! Please respond with just 'Hi there!' and nothing else.";

        // When
        CopilotService.CopilotResponse response = copilotService
            .sendMessageAsync(message, DEFAULT_MODEL)
            .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Then
        assertNotNull(response, "Response should not be null");
        assertNotNull(response.getMessage(), "Message should not be null");
        assertFalse(response.getMessage().isEmpty(), "Message should not be empty");
        System.out.println("[Test] Greeting response: " + response.getMessage());
    }

    @Test
    @DisplayName("Should generate HTTP GET request XML for jsonplaceholder API")
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void testGenerateHttpGetRequest() throws Exception {
        // Given
        String message = "Create an HTTP GET request to https://jsonplaceholder.typicode.com/todos/1";

        // When
        CopilotService.CopilotResponse response = copilotService
            .sendMessageAsync(message, DEFAULT_MODEL)
            .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Then
        assertNotNull(response, "Response should not be null");
        assertNotNull(response.getMessage(), "Message should not be null");
        
        // Copilot should have called the tool to generate the element
        assertTrue(response.hasGeneratedTestPlan(), 
            "Copilot should have generated a test plan element");
        
        String xml = response.getTestPlanXml();
        assertNotNull(xml, "Generated XML should not be null");
        assertTrue(xml.contains("HTTPSamplerProxy") || xml.contains("HTTPSampler"), 
            "XML should contain HTTP sampler element");
        assertTrue(xml.contains("jsonplaceholder.typicode.com"), 
            "XML should contain the target domain");
        
        System.out.println("[Test] Generated XML:\n" + xml);
        System.out.println("[Test] Copilot message: " + response.getMessage());
    }

    @Test
    @DisplayName("Should generate HTTP POST request with JSON body")
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void testGenerateHttpPostRequest() throws Exception {
        // Given
        String message = """
            Create an HTTP POST request to https://jsonplaceholder.typicode.com/posts 
            with JSON body: {"title": "foo", "body": "bar", "userId": 1}
            Include the Content-Type header set to application/json.
            """;

        // When
        CopilotService.CopilotResponse response = copilotService
            .sendMessageAsync(message, DEFAULT_MODEL)
            .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Then
        assertNotNull(response, "Response should not be null");
        assertTrue(response.hasGeneratedTestPlan(), 
            "Copilot should have generated a test plan element");
        
        String xml = response.getTestPlanXml();
        assertNotNull(xml, "Generated XML should not be null");
        assertTrue(xml.contains("POST"), "XML should contain POST method");
        assertTrue(xml.contains("jsonplaceholder.typicode.com"), 
            "XML should contain the target domain");
        
        System.out.println("[Test] Generated POST XML:\n" + xml);
    }

    @Test
    @DisplayName("Should generate Thread Group with specific configuration")
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void testGenerateThreadGroup() throws Exception {
        // Given
        String message = "Create a Thread Group with 10 users, 5 second ramp-up, and 3 iterations";

        // When
        CopilotService.CopilotResponse response = copilotService
            .sendMessageAsync(message, DEFAULT_MODEL)
            .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Then
        assertNotNull(response, "Response should not be null");
        assertTrue(response.hasGeneratedTestPlan(), 
            "Copilot should have generated a test plan element");
        
        String xml = response.getTestPlanXml();
        assertNotNull(xml, "Generated XML should not be null");
        assertTrue(xml.contains("ThreadGroup"), "XML should contain ThreadGroup element");
        assertTrue(xml.contains("10"), "XML should contain 10 threads");
        
        System.out.println("[Test] Generated ThreadGroup XML:\n" + xml);
    }

    @Test
    @DisplayName("Should generate Response Assertion for status code 200")
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void testGenerateResponseAssertion() throws Exception {
        // Given
        String message = "Add a Response Assertion to verify the response status code is 200";

        // When
        CopilotService.CopilotResponse response = copilotService
            .sendMessageAsync(message, DEFAULT_MODEL)
            .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Then
        assertNotNull(response, "Response should not be null");
        assertTrue(response.hasGeneratedTestPlan(), 
            "Copilot should have generated a test plan element");
        
        String xml = response.getTestPlanXml();
        assertNotNull(xml, "Generated XML should not be null");
        assertTrue(xml.contains("ResponseAssertion") || xml.contains("Assertion"), 
            "XML should contain assertion element");
        assertTrue(xml.contains("200"), "XML should contain status code 200");
        
        System.out.println("[Test] Generated Assertion XML:\n" + xml);
    }

    @Test
    @DisplayName("Should maintain conversation context across messages")
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void testConversationContext() throws Exception {
        // First message - create a request
        String message1 = "Create an HTTP GET request to https://api.github.com/users/octocat";
        CopilotService.CopilotResponse response1 = copilotService
            .sendMessageAsync(message1, DEFAULT_MODEL)
            .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertNotNull(response1, "First response should not be null");
        assertTrue(response1.hasGeneratedTestPlan(), "Should generate first element");
        System.out.println("[Test] First element generated");

        // Second message - add an assertion (should understand context)
        String message2 = "Now add an assertion to verify the response contains 'octocat'";
        CopilotService.CopilotResponse response2 = copilotService
            .sendMessageAsync(message2, DEFAULT_MODEL)
            .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertNotNull(response2, "Second response should not be null");
        assertTrue(response2.hasGeneratedTestPlan(), "Should generate second element");
        
        String xml = response2.getTestPlanXml();
        assertTrue(xml.contains("octocat"), "Assertion should reference octocat");
        
        System.out.println("[Test] Assertion XML:\n" + xml);
    }

    @Test
    @DisplayName("Should handle complex load test scenario")
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void testComplexLoadTestScenario() throws Exception {
        // Given
        String message = """
            Create a complete load test scenario with:
            - A Thread Group with 50 users, 10 second ramp-up, running for 60 seconds
            - Include an HTTP GET request to https://httpbin.org/get
            """;

        // When
        CopilotService.CopilotResponse response = copilotService
            .sendMessageAsync(message, DEFAULT_MODEL)
            .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Then
        assertNotNull(response, "Response should not be null");
        assertNotNull(response.getMessage(), "Message should not be null");
        
        // For complex scenarios, Copilot may call the tool multiple times
        // or provide a combined response
        System.out.println("[Test] Complex scenario response: " + response.getMessage());
        if (response.hasGeneratedTestPlan()) {
            System.out.println("[Test] Generated XML:\n" + response.getTestPlanXml());
        }
    }

    @Test
    @DisplayName("Should reset session and start fresh")
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void testSessionReset() throws Exception {
        // First conversation
        String message1 = "Create an HTTP GET request to https://example.com/first";
        CopilotService.CopilotResponse response1 = copilotService
            .sendMessageAsync(message1, DEFAULT_MODEL)
            .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertNotNull(response1);
        System.out.println("[Test] First session response received");

        // Reset the session
        copilotService.resetSession();
        System.out.println("[Test] Session reset");

        // New conversation - should not have context from previous
        String message2 = "What was my previous request?";
        CopilotService.CopilotResponse response2 = copilotService
            .sendMessageAsync(message2, DEFAULT_MODEL)
            .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        
        assertNotNull(response2);
        // After reset, Copilot shouldn't know about the previous request
        assertFalse(response2.getMessage().contains("example.com/first"),
            "After reset, Copilot should not remember previous context");
        
        System.out.println("[Test] Post-reset response: " + response2.getMessage());
    }

    @Test
    @DisplayName("Should generate valid JMeter XML that can be parsed")
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void testGeneratedXmlIsValid() throws Exception {
        // Given
        String message = "Create an HTTP GET request to https://httpbin.org/json";

        // When
        CopilotService.CopilotResponse response = copilotService
            .sendMessageAsync(message, DEFAULT_MODEL)
            .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Then
        assertTrue(response.hasGeneratedTestPlan(), "Should generate test plan");
        
        String xml = response.getTestPlanXml();
        
        // Try to parse the XML to verify it's valid
        javax.xml.parsers.DocumentBuilderFactory factory = 
            javax.xml.parsers.DocumentBuilderFactory.newInstance();
        javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
        
        // Wrap in a root element if needed for parsing
        String wrappedXml = xml.trim().startsWith("<?xml") ? xml : 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><root>" + xml + "</root>";
        
        org.w3c.dom.Document doc = builder.parse(
            new org.xml.sax.InputSource(new java.io.StringReader(wrappedXml)));
        
        assertNotNull(doc, "XML should be parseable");
        System.out.println("[Test] XML is valid and parseable");
        System.out.println("[Test] Generated XML:\n" + xml);
    }
}
