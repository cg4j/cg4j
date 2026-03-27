# Architecture

This document shows the internal architecture and data flow of cg4j.

## ASM Engine Flow

The ASM engine is a lightweight alternative to WALA using the ASM bytecode library with RTA
(Rapid Type Analysis) for call resolution. It uses fake-root seeding, lambda synthesis, and a
single receiver-event-driven worklist to build precise call graphs.

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

:Apply Scope Exclusions;
note right: Remove excluded\nPrimordial classes

|Analysis|
:Build Class Hierarchy;
note right: Inheritance tree for\nvirtual call resolution

:Find Entry Points;
note right: Public, non-abstract methods\nfrom public Application classes

:Seed Fake Root;
note right
  • Materialize entry-point args
  • Add boot calls for entry points
  • Pre-allocate representative types
end note

:Single Worklist (RTA);
note right
  • Reachable-method queue + receiver-event queue
  • Parse bytecode with ASM
  • Track allocations and static field owners
  • Process INVOKEDYNAMIC lambdas
  • Register receiver types incrementally
  • Dispatch virtual/interface calls as receivers appear
  • Trigger class initializers as needed
  • Add new targets to worklist
  • Repeat until both queues are empty
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
- **Worklist Algorithm:** Iteratively processes reachable methods and receiver events until no new work remains
- **Receiver Event Dispatch:** Incrementally re-dispatches virtual/interface calls as new concrete receiver types are registered
- **ASM:** Lightweight Java bytecode manipulation library used for parsing .class files
- **Call Site:** A method invocation instruction (INVOKE*) in bytecode
- **INVOKEDYNAMIC:** Bytecode instruction used for lambda expressions and method references
- **Lambda Factory:** Creates synthetic classes for lambda expressions matching WALA's `wala/lambda$...` naming; these synthetic classes inherit the caller's loader scope
- **RT (Runtime):** Java runtime classes from the JDK (java.*, javax.*, etc.)
- **Entry Points:** Starting methods for analysis (public, non-abstract methods on public Application classes)
- **Fake Root / `<boot>`:** Synthetic root logic that seeds entry point calls, constructor materialization, and static initializer edges
- **Fixpoint:** State where no new reachable methods, receiver events, or edges are discovered
