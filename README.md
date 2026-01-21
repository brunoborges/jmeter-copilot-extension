# JMeter Copilot Plugin

[![Build](https://github.com/brunoborges/jmeter-copilot-chat/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/brunoborges/jmeter-copilot-chat/actions/workflows/build.yml)

An Apache JMeter plugin that provides a GitHub Copilot Chat experience for generating JMeter test plans through natural language conversation.

## Features

- 🤖 **AI-Powered Test Generation**: Describe your tests in natural language and let Copilot generate the appropriate JMeter elements
- 💬 **Chat Interface**: Interactive chat panel integrated directly into JMeter
- 🔧 **Multiple Element Types**: Generate HTTP requests, thread groups, assertions, timers, controllers, and more
- 🔄 **Seamless Integration**: Generated elements are automatically added to your test plan

## Requirements

- Java 17 or later
- Apache JMeter 5.6.3 or later
- GitHub Copilot CLI installed and authenticated
- [Copilot SDK for Java](https://github.com/brunoborges/copilot-sdk/tree/main/java) (version 0.1.0)

## Installation

### Quick Install (Recommended)

Download the latest release and install it to your JMeter installation with a single command (`JMETER_HOME` should point to your JMeter installation):

```bash
curl -sL $(curl -s https://api.github.com/repos/brunoborges/jmeter-copilot/releases/latest | grep "browser_download_url.*jar" | cut -d '"' -f 4) -o $JMETER_HOME/lib/ext/jmeter-copilot-plugin.jar
```

Or, if you prefer to specify the JMeter path directly:

```bash
curl -sL $(curl -s https://api.github.com/repos/brunoborges/jmeter-copilot/releases/latest | grep "browser_download_url.*jar" | cut -d '"' -f 4) -o /path/to/jmeter/lib/ext/jmeter-copilot-plugin.jar
```

### Manual Installation

1. Download the latest JAR from [GitHub Releases](https://github.com/brunoborges/jmeter-copilot/releases/latest)
2. Copy the JAR file to your JMeter `lib/ext` directory
3. Restart JMeter

## Building from Source

### Prerequisites

1. Install the Copilot SDK locally (until it's published to Maven Central):

```bash
cd /path/to/copilot-sdk/java
mvn install
```

2. Build the plugin:

```bash
cd copilot-extension
mvn clean package
```

### Installing to JMeter

Copy the shaded JAR to JMeter's `lib/ext` directory:

```bash
cp target/jmeter-copilot-plugin-1.0.0-SNAPSHOT.jar $JMETER_HOME/lib/ext/
```

Or use the Maven profile (requires setting `jmeter.home` property):

```bash
mvn install -Pinstall-to-jmeter -Djmeter.home=/path/to/jmeter
```

## Usage

1. Start JMeter
2. Add a new **Listener** → **Copilot Chat** to your test plan
3. Describe the test you want to create in the chat input
4. Copilot will generate the appropriate JMeter elements and add them to your test plan

### Example Prompts

- "Create an HTTP GET request to https://api.example.com/users"
- "Add a Thread Group with 10 users and 5 iterations"
- "Create a load test for a REST API with POST requests to /api/login"
- "Add response assertions to verify status code 200"
- "Create a cookie manager and header manager for API authentication"

## Supported JMeter Elements

| Category | Elements |
|----------|----------|
| Samplers | HTTP Request |
| Thread Groups | Thread Group |
| Assertions | Response Assertion |
| Timers | Constant Timer, Random Timer |
| Config Elements | HTTP Header Manager, HTTP Cookie Manager |
| Controllers | Loop Controller, If Controller, Transaction Controller |
| Listeners | View Results Tree, Summary Report, Aggregate Report |

## Architecture

```
jmeter-copilot-plugin/
├── src/main/java/org/apache/jmeter/plugins/copilot/
│   ├── CopilotChatVisualizer.java    # Main JMeter visualizer/GUI component
│   ├── CopilotChatTestElement.java   # Test element for storing configuration
│   ├── JMeterTestPlanIntegrator.java # Integrates generated tests into JMeter
│   ├── gui/
│   │   └── CopilotChatPanel.java     # Swing chat panel UI
│   ├── service/
│   │   └── CopilotService.java       # Copilot SDK integration
│   └── tools/
│       └── JMeterTestGeneratorTool.java # Generates JMeter XML elements
└── src/main/resources/
    ├── META-INF/services/             # JMeter plugin service registration
    └── org/apache/jmeter/plugins/copilot/
        └── CopilotChatResources.properties
```

## Configuration

### AI Models

The plugin supports multiple AI models through the Copilot SDK:

- `claude-sonnet-4.5` (default)
- `gpt-4.1`
- `claude-4-opus`
- `gpt-4.1-mini`

Select your preferred model from the dropdown in the chat panel.

## Development

### Running Tests

```bash
mvn test
```

### Code Style

The project uses standard Java code style. Format code before committing:

```bash
mvn spotless:apply
```

## Troubleshooting

### Copilot CLI Not Found

Ensure the GitHub Copilot CLI is installed and in your PATH:

```bash
copilot --version
```

### Connection Issues

The plugin requires an active internet connection and valid GitHub Copilot authentication. Run `copilot auth login` if you encounter authentication issues.

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.

## Contributing

Contributions are welcome! Please read the contributing guidelines before submitting pull requests.
