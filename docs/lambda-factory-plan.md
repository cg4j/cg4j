# Lambda Factory Support for ASM Engine

Implementation plan for adding WALA-compatible `LambdaMetafactory` handling to the ASM call graph engine.

---

## Problem Statement

The ASM engine completely ignores `INVOKEDYNAMIC` instructions. When Java/Kotlin compiles lambda expressions or method references, the compiler emits `INVOKEDYNAMIC` with `LambdaMetafactory.metafactory` as the bootstrap method. The ASM engine's `CallSiteExtractor` does not override `visitInvokeDynamicInsn()`, so **all lambda call edges are invisible**.

### Benchmark Evidence

**Benchmark command:** `.venv/bin/python benchmark/compare_cg.py --count --diff cgs/okhttp_cg_rta_with_deps_WALA.csv cgs/okhttp_cg_rta_with_deps_ASM.csv`

**Count comparison (okhttp with deps, `--include-rt=false`):**

| Metric | WALA | ASM | Difference |
|--------|------|-----|------------|
| Nodes | 4,468 | 4,620 | +152 (+3.4%) |
| Edges | 13,319 | 27,141 | +13,822 (+103.8%) |

**Edge set diff:**

| Category | Count | Percentage |
|----------|-------|------------|
| Only in WALA | 2,788 | 9.3% |
| Intersection (Both) | 10,518 | 35.1% |
| Only in ASM | 16,623 | 55.5% |
| Total Unique | 29,929 | 100.0% |

**Lambda-specific gap:**
- WALA CG contains **132 lambda-related edges** (64 edges involving synthetic `wala/lambda$...` classes)
- ASM CG contains **3 lambda-related edges** (only coincidental matches on `$lambda$` in method names)
- WALA models **30 unique synthetic lambda classes** in the okhttp call graph

---

## How WALA Handles Lambdas

WALA creates **synthetic lambda classes** that bridge the caller to the lambda body:

### Two-hop edge pattern

```
caller_method  ->  wala/lambda$OwnerClass$N.SAM_method()     [hop 1: virtual dispatch]
wala/lambda$OwnerClass$N.SAM_method()  ->  OwnerClass.lambda_body_method()  [hop 2: delegation]
```

### Example from CG

```
okhttp3/internal/cache/FaultHidingSink.write:(Lokio/Buffer;J)V
  -> wala/lambda$okhttp3$internal$cache$DiskLruCache$Editor$0.invoke:(Ljava/lang/Object;)Ljava/lang/Object;

wala/lambda$okhttp3$internal$cache$DiskLruCache$Editor$0.invoke:(Ljava/lang/Object;)Ljava/lang/Object;
  -> okhttp3/internal/cache/DiskLruCache$Editor.newSink$lambda$0$0:(...)Lkotlin/Unit;
```

### Naming convention

```
wala/lambda$<owner_class_with_slashes_as_dollars>$<per_class_index>
```

- Owner `okhttp3/internal/cache/DiskLruCache$Editor` becomes `okhttp3$internal$cache$DiskLruCache$Editor`
- Index is a zero-based counter per owner class
- The SAM method name and descriptor come from the functional interface (e.g., `invoke`, `create`, `run`)
- The SAM method descriptor uses the **erased** (bridge) type from the functional interface

---

## Implementation Plan

### Approach: Synthetic Lambda Classes (WALA-compatible)

Create synthetic `ClassInfo` entries in the class hierarchy for each lambda `INVOKEDYNAMIC` site, replicating WALA's modeling. This produces edge-compatible output between engines.

### Effort Estimate

| Task | Files Changed | Estimated Lines | Complexity |
|------|--------------|-----------------|------------|
| 1. CallSiteExtractor: capture INVOKEDYNAMIC | 1 | ~40 | Low |
| 2. New LambdaCallSite data class | 1 (new) | ~60 | Low |
| 3. AsmCallGraphBuilder: lambda processing | 1 | ~80 | Medium |
| 4. ClassHierarchy: register synthetic classes | 1 | ~15 | Low |
| 5. Unit tests | 2 | ~120 | Medium |
| 6. Integration test updates | 1 | ~30 | Low |
| **Total** | **6-7 files** | **~345 lines** | **Medium** |

**Estimated effort: 1-2 days for a developer familiar with the codebase.**

---

### Task 1: Capture INVOKEDYNAMIC in CallSiteExtractor

**File:** `src/main/java/net/cg4j/asm/CallSiteExtractor.java`

**Change:** Override `visitInvokeDynamicInsn()` to capture lambda metafactory calls.

```java
// New import
import org.objectweb.asm.Handle;

// New field
private final List<LambdaCallSite> lambdaCallSites = new ArrayList<>();

@Override
public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle,
                                   Object... bootstrapMethodArguments) {
  // Only handle LambdaMetafactory bootstrap methods
  if (!isLambdaMetafactory(bootstrapMethodHandle)) {
    return;
  }

  // bootstrapMethodArguments[1] is the Handle to the implementation method
  if (bootstrapMethodArguments.length >= 2
      && bootstrapMethodArguments[1] instanceof Handle) {
    Handle implHandle = (Handle) bootstrapMethodArguments[1];
    lambdaCallSites.add(new LambdaCallSite(
        name,        // SAM method name (e.g., "invoke", "run", "apply")
        descriptor,  // invokedynamic descriptor (captures + returns functional interface)
        implHandle.getOwner(),
        implHandle.getName(),
        implHandle.getDesc(),
        implHandle.getTag()
    ));
  }
}

private static boolean isLambdaMetafactory(Handle handle) {
  return handle.getOwner().equals("java/lang/invoke/LambdaMetafactory")
      && (handle.getName().equals("metafactory")
          || handle.getName().equals("altMetafactory"));
}

public List<LambdaCallSite> getLambdaCallSites() {
  return lambdaCallSites;
}
```

**Key insight:** ASM's `Handle` in `bootstrapMethodArguments[1]` directly tells us the lambda body method. No bytecode analysis of the lambda body is needed at this stage.

---

### Task 2: New LambdaCallSite Data Class

**File:** `src/main/java/net/cg4j/asm/LambdaCallSite.java` (new)

**Purpose:** Immutable data class holding the information extracted from an `INVOKEDYNAMIC` lambda bootstrap.

```java
package net.cg4j.asm;

/**
 * Represents a lambda/method-reference captured from an INVOKEDYNAMIC instruction.
 */
public final class LambdaCallSite {

  private final String samMethodName;       // "invoke", "run", "apply", etc.
  private final String indyDescriptor;      // invokedynamic call-site descriptor
  private final String implOwner;           // lambda body owner class
  private final String implName;            // lambda body method name
  private final String implDescriptor;      // lambda body method descriptor
  private final int implTag;                // Handle tag (REF_invokeStatic, etc.)

  // Constructor, getters, toString
}
```

**Fields explained:**
- `samMethodName`: The single abstract method name on the functional interface (e.g., `invoke` for `Function1`, `run` for `Runnable`)
- `indyDescriptor`: The descriptor of the invokedynamic instruction. Its return type is the functional interface. Captured variables are the parameters.
- `implOwner/implName/implDescriptor`: The actual lambda body method (e.g., `okhttp3/internal/_UtilJvmKt.asFactory$lambda$0`)
- `implTag`: Whether it's INVOKESTATIC, INVOKESPECIAL, etc.

---

### Task 3: Lambda Processing in AsmCallGraphBuilder

**File:** `src/main/java/net/cg4j/asm/AsmCallGraphBuilder.java`

**Changes in `runWorklist()` method and new helper `processLambdaCallSite()`:**

#### 3a. Update MethodAnalysisResult to include lambda call sites

```java
private static class MethodAnalysisResult {
  final List<CallSite> callSites;
  final Set<String> instantiatedTypes;
  final List<LambdaCallSite> lambdaCallSites;  // NEW

  MethodAnalysisResult(List<CallSite> callSites, Set<String> instantiatedTypes,
                       List<LambdaCallSite> lambdaCallSites) {
    this.callSites = callSites;
    this.instantiatedTypes = instantiatedTypes;
    this.lambdaCallSites = lambdaCallSites;
  }
}
```

#### 3b. Update MethodAnalysisVisitor to capture lambda sites

In the `visitMethod()` override, also capture `extractor.getLambdaCallSites()`.

#### 3c. Process lambda call sites in the worklist

For each `LambdaCallSite` found during method analysis:

1. **Generate synthetic class name:** `wala/lambda$<owner/$->$>$<index>`
   - Maintain a `Map<String, AtomicInteger>` counter per owner class
   - Owner is the class containing the `INVOKEDYNAMIC` instruction (the current method's owner)

2. **Create synthetic ClassInfo:**
   - Name: the generated synthetic name
   - SuperName: `java/lang/Object`
   - Interfaces: extract functional interface from `indyDescriptor` return type
   - Methods: one SAM method with erased descriptor from the functional interface
   - Access: `ACC_PUBLIC | ACC_FINAL | ACC_SYNTHETIC`
   - LoaderType: same as the owner class
   - hasClinit: false

3. **Register in hierarchy** (see Task 4)

4. **Create two edges:**
   - **Edge 1:** `currentMethod -> syntheticClass.samMethod` (the enclosing method "calls" the lambda's SAM)
   - **Edge 2:** `syntheticClass.samMethod -> implOwner.implMethod` (the SAM delegates to the body)

5. **Add the impl method to the worklist** (so its call sites are also analyzed)

6. **Mark the synthetic class as instantiated** (for RTA virtual dispatch)

```java
// In runWorklist(), after processing regular call sites:
for (LambdaCallSite lambda : analysisResult.lambdaCallSites) {
  processLambdaCallSite(method, lambda, edges, worklist, reachable, instantiatedTypes);
}
```

#### 3d. SAM descriptor resolution

The SAM method descriptor for the synthetic class uses the **functional interface's erased SAM descriptor**, not the specialized one. For example, `Function1<String, Unit>` has SAM `invoke:(Ljava/lang/Object;)Ljava/lang/Object;` (erased), not `invoke:(Ljava/lang/String;)Lkotlin/Unit;`.

To get this: look up the functional interface (from `indyDescriptor` return type) in the class hierarchy, find its single abstract method, and use that descriptor. If the interface isn't in the hierarchy, fall back to using `bootstrapMethodArguments[0]` (the `Type` representing the erased SAM descriptor) from the original INVOKEDYNAMIC instruction.

**Simpler approach:** Pass `bootstrapMethodArguments[0]` (a `Type` representing the erased SAM method type) through `LambdaCallSite` as `samDescriptor`. This avoids hierarchy lookup entirely.

---

### Task 4: Register Synthetic Classes in ClassHierarchy

**File:** `src/main/java/net/cg4j/asm/ClassHierarchy.java`

**Change:** Add a method to register dynamically-created synthetic classes.

```java
/**
 * Registers a synthetic class (e.g., lambda) into the hierarchy.
 */
public void registerSyntheticClass(ClassInfo syntheticClass) {
  classes.put(syntheticClass.getName(), syntheticClass);

  // Update reverse relations
  if (syntheticClass.getSuperName() != null) {
    subclasses.computeIfAbsent(syntheticClass.getSuperName(), k -> new HashSet<>())
        .add(syntheticClass.getName());
  }
  for (String iface : syntheticClass.getInterfaces()) {
    implementors.computeIfAbsent(iface, k -> new HashSet<>())
        .add(syntheticClass.getName());
  }

  // Invalidate affected cache entries
  subtypesCache.clear();
}
```

**Note:** Clearing the subtypes cache is safe but aggressive. Since lambda classes are leaf nodes (no subclasses), a more targeted invalidation could just remove cache entries for the superclass and interfaces. However, `clear()` is simpler and the cache rebuilds lazily.

---

### Task 5: Unit Tests

**File:** `src/test/java/net/cg4j/asm/LambdaCallSiteTest.java` (new)

Tests for the new data class:
- Constructor and getters
- toString format

**File:** `src/test/java/net/cg4j/asm/CallSiteExtractorTest.java` (update)

Add tests using synthetic bytecode generated with ASM ClassWriter:
- `testExtractsLambdaMetafactory`: Create bytecode with INVOKEDYNAMIC using LambdaMetafactory bootstrap, verify `getLambdaCallSites()` returns the correct `LambdaCallSite`
- `testIgnoresNonLambdaInvokeDynamic`: Verify that INVOKEDYNAMIC with non-LambdaMetafactory bootstrap (e.g., string concatenation `makeConcatWithConstants`) is ignored
- `testAltMetafactory`: Test `altMetafactory` bootstrap variant
- `testLambdaAndRegularCallSites`: Verify both regular `CallSite` and `LambdaCallSite` lists are populated correctly in the same method

**File:** `src/test/java/net/cg4j/asm/ClassHierarchyTest.java` (update)

- `testRegisterSyntheticClass`: Register a synthetic class and verify it appears in hierarchy lookups, subclass maps, and implementor maps

---

### Task 6: Integration Test Updates

**File:** `src/test/java/net/cg4j/AsmIntegrationTest.java` or `src/test/java/net/cg4j/RtaIntegrationTest.java`

- Update edge count assertions if they use exact counts (currently they use `> N` thresholds, so they should still pass)
- Add a new test that verifies lambda edges exist in the output:
  ```java
  @Test
  void testAsmEngine_LambdaEdges_OkHttp() {
    // Run ASM engine on okhttp
    // Parse CSV output
    // Assert presence of edges matching "wala/lambda$" pattern
    // Verify two-hop pattern: caller -> lambda_SAM and lambda_SAM -> body
  }
  ```

---

## Implementation Notes

### Edge cases to handle

1. **Method references** (e.g., `MyClass::myMethod`): Same INVOKEDYNAMIC pattern as lambdas. The impl handle points to the referenced method. No special handling needed.

2. **Constructor references** (e.g., `MyClass::new`): The impl handle tag is `REF_newInvokeSpecial` (8). The impl method is `<init>`. Should work with the same logic.

3. **altMetafactory**: Used for serializable lambdas, lambdas with markers, or lambdas that need bridge methods. Same extraction logic; just check for both `metafactory` and `altMetafactory` bootstrap names.

4. **String concatenation INVOKEDYNAMIC**: Java 9+ uses `StringConcatFactory.makeConcatWithConstants` via INVOKEDYNAMIC for string concatenation (`"a" + b`). The bootstrap method owner is `java/lang/invoke/StringConcatFactory`, not `LambdaMetafactory`, so the `isLambdaMetafactory()` check correctly ignores these.

5. **Lambda counter persistence**: The per-class lambda index counter should be scoped to the entire worklist run (field on `AsmCallGraphBuilder`), not per-method. This ensures unique names across the entire call graph.

6. **Duplicate lambda sites**: If the same INVOKEDYNAMIC site is visited multiple times (shouldn't happen with the reachable set, but defensively), the synthetic class should be created only once. Use a `Map<String, ClassInfo>` keyed by synthetic class name to deduplicate.

7. **JDK classes with lambdas**: Since JDK bytecode is not loaded into `classBytecode`, lambdas inside JDK methods are never analyzed. This matches WALA's behavior with exclusions.

### Files changed summary

| File | Change Type | Description |
|------|-------------|-------------|
| `CallSiteExtractor.java` | Modify | Add `visitInvokeDynamicInsn()`, lambda fields |
| `LambdaCallSite.java` | New | Immutable data class for lambda info |
| `AsmCallGraphBuilder.java` | Modify | Process lambdas in worklist, create synthetic classes |
| `ClassHierarchy.java` | Modify | Add `registerSyntheticClass()` method |
| `LambdaCallSiteTest.java` | New | Unit tests for data class |
| `CallSiteExtractorTest.java` | Modify | Add INVOKEDYNAMIC extraction tests |
| `ClassHierarchyTest.java` | Modify | Add synthetic class registration test |
| `AsmIntegrationTest.java` | Modify | Add lambda edge integration test |

### What NOT to change

- `CallSite.java`: No changes. INVOKEDYNAMIC is a different concept from regular method call sites.
- `MethodSignature.java`: No changes. Synthetic lambda methods use the same `MethodSignature` format.
- `CallGraphResult.java`: No changes. Lambda edges use the same `Edge` type.
- `Main.java`: No changes. No new CLI options needed.
- `ClassInfoVisitor.java`: No changes. Synthetic classes are created programmatically, not from bytecode.
- WALA engine (`CallGraphBuilder.java`): No changes.

### Testing strategy

1. **Unit tests first**: Validate `CallSiteExtractor` captures INVOKEDYNAMIC correctly using synthetic bytecode
2. **ClassHierarchy tests**: Verify synthetic class registration
3. **Integration tests**: Run against okhttp with deps, verify lambda edges appear
4. **Benchmark**: Re-run `benchmark/compare_cg.py --diff` and verify the WALA-only lambda edges move into the intersection set
5. **Coverage**: Ensure new code is covered to maintain 90% JaCoCo threshold

### Expected impact on benchmark

After implementation, the 64 `wala/lambda$...` edges that are currently WALA-only should move into the intersection set. This would:
- Reduce "Only in WALA" from 2,788 to ~2,724 (-64)
- Increase "Intersection" from 10,518 to ~10,582 (+64)
- Increase ASM edges slightly (new synthetic lambda edges added)
- Add ~30 new nodes (synthetic lambda classes) to the ASM CG
