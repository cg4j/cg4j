# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

cg4j (Call Graph for Java) builds call graphs from Java JAR files using IBM WALA. It uses 0-CFA analysis with Class Hierarchy Analysis to generate call graphs, outputting results as CSV.

- **Language**: Java 11+ (CI uses Java 21)
- **Build Tool**: Maven 3.6+
- **Package**: `net.cg4j`
- **Entry Point**: `net.cg4j.Main`
- **Note**: Ignore `.venv/` folder for context/analysis (Python virtual environment), but Python/pip tools may be used to run Python scripts

## Build and Test Commands

```bash
# Build fat JAR
mvn clean package

# Run all tests (parallel execution enabled)
mvn test

# Run specific test class
mvn test -Dtest=IntegrationTest

# Run with coverage report (90% line coverage enforced)
mvn clean test jacoco:report

# Run the tool
java -jar target/cg4j-0.1.0-SNAPSHOT-jar-with-dependencies.jar <jarfile> -o output.csv -d deps/
```

## Architecture

### Core Classes

- **Main.java** - CLI entry point using Picocli. Validates inputs, orchestrates analysis, writes CSV output.
- **CallGraphBuilder.java** - Core WALA analysis engine. Key methods:
  - `buildCallGraph()` - Main entry point for 0-CFA analysis
  - `constructCHA()` - Builds Class Hierarchy with three loaders
  - `generateEntryPoints()` - Creates entry points from public methods in Application loader
  - `extractEdgesAsStream()` - Extracts call graph edges as lazy stream

### WALA Class Loaders

The analysis uses three class loaders:
- **Primordial** - Java runtime classes (JDK/rt.jar)
- **Extension** - Dependency JARs from `-d` option
- **Application** - Target JAR being analyzed (entry points come only from here)

### Output Format

CSV with method URIs: `package/Class.method:(descriptor)`
- WALA fake root methods are normalized to `<boot>`
- Lambda metafactory calls are filtered out

## Code Style

- **Indentation**: 2 spaces (no tabs)
- **Line length**: ~100 characters
- **Braces**: K&R style (opening brace on same line)
- **Imports**: No wildcards, sorted alphabetically, third-party before java.*
- **Naming**: PascalCase for classes, camelCase for methods/variables, `is*` prefix for boolean methods
- **Javadoc**: Required for all public classes/methods and all test methods

## Testing

- **Framework**: JUnit 5 with AssertJ assertions
- **Structure**: Unit tests in `CallGraphBuilderTest.java` (9 tests), integration tests in `IntegrationTest.java` (12 tests)
- **Test data**: Fixed JAR versions in `src/test/resources/test-jars/`
- **Parallel execution**: Enabled via `junit-platform.properties` with dynamic strategy

## Error Handling

- Declare checked exceptions with `throws` - don't catch unnecessarily
- Use `try-with-resources` for all auto-closeable resources
- Use Log4j for logging: `logger.info("Message: {}", value)`

## Key Dependencies

- **IBM WALA 1.6.12** - Call graph analysis framework
- **Picocli 4.7.5** - CLI framework
- **Log4j 2.20.0** - Logging

## Benchmarking

**Script**: `benchmark/compare_cg.py` - Compare call graph CSV files

```bash
# Show comparison table (nodes/edges with differences)
python benchmark/compare_cg.py cg1.csv cg2.csv

# Show edge set intersection (Venn diagram style)
python benchmark/compare_cg.py --diff cg1.csv cg2.csv

# Show both tables
python benchmark/compare_cg.py --count --diff cg1.csv cg2.csv
```

- `--count`: Node/edge count comparison with percentages (default behavior)
- `--diff`: Edge set analysis showing only in CG1, intersection, only in CG2, and totals
