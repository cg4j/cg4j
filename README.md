# cg4j - Call Graph for Java

A command-line tool to build call graphs from Java JAR files using IBM WALA (T.J. Watson Libraries for Analysis).

## Features

- Builds 0-CFA call graphs using Class Hierarchy Analysis (CHA)
- Analyzes JAR files and their dependencies
- Generates all public methods as entry points
- Outputs call graph to CSV file
- Clean CLI powered by picocli
- Based on ml4cgp_study implementation

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
