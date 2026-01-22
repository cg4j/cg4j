# Architecture

This document shows the internal architecture and data flow of cg4j.

## System Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                         Input Phase                             │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
                    ┌───────────────────────┐
                    │   Target JAR File     │
                    └───────────┬───────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                     Analysis Scope Setup                        │
└─────────────────────────────────────────────────────────────────┘
                                │
                ┌───────────────┼───────────────┐
                ▼               ▼               ▼
        ┌──────────────┐  ┌──────────┐  ┌────────────┐
        │ Primordial   │  │Extension │  │Application │
        │ (JDK/RT)     │  │ (Deps)   │  │ (Target)   │
        └──────┬───────┘  └────┬─────┘  └─────┬──────┘
               │               │               │
               └───────────────┼───────────────┘
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│              Class Hierarchy Analysis (CHA)                     │
│  • Builds inheritance tree                                      │
│  • Resolves virtual method calls                                │
└─────────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                   Entry Points Generation                       │
│  • Find all public methods in Application loader                │
│  • Skip Primordial and Extension classes                        │
└─────────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                     0-CFA Call Graph Build                      │
│  • Start from entry points                                      │
│  • Use CHA to resolve method calls                              │
│  • Iterate until fixpoint (no new edges)                        │
└─────────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Edge Extraction (Stream API)                 │
│  • Filter fake methods                                          │
│  • Normalize boot methods to <boot>                             │
│  • Filter RT classes (if includeRt=false)                       │
└─────────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                      CSV Output                                 │
│  source_method,target_method                                    │
│  <boot>,org/slf4j/Logger.info:(...)                            │
│  org/slf4j/Logger.info:(...),org/slf4j/helpers/...              │
└─────────────────────────────────────────────────────────────────┘
```

## Notes

### Class Loaders
- **Primordial:** Java runtime classes (JDK, RT jar) - standard library code
- **Extension:** Dependency JARs - external libraries your application uses
- **Application:** Target JAR being analyzed - your application code

### Keywords
- **RT (Runtime):** Java runtime classes from the JDK (java.*, javax.*, etc.)
- **<boot>:** Placeholder for WALA's fake root methods used to bootstrap analysis
- **CHA (Class Hierarchy Analysis):** Algorithm that uses inheritance tree to resolve method calls
- **0-CFA:** Zero-context-flow-insensitive analysis - fast call graph construction
- **Entry Points:** Starting methods for analysis (all public methods in Application loader)

### WALA Fake Methods
WALA creates artificial methods for analysis bootstrapping:
- `FakeRootClass.fakeRootMethod` - Artificial entry point that calls all real entry points
- `FakeRootClass.fakeWorldClinit` - Artificial class initializer
- These are normalized to `<boot>` in the output for clarity
