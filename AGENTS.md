# Agent Guidelines for cg4j

This document provides coding guidelines for AI agents working on the cg4j (Call Graph for Java) project.

## Project Overview

- **Language**: Java 11+
- **Build Tool**: Maven 3.6+
- **Main Package**: `net.cg4j`
- **Entry Point**: `net.cg4j.Main`
- **Artifact**: `cg4j-0.1.0-SNAPSHOT-jar-with-dependencies.jar`
- **Note**: Ignore `.venv/` folder for context/analysis (Python virtual environment), but Python/pip tools may be used to run Python scripts

## Build Commands

```bash
# Clean and build
mvn clean package
```

### Testing

**Test Framework**: JUnit 5 (Jupiter) with AssertJ assertions

**Run tests**:
```bash
# All tests (parallel execution enabled)
mvn test

# Specific test class
mvn test -Dtest=IntegrationTest

# With coverage report
mvn clean test jacoco:report
```

**Parallel Execution**:
- Tests run concurrently using JUnit 5 parallel execution
- Configuration: `src/test/resources/junit-platform.properties`
- Uses dynamic strategy (adapts to available CPU cores)
- Faster execution (~30% improvement on multi-core systems)

**Test Structure**:
- Unit tests: `src/test/java/net/cg4j/CallGraphBuilderTest.java` (9 tests)
- Integration tests: `src/test/java/net/cg4j/IntegrationTest.java` (12 tests)
- Test resources: `src/test/resources/test-jars/` (fixed JAR versions)
- Coverage target: 90% line coverage (enforced by JaCoCo)

## Code Style Guidelines

### Import Organization

1. Group imports in this order with blank lines between:
   - Third-party libraries (WALA, Picocli, Log4j) - alphabetically sorted
   - Java standard library - alphabetically sorted
2. No wildcard imports (`import java.util.*;`) - use explicit imports only
3. No unused imports

### Formatting

- **Indentation**: 2 spaces (no tabs)
- **Line length**: ~100 characters (flexible for readability)
- **Braces**: K&R style - opening brace on same line
```java
public void method() {
  // code
}
```
- **Spacing**: 
  - Space after keywords: `if (condition)`, `for (int i = 0; ...)`
  - Space around operators: `a + b`, `x = 5`
  - No space between method name and parenthesis: `method(args)`

### Naming Conventions

- **Classes**: PascalCase - `Main`, `CallGraphBuilder`
- **Methods**: camelCase - `buildCallGraph()`, `formatMethod()`
- **Variables/Fields**: camelCase - `targetJar`, `outputCsv`
- **Boolean methods**: Use `is` prefix - `isPublicClass()`, `isPublicMethod()`
- **Constants**: Not many used; inline literals acceptable
- Use descriptive names - avoid abbreviations except standard ones (e.g., `cg` for CallGraph)

### Documentation

**Required Javadoc**:
- All public classes and methods
- Brief one-line summaries preferred
- No need for `@param`/`@return` tags for simple methods

**Test Method Javadoc**:
- All test methods (unit and integration tests)
- Format:
  ```java
  /**
   * [Test type]: [Main purpose of test].
   * Expects [expected outcome/behavior].
   */
  ```
- Test type: "Unit test" or "Integration test"
- Keep it simple: 1-2 sentences maximum
- Focus on what is tested and what is expected
- Example:
  ```java
  /**
   * Unit test: Tests basic call graph construction from a simple JAR file.
   * Expects non-null call graph with at least one node.
   */
  @Test
  void testBuildCallGraph_ValidJar_Success() throws Exception {
    // test code
  }
  ```

**Inline Comments**:
- Use `//` style for single-line comments
- Place before the code block they describe
- Explain intent, not mechanics
- Use for complex logic or non-obvious sections

### Error Handling

**Exception Strategy**:
- Declare checked exceptions with `throws` - don't catch unnecessarily
- Use `try-with-resources` for all auto-closeable resources

**Validation**:
- Validate inputs at start of methods
- Provide user-friendly error messages

**Logging**:
- Use Log4j for all logging (errors, warnings, info messages)
- Use `logger.error()` for errors
- Use `logger.warn()` for warnings
- Use `logger.info()` for informational messages
- Include context (file paths, specific issue) using parameterized logging: `logger.info("Message: {}", value)`

**Null Handling**:
- Check for null before usage
- No external null-assertion libraries


### Code Organization

- **One class per file** (except static inner classes)
- **Access modifiers**: Public API is `public`, helpers are `private` or `private static`
- **Method order**: Public methods first, then private helpers
- **Validation first**: Input validation at start of methods

### Modern Java Practices

Use these Java 11+ features:
- **Stream API** for collection processing
- **Method references**: `this::methodName`
- **Diamond operator**: `new ArrayList<>()`
- **Try-with-resources** for auto-closeables
- **Underscore separators** in numeric literals: `1_000_000`

### Console Output

- Use Log4j for console output via `logger.info()`
- Include metrics and progress updates
- Use `String.format("%.2f", value)` for decimal formatting
- Blank lines (`\n`) to separate output sections

## Dependencies

Current dependencies (see `pom.xml`):
- **IBM WALA Core** - Call graph analysis
- **IBM WALA Util** - WALA utilities
- **Picocli** - CLI framework
- **Log4j API** - Logging API
- **Log4j Core** - Logging implementation

When adding dependencies:
- Use latest stable versions
- Specify explicit versions (no version ranges)
- Group related dependencies together

## Don'ts

- ❌ Don't use wildcard imports
- ❌ Don't use tabs (use 2 spaces)
- ❌ Don't catch exceptions unnecessarily
- ❌ Don't use abbreviations in names (except standard: `cg`, `csv`)
- ❌ Don't modify existing formatting style
- ❌ Don't add null-assertion libraries
- ❌ Don't use `TODO` comments without tracking

## Benchmarking

**Location**: `benchmark/compare_cg.py` - Python script for comparing call graph CSV files

**Usage**:
```bash
# Default: Show comparison table (nodes/edges counts)
python benchmark/compare_cg.py cg1.csv cg2.csv

# --count: Explicitly show comparison table (same as default)
python benchmark/compare_cg.py --count cg1.csv cg2.csv

# --diff: Show edge set intersection (Venn diagram)
python benchmark/compare_cg.py --diff cg1.csv cg2.csv

# Both: Show comparison table + edge intersection
python benchmark/compare_cg.py --count --diff cg1.csv cg2.csv
```

**Flags**:
- `--count`: Displays node/edge count comparison table with differences (percentages and absolute values)
- `--diff`: Displays edge set intersection analysis showing: only in CG1, intersection (both), only in CG2, and total unique edges with percentages

## Git

- `.gitignore` excludes: `target/`, `*.csv`
- Commit message style: Not specified, use conventional style
- No pre-commit hooks configured
