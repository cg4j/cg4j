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

## Class Hierarchy Analysis (CHA)

**CHA** is an algorithm that determines which method might be called at a call site.

### The Problem

In object-oriented programs, method calls can be dynamic:
```java
Animal a = getAnimal(); // Could be Dog, Cat, Bird...
a.speak();              // Which speak() method runs?
```

### How CHA Works

CHA uses the class hierarchy (inheritance tree) to find all possible targets:

1. Look at the declared type (`Animal`)
2. Find all subclasses (`Dog`, `Cat`, `Bird`)
3. Include all implementations of the method (`speak()`)

**Result**: Conservative approximation - includes all possible targets, may include some impossible ones.

### CHA Trade-offs

✅ **Pros:**
- Fast to compute
- Simple to understand
- Sound (won't miss real calls)

❌ **Cons:**
- Over-approximates (includes impossible calls)
- Less precise than flow-sensitive analysis

## 0-CFA (Zero Context-Flow-Insensitive Analysis)

**0-CFA** is a call graph construction algorithm that cg4j uses.

### What Does "0-CFA" Mean?

- **0**: Zero calling context (doesn't track call chains)
- **C**: Context-sensitive
- **F**: Flow-insensitive (ignores execution order)
- **A**: Analysis

### How 0-CFA Works

1. **Start with entry points**
   - In cg4j: all public methods

2. **For each method reachable:**
   - Find all method calls in its body
   - Use CHA to determine possible targets
   - Add edges to the call graph
   - Add newly discovered methods to the worklist

3. **Repeat** until no new methods are discovered

### Example

```java
// Entry point
public void main() {
    Animal a = new Dog();
    a.speak();  // CHA finds: Dog.speak(), Cat.speak(), Bird.speak()
}

class Dog extends Animal {
    public void speak() { bark(); }
    private void bark() { }
}
```

**Call graph edges:**
```
main() → Dog.speak()
main() → Cat.speak()    // CHA conservatively includes this
main() → Bird.speak()   // And this
Dog.speak() → Dog.bark()
```

Even though we know `a` is a `Dog`, 0-CFA with CHA includes all possible subtypes.

## How cg4j Builds Call Graphs

1. **Load JAR file** and dependencies
2. **Find entry points** (all public methods)
3. **Build class hierarchy** from loaded classes
4. **Run 0-CFA algorithm**:
   - Process each reachable method
   - Use CHA to resolve method calls
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

| What You Want | What You Get |
|---------------|--------------|
| Fast analysis | ✅ CHA is very fast |
| Perfect precision | ❌ Over-approximates possible calls |
| Handle reflection | ❌ Not supported |
| Sound results | ✅ Won't miss real calls (for supported features) |
| Scale to large programs | ✅ Efficient for large codebases |
