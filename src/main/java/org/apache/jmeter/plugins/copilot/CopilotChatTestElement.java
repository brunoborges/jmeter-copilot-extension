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

import org.apache.jmeter.testelement.AbstractTestElement;

/**
 * Test element that holds configuration for the Copilot Chat visualizer.
 * This element stores settings like the selected AI model and chat history.
 */
public class CopilotChatTestElement extends AbstractTestElement {

    private static final long serialVersionUID = 1L;

    public static final String MODEL = "CopilotChatTestElement.model";
    public static final String DEFAULT_MODEL = "claude-sonnet-4.5";

    /**
     * Creates a new CopilotChatTestElement with default settings.
     */
    public CopilotChatTestElement() {
        super();
    }

    /**
     * Get the selected AI model for chat.
     *
     * @return The model name
     */
    public String getModel() {
        return getPropertyAsString(MODEL, DEFAULT_MODEL);
    }

    /**
     * Set the AI model for chat.
     *
     * @param model The model name
     */
    public void setModel(String model) {
        setProperty(MODEL, model);
    }
}
