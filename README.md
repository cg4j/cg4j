# Java Call Graph Demo with WALA

A simple tool to build call graphs from Java JAR files using IBM WALA (T.J. Watson Libraries for Analysis).

## Features

- Builds 0-CFA call graphs using Class Hierarchy Analysis (CHA)
- Analyzes JAR files and their dependencies
- Generates all public methods as entry points
- Outputs call graph to CSV file
- Based on ml4cgp_study implementation

## Build

```bash
mvn clean package
```

This creates `target/java-cg-wala-1.0-SNAPSHOT-jar-with-dependencies.jar`

## Usage

```bash
# Basic usage (outputs to callgraph.csv)
java -jar target/java-cg-wala-1.0-SNAPSHOT-jar-with-dependencies.jar <target.jar>

# Specify output file
java -jar target/java-cg-wala-1.0-SNAPSHOT-jar-with-dependencies.jar <target.jar> <output.csv>

# Include dependencies
java -jar target/java-cg-wala-1.0-SNAPSHOT-jar-with-dependencies.jar <target.jar> <output.csv> <dependencies-dir>
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
