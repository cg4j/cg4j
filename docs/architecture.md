# Architecture

This document shows the internal architecture and data flow of cg4j.

## ASM Engine Flow

The ASM engine is a lightweight alternative to WALA using the ASM bytecode library with RTA (Rapid Type Analysis) for call resolution. It includes lambda support and fixed-point re-resolution for precise call graphs.

```kroki-plantuml
@startuml
skinparam backgroundColor white
skinparam defaultTextAlignment center

|Input|
start
:Target JAR File;

|Class Loading|
fork
  :JDK Classes\n(Primordial);
fork again
  :Dependency JARs\n(Extension);
fork again
  :Application Classes\n(Target JAR);
end fork

|Analysis|
:Build Class Hierarchy;
note right: Inheritance tree for\nvirtual call resolution

:Find Entry Points;
note right: Public methods from\nApplication classes

:Main Worklist (RTA);
note right
  • Pop method from worklist
  • Parse bytecode with ASM
  • Extract call sites & NEW instructions
  • Process INVOKEDYNAMIC lambdas
  • Resolve virtual/interface calls using RTA
  • Add new targets to worklist
  • Repeat until empty
end note

:Fixed-Point Re-resolution;
note right
  • Re-resolve all virtual/interface calls
  • Against complete instantiated types
  • Process newly discovered methods
  • Converges in 1-2 passes
end note

|Output|
:Filter Edges;
note right: Remove RT classes\nif includeRt=false

:CSV Output;
stop

@enduml
```

## Notes

### Class Loaders
- **Primordial:** Java runtime classes (JDK) - standard library code
- **Extension:** Dependency JARs - external libraries your application uses
- **Application:** Target JAR being analyzed - your application code

### Keywords
- **RTA (Rapid Type Analysis):** Uses instantiated types (from NEW instructions) to prune virtual call targets
- **Worklist Algorithm:** Iteratively processes methods until no new reachable methods are found
- **Fixed-Point Re-resolution:** Re-resolves virtual/interface calls after main worklist to recover late-registered types
- **ASM:** Lightweight Java bytecode manipulation library used for parsing .class files
- **Call Site:** A method invocation instruction (INVOKE*) in bytecode
- **INVOKEDYNAMIC:** Bytecode instruction used for lambda expressions and method references
- **Lambda Factory:** Creates synthetic classes for lambda expressions matching WALA's `wala/lambda$...` naming
- **RT (Runtime):** Java runtime classes from the JDK (java.*, javax.*, etc.)
- **Entry Points:** Starting methods for analysis (all public methods in Application loader)
- **<boot>:** Synthetic root method that calls all entry points and static initializers
- **Fixpoint:** State where no new methods or edges are discovered
