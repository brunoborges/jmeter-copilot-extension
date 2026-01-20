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
package org.apache.jmeter.plugins.copilot;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import javax.swing.SwingUtilities;

import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.save.SaveService;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.testelement.TestPlan;
import org.apache.jmeter.threads.ThreadGroup;
import org.apache.jorphan.collections.HashTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class that integrates generated JMeter test elements into the
 * current test plan in JMeter's GUI.
 */
public class JMeterTestPlanIntegrator {

    private static final Logger log = LoggerFactory.getLogger(JMeterTestPlanIntegrator.class);

    /**
     * Integrate a generated test plan XML fragment into the current JMeter test plan.
     *
     * @param testPlanXml The XML fragment containing JMeter elements
     * @throws Exception If integration fails
     */
    public static void integrateTestPlan(String testPlanXml) throws Exception {
        if (testPlanXml == null || testPlanXml.trim().isEmpty()) {
            throw new IllegalArgumentException("Test plan XML is empty");
        }

        log.info("Integrating generated test plan into JMeter GUI");
        System.out.println("[JMeter Copilot] Integrating test plan (" + testPlanXml.length() + " chars)");

        // The XML should already be a complete test plan
        String fullXml = testPlanXml.trim();
        
        // If it's not a complete test plan, wrap it (fallback)
        if (!fullXml.startsWith("<?xml") && !fullXml.startsWith("<jmeterTestPlan")) {
            System.out.println("[JMeter Copilot] Warning: Received fragment instead of complete test plan, wrapping...");
            fullXml = wrapInTestPlanIfNeeded(testPlanXml);
        }
        
        System.out.println("[JMeter Copilot] Final XML to load:\\n" + fullXml);

        // Parse the XML by writing to a temporary file
        HashTree testTree;
        File tempFile = null;
        try {
            tempFile = File.createTempFile("jmeter_copilot_", ".jmx");
            Files.writeString(tempFile.toPath(), fullXml, StandardCharsets.UTF_8);
            System.out.println("[JMeter Copilot] Saved to temp file: " + tempFile.getAbsolutePath());
            testTree = SaveService.loadTree(tempFile);
            System.out.println("[JMeter Copilot] Successfully parsed test plan");
        } catch (Exception e) {
            System.err.println("[JMeter Copilot] Failed to parse test plan: " + e.getMessage());
            throw e;
        } finally {
            if (tempFile != null && tempFile.exists()) {
                // Keep file for debugging for now
                System.out.println("[JMeter Copilot] Temp file retained for debugging: " + tempFile.getAbsolutePath());
                // tempFile.delete();
            }
        }

        if (testTree == null || testTree.isEmpty()) {
            throw new IllegalStateException("Failed to parse test plan XML - tree is empty");
        }

        // Add elements to the current test plan on the EDT
        SwingUtilities.invokeLater(() -> {
            try {
                addElementsToTestPlan(testTree);
                System.out.println("[JMeter Copilot] Successfully added elements to test plan");
            } catch (Exception e) {
                System.err.println("[JMeter Copilot] Failed to add elements to test plan: " + e.getMessage());
                log.error("Failed to add elements to test plan", e);
            }
        });
    }

    /**
     * Wrap an XML fragment in a full JMeter test plan structure if it's not already.
     */
    private static String wrapInTestPlanIfNeeded(String xml) {
        String trimmed = xml.trim();
        
        // If it's already a full test plan, return as-is
        if (trimmed.startsWith("<?xml") || trimmed.startsWith("<jmeterTestPlan")) {
            return xml;
        }

        // Process XML to add <hashTree/> after each element if missing
        String processedXml = addHashTreesToElements(trimmed);

        // Wrap the fragment in a minimal test plan structure
        StringBuilder wrapped = new StringBuilder();
        wrapped.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        wrapped.append("<jmeterTestPlan version=\"1.2\" properties=\"5.0\" jmeter=\"5.6.3\">\n");
        wrapped.append("  <hashTree>\n");
        wrapped.append("    <TestPlan guiclass=\"TestPlanGui\" testclass=\"TestPlan\" testname=\"Generated Test Plan\" enabled=\"true\">\n");
        wrapped.append("      <stringProp name=\"TestPlan.comments\">Generated by Copilot</stringProp>\n");
        wrapped.append("      <boolProp name=\"TestPlan.functional_mode\">false</boolProp>\n");
        wrapped.append("      <boolProp name=\"TestPlan.serialize_threadgroups\">false</boolProp>\n");
        wrapped.append("      <elementProp name=\"TestPlan.user_defined_variables\" elementType=\"Arguments\" guiclass=\"ArgumentsPanel\" testclass=\"Arguments\" testname=\"User Defined Variables\" enabled=\"true\">\n");
        wrapped.append("        <collectionProp name=\"Arguments.arguments\"/>\n");
        wrapped.append("      </elementProp>\n");
        wrapped.append("      <stringProp name=\"TestPlan.user_define_classpath\"></stringProp>\n");
        wrapped.append("    </TestPlan>\n");
        wrapped.append("    <hashTree>\n");
        wrapped.append("      ").append(processedXml).append("\n");
        wrapped.append("    </hashTree>\n");
        wrapped.append("  </hashTree>\n");
        wrapped.append("</jmeterTestPlan>\n");

        String result = wrapped.toString();
        log.debug("Wrapped XML:\n{}", result);
        System.out.println("[JMeter Copilot] Wrapped XML for JMeter:\n" + result);
        return result;
    }

    /**
     * Add <hashTree/> elements after each JMeter element if missing.
     * JMeter's XML format requires every element to be followed by a hashTree.
     */
    private static String addHashTreesToElements(String xml) {
        // Common JMeter element tags that need hashTree after them
        String[] jmeterElements = {
            "HTTPSamplerProxy", "ThreadGroup", "ResponseAssertion", "ConstantTimer",
            "UniformRandomTimer", "HeaderManager", "CookieManager", "LoopController",
            "IfController", "TransactionController", "ResultCollector", "CSVDataSet",
            "Arguments", "ConfigTestElement", "BeanShellSampler", "JSR223Sampler",
            "DebugSampler", "TestAction", "WhileController", "ForeachController",
            "OnceOnlyController", "InterleaveController", "RandomController",
            "ThroughputController", "ModuleController", "IncludeController",
            "RandomOrderController", "SwitchController", "RegexExtractor",
            "JSONPathExtractor", "XPathExtractor", "BoundaryExtractor",
            "JSONPathAssertion", "XPathAssertion", "DurationAssertion",
            "SizeAssertion", "HTMLAssertion", "AuthManager", "DNSCacheManager",
            "HTTPCacheManager", "ConstantThroughputTimer", "SynchronizingTimer",
            "PreciseThroughputTimer", "PoissonRandomTimer", "GaussianRandomTimer"
        };

        String result = xml;
        
        for (String element : jmeterElements) {
            // Pattern: </ElementName> not followed by whitespace and <hashTree
            // We need to add <hashTree/> or <hashTree></hashTree> after closing tags
            String closingTag = "</" + element + ">";
            
            if (result.contains(closingTag)) {
                // Check each occurrence of the closing tag
                StringBuilder processed = new StringBuilder();
                int lastEnd = 0;
                int index = result.indexOf(closingTag);
                
                while (index != -1) {
                    int afterClosingTag = index + closingTag.length();
                    processed.append(result, lastEnd, afterClosingTag);
                    
                    // Look ahead to see if there's already a hashTree
                    String remaining = result.substring(afterClosingTag);
                    String trimmedRemaining = remaining.stripLeading();
                    
                    if (!trimmedRemaining.startsWith("<hashTree")) {
                        // Need to add hashTree
                        processed.append("\n<hashTree/>");
                    }
                    
                    lastEnd = afterClosingTag;
                    index = result.indexOf(closingTag, afterClosingTag);
                }
                
                processed.append(result.substring(lastEnd));
                result = processed.toString();
            }
        }
        
        return result;
    }

    /**
     * Add parsed elements to the current test plan.
     */
    private static void addElementsToTestPlan(HashTree testTree) throws Exception {
        GuiPackage guiPackage = GuiPackage.getInstance();
        if (guiPackage == null) {
            log.warn("GuiPackage not available - running in non-GUI mode?");
            System.out.println("[JMeter Copilot] GuiPackage not available");
            return;
        }

        JMeterTreeModel treeModel = guiPackage.getTreeModel();
        JMeterTreeNode rootNode = (JMeterTreeNode) treeModel.getRoot();
        
        System.out.println("[JMeter Copilot] Current test plan root: " + rootNode.getName());
        System.out.println("[JMeter Copilot] Parsed tree has " + testTree.size() + " top-level elements");

        // Find the Test Plan node in the current JMeter GUI
        JMeterTreeNode testPlanNode = findTestPlanNode(rootNode);
        if (testPlanNode == null) {
            System.out.println("[JMeter Copilot] No TestPlan node found, using root");
            testPlanNode = rootNode;
        } else {
            System.out.println("[JMeter Copilot] Found TestPlan node: " + testPlanNode.getName());
        }

        // Extract elements from the parsed tree and add them
        addElementsFromTree(testTree, treeModel, testPlanNode);

        // Refresh the GUI
        guiPackage.getMainFrame().repaint();
        guiPackage.updateCurrentGui();
    }

    /**
     * Find the TestPlan node in the tree.
     */
    private static JMeterTreeNode findTestPlanNode(JMeterTreeNode node) {
        if (node.getTestElement() instanceof TestPlan) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            JMeterTreeNode child = (JMeterTreeNode) node.getChildAt(i);
            JMeterTreeNode found = findTestPlanNode(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * Find the best parent node for adding new elements.
     */
    private static JMeterTreeNode findBestParentNode(JMeterTreeNode currentNode) {
        if (currentNode == null) {
            return null;
        }

        TestElement element = currentNode.getTestElement();
        
        // If current node is a Thread Group or Controller, use it as parent
        if (element instanceof ThreadGroup || 
            element instanceof org.apache.jmeter.control.Controller) {
            return currentNode;
        }

        // Otherwise, try to find a Thread Group ancestor
        JMeterTreeNode parent = (JMeterTreeNode) currentNode.getParent();
        while (parent != null) {
            if (parent.getTestElement() instanceof ThreadGroup) {
                return parent;
            }
            parent = (JMeterTreeNode) parent.getParent();
        }

        // Fall back to current node
        return currentNode;
    }

    /**
     * Recursively add elements from a HashTree to the JMeter tree model.
     */
    private static void addElementsFromTree(HashTree tree, JMeterTreeModel model, JMeterTreeNode parent) throws Exception {
        for (Object key : tree.keySet()) {
            if (key instanceof TestElement testElement) {
                String elementClass = testElement.getClass().getSimpleName();
                System.out.println("[JMeter Copilot] Processing element: " + elementClass + " - " + testElement.getName());
                
                // Skip the TestPlan element itself, we only want to add its children
                if (testElement instanceof TestPlan) {
                    System.out.println("[JMeter Copilot] Skipping TestPlan, processing children...");
                    HashTree subTree = tree.getTree(key);
                    if (subTree != null) {
                        addElementsFromTree(subTree, model, parent);
                    }
                } else {
                    // Add the element to the tree
                    try {
                        JMeterTreeNode newNode = model.addComponent(testElement, parent);
                        System.out.println("[JMeter Copilot] Added element: " + testElement.getName() + " under " + parent.getName());
                        log.info("Added element: {} under {}", testElement.getName(), parent.getName());
                        
                        // Recursively add children
                        HashTree subTree = tree.getTree(key);
                        if (subTree != null && !subTree.isEmpty()) {
                            addElementsFromTree(subTree, model, newNode);
                        }
                    } catch (Exception e) {
                        System.err.println("[JMeter Copilot] Failed to add element " + testElement.getName() + ": " + e.getMessage());
                        log.error("Failed to add element: {}", testElement.getName(), e);
                    }
                }
            } else {
                System.out.println("[JMeter Copilot] Skipping non-TestElement key: " + (key != null ? key.getClass().getName() : "null"));
            }
        }
    }
}
