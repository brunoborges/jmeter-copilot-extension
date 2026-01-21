# Apache JMeter Copilot Chat Plugin

[![Build](https://github.com/brunoborges/jmeter-copilot-chat/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/brunoborges/jmeter-copilot-chat/actions/workflows/build.yml)
[![Java 17+](https://img.shields.io/badge/Java-17%2B-blue?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Apache JMeter](https://img.shields.io/badge/JMeter-5.6.3%2B-green?logo=apache&logoColor=white)](https://jmeter.apache.org/)
[![License: MIT](https://img.shields.io/github/license/brunoborges/jmeter-copilot)](https://github.com/brunoborges/jmeter-copilot/blob/main/LICENSE)

An Apache JMeter plugin that provides a GitHub Copilot Chat experience for generating JMeter test plans through natural language conversation.

## Features

- 🤖 **AI-Powered Test Generation**: Describe your tests in natural language and let Copilot generate the appropriate JMeter elements
- 🛠️ **Execute Test Cases Directly**: Run tests within JMeter without defining test plans
- 💬 **Chat Interface**: Interactive chat panel integrated directly into JMeter
- 🔧 **Multiple Element Types**: Generate HTTP requests, thread groups, assertions, timers, controllers, and more
- 🔄 **Seamless Integration**: Generated elements are automatically added to your test plan

## Requirements

- Java 17 or later
- Apache JMeter 5.6.3 or later
- GitHub Copilot CLI installed and authenticated

## Installation

### Quick Install (Recommended)

Download the latest release and install it to your JMeter installation with a single command.

#### Linux/macOS

Using `JMETER_HOME` environment variable:

```bash
curl -sL $(curl -s https://api.github.com/repos/brunoborges/jmeter-copilot/releases/latest | grep "browser_download_url.*jar" | cut -d '"' -f 4) -o $JMETER_HOME/lib/ext/jmeter-copilot-plugin.jar
```

Or specify the JMeter path directly:

```bash
curl -sL $(curl -s https://api.github.com/repos/brunoborges/jmeter-copilot/releases/latest | grep "browser_download_url.*jar" | cut -d '"' -f 4) -o /path/to/jmeter/lib/ext/jmeter-copilot-plugin.jar
```

#### Windows (PowerShell)

Using `JMETER_HOME` environment variable:

```powershell
$release = Invoke-RestMethod -Uri "https://api.github.com/repos/brunoborges/jmeter-copilot/releases/latest"
$jarUrl = ($release.assets | Where-Object { $_.name -like "*.jar" }).browser_download_url
Invoke-WebRequest -Uri $jarUrl -OutFile "$env:JMETER_HOME\lib\ext\jmeter-copilot-plugin.jar"
```

Or specify the JMeter path directly:

```powershell
$release = Invoke-RestMethod -Uri "https://api.github.com/repos/brunoborges/jmeter-copilot/releases/latest"
$jarUrl = ($release.assets | Where-Object { $_.name -like "*.jar" }).browser_download_url
Invoke-WebRequest -Uri $jarUrl -OutFile "C:\path\to\jmeter\lib\ext\jmeter-copilot-plugin.jar"
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
