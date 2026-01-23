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

## Call Graph Construction Algorithms

cg4j supports two algorithms for resolving virtual method calls: **CHA** and **RTA**.

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

## How cg4j Uses CHA and RTA

cg4j provides two engines with different algorithm support:

### WALA Engine (Default)

- Uses **0-CFA** (Zero Context-Flow-Insensitive Analysis) with CHA
- More mature and feature-rich
- Handles complex Java features

### ASM Engine

- Supports both **CHA** and **RTA** algorithms
- Faster and more lightweight
- Good for straightforward call graph construction

**Usage:**
```bash
# ASM with CHA (default)
java -jar cg4j.jar --engine=asm --algorithm=cha app.jar

# ASM with RTA (more precise)
java -jar cg4j.jar --engine=asm --algorithm=rta app.jar

# WALA (uses 0-CFA with CHA)
java -jar cg4j.jar --engine=wala app.jar
```

### Worklist Algorithm

Both CHA and RTA use a worklist-based approach:

1. **Start with entry points** (all public methods in cg4j)

2. **For each reachable method:**
   - Scan method bytecode for call sites
   - Track instantiations (`new` expressions) - **RTA only**
   - Resolve virtual calls using CHA or RTA
   - Add discovered methods to worklist

3. **Repeat** until no new methods are discovered

**Key difference:**
- **CHA**: Resolves calls to all subtypes in hierarchy
- **RTA**: Resolves calls only to instantiated subtypes

## How cg4j Builds Call Graphs

1. **Load JAR file** and dependencies
2. **Find entry points** (all public methods)
3. **Build class hierarchy** from loaded classes
4. **Run worklist algorithm**:
   - Process each reachable method
   - Extract call sites from bytecode
   - Track instantiations (RTA only)
   - Resolve virtual calls using CHA or RTA
   - Expand the call graph iteratively
5. **Output CSV** with source → target edges

## Limitations

### What cg4j Handles

✅ Direct method calls
✅ Virtual method calls (using CHA)
✅ Constructor calls
✅ Static method calls

### What cg4j May Miss

❌ Reflection (`Method.invoke()`)
❌ Dynamic proxy calls
❌ Lambda expressions (depends on WALA support)
❌ Native methods

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

- [WALA Documentation](http://wala.sourceforge.net/)
- [Call Graph Construction Algorithms](https://yanniss.github.io/points-to-tutorial15.pdf) (Academic paper)
- [Static Analysis Overview](https://cs.au.dk/~amoeller/spa/)

## Quick Reference

| What You Want | WALA (0-CFA/CHA) | ASM with CHA | ASM with RTA |
|---------------|------------------|--------------|--------------|
| Fast analysis | ✅ Fast | ✅ Very fast | ✅ Fast |
| More precision | ⚠️ Moderate | ⚠️ Lower | ✅ Higher |
| Handle reflection | ❌ Limited | ❌ No | ❌ No |
| Sound results | ✅ Yes | ✅ Yes | ✅ Yes |
| Large programs | ✅ Yes | ✅ Yes | ✅ Yes |
| Smaller call graphs | ⚠️ Moderate | ❌ Larger | ✅ Smaller |

**Recommendation**: Start with ASM+RTA for better precision. Use WALA for complex Java features or ASM+CHA for maximum speed.
