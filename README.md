# cg4j - Call Graph for Java

A command-line tool to build call graphs from Java JAR files using IBM WALA (T.J. Watson Libraries for Analysis).

## Table of Contents

- [Features](#features)
- [Requirements](#requirements)
- [Build](#build)
- [Docker](#docker)
- [Usage](#usage)
- [Output Format](#output-format)
- [Testing](#testing)
- [Documentation](#documentation)

## Features

- Builds 0-CFA call graphs using Class Hierarchy Analysis (CHA)
- Analyzes JAR files and their dependencies
- Generates all public methods as entry points
- Outputs call graph to CSV file
- Clean CLI powered by picocli

## Requirements

- Java 11 or higher
- Maven 3.6 or higher
- Docker (optional, for containerized usage)

## Build

```bash
mvn clean package
```

This creates `target/cg4j-1.0-SNAPSHOT-jar-with-dependencies.jar`

## Docker

### Build and Run

```bash
# Build image
docker build -t cg4j:latest .

# Run analysis
docker run --rm \
  -v $(pwd):/input:ro \
  -v $(pwd):/output \
  cg4j:latest -o /output/callgraph.csv /input/myapp.jar
```

### Docker Compose

```bash
# Configure paths (first time only)
cp .env.example .env
# Edit .env to set INPUT_DIR and OUTPUT_DIR

# Run analysis
docker-compose run --rm cg4j -o /output/callgraph.csv /input/myapp.jar

# Show help
docker-compose run --rm cg4j --help
```

Default configuration (`.env`):
- Input: `./src/test/resources/test-jars`
- Output: `/tmp/cg4j-output`

## Usage

```bash
# Basic usage - outputs to callgraph.csv
java -jar target/cg4j-1.0-SNAPSHOT-jar-with-dependencies.jar myapp.jar

# With dependencies and custom output
java -jar target/cg4j-1.0-SNAPSHOT-jar-with-dependencies.jar myapp.jar -o output.csv -d lib/
```

**Options:**
- `-o, --output=<file>` - Output CSV file (default: callgraph.csv)
- `-d, --deps=<dir>` - Directory containing dependency JAR files
- `-h, --help` - Show help message

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

## Testing

### Running Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=IntegrationTest

# Run specific test method
mvn test -Dtest=IntegrationTest#testAppOnly_NoRT_Slf4j

# Run tests with coverage report
mvn clean test jacoco:report

# View HTML coverage report
open target/site/jacoco/index.html

# Get coverage percentage from report
grep -oP '<tfoot>.*?<td class="ctr2">\K[0-9]+%' target/site/jacoco/index.html | head -1

# View XML coverage report (for CI)
cat target/site/jacoco/jacoco.xml
```

### Test Structure

The project includes comprehensive tests with 90% code coverage target:

- **Unit Tests** (`CallGraphBuilderTest.java`): 9 tests covering CallGraphBuilder internals
- **Integration Tests** (`IntegrationTest.java`): 12 tests covering end-to-end scenarios
- **Parallel Execution**: Tests run concurrently using JUnit 5 for faster execution (~30% speedup)

### Test Resources

Test JARs are included in `src/test/resources/test-jars/`:
- `slf4j-api-2.0.17.jar` - Simple library (69 KB)
- `okhttp-jvm-5.3.2.jar` - HTTP client library (849 KB)
- `deps/` - OkHttp dependencies (~2.1 MB)
  - `kotlin-stdlib-2.2.21.jar`
  - `okio-jvm-3.16.4.jar`

**Total test resources: ~2.9 MB**

These JARs are fixed versions and should not be updated.

## Documentation

**New to call graphs?** Read [Call Graph Basics](docs/CALLGRAPH-BASICS.md) to learn about CHA, 0-CFA, and how call graphs are built.
