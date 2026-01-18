# cg4j - Call Graph for Java

A command-line tool to build call graphs from Java JAR files using IBM WALA (T.J. Watson Libraries for Analysis).

## Features

- Builds 0-CFA call graphs using Class Hierarchy Analysis (CHA)
- Analyzes JAR files and their dependencies
- Generates all public methods as entry points
- Outputs call graph to CSV file
- Clean CLI powered by picocli

## Build

```bash
mvn clean package
```

This creates `target/cg4j-1.0-SNAPSHOT-jar-with-dependencies.jar`

## Usage

```bash
Usage: cg4j [-hV] [-d=<depsDir>] [-o=<outputCsv>] <targetJar>

Arguments:
  <targetJar>              JAR file to analyze (required)

Options:
  -o, --output=<file>      Output CSV file (default: callgraph.csv)
  -d, --deps=<dir>         Directory containing dependency JAR files
  -h, --help               Show help message and exit
  -V, --version            Print version information and exit
```

### Examples

```bash
# Basic usage - outputs to callgraph.csv
java -jar target/cg4j-1.0-SNAPSHOT-jar-with-dependencies.jar myapp.jar

# Specify output file with short option
java -jar target/cg4j-1.0-SNAPSHOT-jar-with-dependencies.jar myapp.jar -o output.csv

# Specify output file with long option
java -jar target/cg4j-1.0-SNAPSHOT-jar-with-dependencies.jar myapp.jar --output=output.csv

# Include dependencies
java -jar target/cg4j-1.0-SNAPSHOT-jar-with-dependencies.jar myapp.jar -o output.csv -d lib/

# Show help
java -jar target/cg4j-1.0-SNAPSHOT-jar-with-dependencies.jar --help

# Show version
java -jar target/cg4j-1.0-SNAPSHOT-jar-with-dependencies.jar --version
```

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

## Requirements

- Java 11 or higher
- Maven 3.6 or higher

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

# View XML coverage report (for CI)
cat target/site/jacoco/jacoco.xml
```

### Test Structure

The project includes comprehensive tests with 90% code coverage target:

- **Unit Tests** (`CallGraphBuilderTest.java`): 9 tests covering CallGraphBuilder internals
- **Integration Tests** (`IntegrationTest.java`): 12 tests covering end-to-end scenarios

### Test Scenarios

1. **Application only (no RT jar)** - Pure application analysis
2. **Application only (with RT jar)** - Application with Java runtime
3. **Application + dependencies (no RT jar)** - Multi-JAR without RT
4. **Application + dependencies (with RT jar)** - Complete call graph

### Test Resources

Test JARs are included in `src/test/resources/test-jars/`:
- `slf4j-api-2.0.17.jar` - Simple library (69 KB)
- `mockito-core-5.21.0.jar` - Library with dependencies (695 KB)
- `deps/` - Mockito dependencies (9.1 MB)

These JARs are fixed versions and should not be updated.

### Coverage Requirements

- Minimum 90% line coverage enforced
- Build fails if coverage drops below threshold
- Reports: HTML (`target/site/jacoco/index.html`) and XML (`target/site/jacoco/jacoco.xml`)
