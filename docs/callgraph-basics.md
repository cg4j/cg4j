# Call Graph Basics

This document explains the core concepts behind call graph generation in cg4j.

## What is a Call Graph?

A **call graph** is a directed graph that represents calling relationships between methods in a program.

- **Nodes**: Methods in your program
- **Edges**: Method calls (A → B means "method A calls method B")

**Example:**

```
main() → processData()
processData() → readFile()
processData() → writeFile()
```

## Why Are Call Graphs Useful?

- **Program understanding**: Visualize how code flows through your application
- **Impact analysis**: Find what breaks when you change a method
- **Security analysis**: Trace paths to sensitive operations
- **Dead code detection**: Find unreachable methods
- **Testing**: Identify test coverage gaps

## Types of Method Calls in Java

Understanding how method calls work at the bytecode level is essential for call graph construction.

### Static Calls (invokestatic)

Static calls invoke methods declared with the `static` keyword. These belong to the class itself rather than to instances. The target method is resolved at compile time because static methods cannot be overridden.

```java
Math.abs(-5);           // Static call
Collections.sort(list); // Static call
```

### Virtual Calls (invokevirtual)

Virtual calls invoke non-static instance methods that can be overridden by subclasses. The JVM determines at runtime which implementation to execute based on the actual object type, not the declared type. This enables polymorphism - all instance methods in Java are virtual by default.

```java
Animal a = new Dog();
a.speak();  // Virtual call - resolved at runtime to Dog.speak()
```

**Exception cases** (resolved statically):
- **Private methods**: Cannot be overridden
- **Final methods**: Cannot be overridden
- **Super calls**: `super.method()` always calls the superclass implementation

### Special Calls (invokespecial)

Special calls invoke constructors, superclass methods, and private methods. They are resolved statically because the target is unambiguous.

```java
new ArrayList<>();      // Constructor call
super.toString();       // Superclass method call
this.privateMethod();   // Private method call
```

### Interface Calls (invokeinterface)

Interface calls invoke methods declared in interfaces. Similar to virtual calls, the actual implementation is resolved at runtime based on the object's type.

```java
List<String> list = new ArrayList<>();
list.add("hello");  // Interface call - resolved to ArrayList.add()
```

### Dynamic Calls (invokedynamic)

Dynamic calls use the `invokedynamic` bytecode instruction, introduced for dynamically-typed JVM languages. Java uses it for lambda expressions and method references. The cg4j ASM engine handles lambda metafactory calls by creating synthetic lambda classes.

```java
list.forEach(x -> System.out.println(x));  // Lambda uses invokedynamic
Function<String, Integer> f = String::length;  // Method reference uses invokedynamic
```

**How cg4j handles lambdas:**
- Detects `LambdaMetafactory.metafactory` bootstrap methods
- Creates synthetic classes matching WALA's `wala/lambda$...` naming convention
- Generates two-hop edges: caller → synthetic SAM method → lambda body

### Why This Matters for Call Graphs

Static and special calls have a single target - easy to resolve. Virtual and interface calls are the challenge: the target depends on the runtime type. This is why call graph algorithms like CHA and RTA exist - they approximate which implementations might be called at virtual call sites.

## Call Graph Construction Algorithms

There are two common algorithms for resolving virtual method calls: **CHA** and **RTA**. cg4j uses RTA for its analysis.

### Class Hierarchy Analysis (CHA)

**CHA** is a fast algorithm that determines which methods might be called at a call site.

#### The Problem

In object-oriented programs, method calls can be dynamic:

```java
Animal a = getAnimal(); // Could be Dog, Cat, Bird...
a.speak();              // Which speak() method runs?
```

#### How CHA Works

CHA uses the class hierarchy (inheritance tree) to find all possible targets:

1. Look at the declared type (`Animal`)
2. Find all subclasses in the hierarchy (`Dog`, `Cat`, `Bird`)
3. Include all implementations of the method (`speak()`)

**Result**: Conservative approximation - includes **all** subtypes in the hierarchy, even if they're never instantiated.

#### CHA Trade-offs

✅ **Pros:**
- Very fast to compute
- Simple to understand
- Sound (won't miss real calls)
- No need to analyze method bodies

❌ **Cons:**
- Over-approximates (includes impossible calls)
- Creates edges to methods of classes never instantiated
- Less precise than type-sensitive analyses

### Rapid Type Analysis (RTA)

**RTA** is a more precise algorithm that only considers **instantiated types**.

#### How RTA Works

RTA tracks which classes are actually instantiated (created with `new`) during analysis:

1. Start with entry points
2. **Track instantiations**: Record every `new ClassName()` encountered
3. **Resolve virtual calls**: Only consider subtypes that have been instantiated
4. Repeat until no new types are discovered

**Result**: More precise than CHA - only includes methods of classes that are actually created.

#### RTA Example

```java
public void main() {
    Animal a = new Dog();
    a.speak();  // Which speak() methods are possible?
}

class Dog extends Animal {
    public void speak() { bark(); }
}

class Cat extends Animal {
    public void speak() { meow(); }
}

class Bird extends Animal {
    public void speak() { chirp(); }
}
```

**CHA result**: Includes `Dog.speak()`, `Cat.speak()`, `Bird.speak()` (all subtypes)

**RTA result**: Only includes `Dog.speak()` (only instantiated type)

#### RTA Trade-offs

✅ **Pros:**
- More precise than CHA
- Eliminates impossible calls to never-instantiated classes
- Still fast and scalable
- Sound (won't miss real calls)

❌ **Cons:**
- Slower than CHA (must analyze method bodies)
- Requires tracking instantiation sites
- Still over-approximates in some cases

### CHA vs RTA Comparison

| Aspect | CHA | RTA |
|--------|-----|-----|
| **Speed** | Fastest | Fast |
| **Precision** | Lower (all subtypes) | Higher (only instantiated) |
| **Analysis cost** | Class hierarchy only | Bytecode scanning required |
| **Call graph size** | Larger | Smaller |
| **Use case** | Quick overview | More accurate analysis |

### When to Use Each Algorithm

**Use CHA when:**
- You need fast results
- You want maximum soundness (catch everything)
- Precision is less important

**Use RTA when:**
- You need more accurate call graphs
- You want to reduce false positives
- Analysis time is acceptable

## How cg4j Uses These Algorithms

cg4j provides two engines:

### WALA Engine

- Uses **0-CFA** (Zero Context-Flow-Insensitive Analysis)
- More mature and feature-rich
- Handles complex Java features

### ASM Engine

- Uses **RTA** (Rapid Type Analysis) with fixed-point iteration
- Faster and more lightweight
- Handles lambda expressions and method references
- Good for accurate, efficient call graph construction

**Usage:**
```bash
# ASM engine (uses RTA)
java -jar cg4j.jar -j app.jar --engine=asm

# WALA engine (uses 0-CFA)
java -jar cg4j.jar -j app.jar --engine=wala
```

### Worklist Algorithm

cg4j uses a worklist-based approach for call graph construction:

1. **Start with entry points** (all public methods in cg4j)

2. **Main worklist pass - for each reachable method:**
   - Scan method bytecode for call sites
   - Track instantiations (`new` expressions)
   - Resolve virtual/interface calls using RTA
   - Process lambda `invokedynamic` sites (ASM engine)
   - Add discovered methods to worklist

3. **Fixed-point re-resolution (ASM engine only):**
   - After the main worklist completes, re-resolve all virtual/interface call sites
   - Recovers edges missed due to worklist ordering (e.g., lambda classes registered late)
   - Iterates until no new edges are discovered (typically 1-2 passes)

4. **Repeat** until convergence

#### Why Fixed-Point Iteration?

The RTA worklist is single-pass: when method A calls an interface method, only types instantiated *before* A is processed are found as targets. Types instantiated *after* (e.g., lambda classes created later in the worklist) are missed because A is never revisited.

**Example:**
```java
// Method A processes Function1.invoke() call
// Only finds lambda classes already registered

// Method B later creates a new lambda
// A never learns about B's lambda
```

**Solution:** After the main worklist, re-resolve all virtual/interface call sites against the complete set of instantiated types. This recovers edges to types that were registered late in the worklist. Converges quickly since types don't recursively create new types.

## How cg4j Builds Call Graphs

1. **Load JAR file** and dependencies
2. **Find entry points** (all public methods)
3. **Build class hierarchy** from loaded classes
4. **Run worklist algorithm**:
   - Process each reachable method
   - Extract call sites from bytecode
   - Track instantiations (`new` expressions)
   - Process lambda `invokedynamic` sites (ASM engine)
   - Resolve virtual/interface calls using RTA
   - Expand the call graph iteratively
5. **Fixed-point re-resolution** (ASM engine):
   - Re-resolve all virtual/interface calls with complete type set
   - Recover edges to late-registered types
6. **Output CSV** with source → target edges

## Limitations

### What cg4j Handles

✅ Direct method calls
✅ Virtual method calls (using RTA)
✅ Interface method calls
✅ Constructor calls
✅ Static method calls
✅ Lambda expressions and method references
✅ Static initializers (`<clinit>`)

### What cg4j May Miss

❌ Reflection (`Method.invoke()`, `Constructor.newInstance()`)
❌ Dynamic proxy calls (`Proxy.newProxyInstance()`)
❌ Method handles (`MethodHandle.invoke()`)
❌ Native methods
❌ Custom `invokedynamic` bootstraps (non-lambda)

## Key Terminology

| Term | Meaning |
|------|---------|
| **Static call** | Direct method call, target known at compile-time |
| **Virtual call** | Method call through object reference, resolved at runtime |
| **Entry point** | Starting method for analysis (e.g., `main()`, public methods) |
| **Reachable** | Method that can be called from an entry point |
| **Call site** | Location in code where a method call occurs |
| **Receiver type** | Type of object on which a method is called |

## Further Reading

### Foundational Papers

**CHA (Class Hierarchy Analysis):**
- Dean, J., Grove, D., and Chambers, C. (1995). *Optimization of Object-Oriented Programs Using Static Class Hierarchy Analysis*. ECOOP'95.
  - [PDF](https://web.cs.ucla.edu/~palsberg/tba/papers/dean-grove-chambers-ecoop95.pdf)
  - The original paper introducing CHA for call graph construction

**RTA (Rapid Type Analysis):**
- Bacon, D. F. and Sweeney, P. F. (1996). *Fast Static Analysis of C++ Virtual Function Calls*. OOPSLA'96.
  - [PDF](https://people.cs.vt.edu/ryder/516/sp03/papers/baconOOPSLA96.pdf)
  - Introduces RTA as a more precise alternative to CHA by tracking instantiated types

### Additional Resources

- [WALA Documentation](http://wala.sourceforge.net/) - Framework used by cg4j
- [Call Graph Construction Algorithms](https://yanniss.github.io/points-to-tutorial15.pdf) - Comprehensive tutorial on points-to analysis
- [Static Analysis Overview](https://cs.au.dk/~amoeller/spa/) - Course materials on static program analysis

## Quick Reference

| What You Want | WALA (0-CFA) | ASM (RTA) |
|---------------|--------------|-----------|
| Fast analysis | ✅ Fast | ✅ Very fast |
| More precision | ⚠️ Moderate | ✅ Higher |
| Handle reflection | ❌ Limited | ❌ No |
| Sound results | ✅ Yes | ✅ Yes |
| Large programs | ✅ Yes | ✅ Yes |
| Smaller call graphs | ⚠️ Moderate | ✅ Smaller |

**Recommendation**: Start with ASM for faster analysis and better precision. Use WALA for complex Java features.
