<p align="center">
  <img src="docs/assets/cg4j-logo.svg" alt="cg4j logo" width="256" height="256">
</p>

# CG4j - Call Graph Generation for Java

<p align="center">
  <a href="https://github.com/cg4j/cg4j/blob/master/LICENSE"><img src="https://img.shields.io/github/license/cg4j/cg4j?style=flat-square" alt="License"></a>
  <a href="https://github.com/cg4j/cg4j/actions/workflows/build.yml"><img src="https://img.shields.io/github/actions/workflow/status/cg4j/cg4j/build.yml?branch=master&label=build&style=flat-square" alt="Build"></a>
  <a href="https://mvnrepository.com/artifact/net.cg4j/cg4j"><img src="https://img.shields.io/maven-central/v/net.cg4j/cg4j?label=maven&style=flat-square" alt="Maven"></a>
</p>

A command-line tool to build call graphs for Java programs.

## Table of Contents

- [Features](#features)
- [Requirements](#requirements)
- [Quickstart](#quickstart)
- [Usage](#usage)
- [Docker](#docker)
- [Development](#development)
- [Output Format](#output-format)
- [Documentation](#documentation)
- [License](#license)

## Features

- Builds call graphs using Rapid Type Analysis (RTA)
- Analyzes JAR files and their dependencies
- Generates all public methods as entry points
- Outputs call graph to CSV file
- Clean CLI powered by picocli

## Requirements

- Java 11 or higher
- Maven 3.6 or higher (for building from source)
- Docker (optional, for containerized usage)

## Quickstart

Install `cg4j` for the current user on Linux, macOS, or WSL. No `sudo` access is required.

```bash
curl -fsSL https://cg4j.net/install.sh | bash
```

> Note: If `cg4j` is already installed, `install.sh` will prompt to uninstall the current installation and then exit. Run the installer again if you want to reinstall `cg4j`.

Verify installation:

```bash
cg4j --help
```

## Usage

After installation:
```bash
# Basic usage - outputs to callgraph.csv
cg4j -j myapp.jar

# With dependencies and custom output
cg4j -j myapp.jar -o output.csv -d lib/

# Use the ASM engine
cg4j -j myapp.jar --engine asm

# Suppress info/progress logs
cg4j -j myapp.jar -q
```

**Options:**
- `-j, --app-jar=<file>` - JAR file to analyze (required)
- `-o, --output=<file>` - Output CSV file (default: `callgraph.csv`)
- `-d, --deps=<dir>` - Directory containing dependency JAR files
- `--include-rt` - Include Java runtime in call graph analysis (default: `true`)
- `--engine` - Call graph engine: `wala` or `asm` (default: `wala`)
- `--exclusions` - Exclusion patterns file for the ASM engine; see [`default-exclusions.txt`](src/main/resources/default-exclusions.txt)
- `-q, --quiet` - Suppress info/progress logs (errors only)
- `-h, --help` - Show help message
- `-V, --version` - Show version information

## Docker

### Build and Run

```bash
# Build image
docker build -t cg4j:latest .

# Run analysis
docker run --rm \
  -v $(pwd):/input:ro \
  -v $(pwd):/output \
  cg4j:latest -j /input/myapp.jar -o /output/callgraph.csv
```

### Docker Compose

```bash
# Configure paths (first time only)
cp .env.example .env
# Edit .env to set INPUT_DIR and OUTPUT_DIR

# Run analysis
docker-compose run --rm cg4j -j /input/myapp.jar -o /output/callgraph.csv

# Show help
docker-compose run --rm cg4j --help
```

Default configuration (`.env`):
- Input: `./src/test/resources/test-jars`
- Output: `/tmp/cg4j-output`

## Development

Build from source using Make:

```bash
make build
```

Or using Maven directly:
```bash
mvn clean package
```

This creates `target/cg4j-<version>-jar-with-dependencies.jar`

Install cg4j from the local source tree:

```bash
make install
```

Uninstall the local development install:

```bash
make uninstall
```

**Note:** If `cg4j` command is not found after installation, ensure `~/.local/bin` is in your PATH:

```bash
# Add to ~/.bashrc or ~/.zshrc
export PATH="$HOME/.local/bin:$PATH"
```

Then restart your terminal or run `source ~/.bashrc`.

### Testing

Using Make:
```bash
make test
```

Or using Maven directly:
```bash
# Run tests
mvn test

# Run tests with coverage report
mvn clean test jacoco:report

# Check coverage percentage
grep -oP '<tfoot>.*?<td class="ctr2">\K[0-9]+%' target/site/jacoco/index.html | head -1
```

Test data: `src/test/resources/test-jars/`

## Output Format

CSV file with two columns:
```
source_method,target_method
package/Class.method:(descriptor),package/Class.method:(descriptor)
```

Example:
```
source_method,target_method
<boot>,org/slf4j/Logger.info:(Ljava/lang/String;)V
org/slf4j/Logger.info:(Ljava/lang/String;)V,org/slf4j/helpers/MessageFormatter.format:(Ljava/lang/String;)Ljava/lang/String;
```

## Documentation

- **New to call graphs?** Read [Call Graph Basics](docs/CALLGRAPH-BASICS.md) to learn about CHA, RTA, and how call graphs are built.
- **System architecture:** See [Architecture](docs/ARCHITECTURE.md) for the internal design and data flow.

## License

Licensed under the [GNU Affero General Public License v3.0](LICENSE).

Copyright (C) 2026 CG4j Team
