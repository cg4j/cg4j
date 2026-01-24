package net.cg4j.asm;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ClassHierarchy class.
 */
class ClassHierarchyTest {

  /**
   * Unit test: Tests ClassHierarchy subtype queries.
   * Expects correct transitive subtype relationships.
   */
  @Test
  void testSubtypeQueries() {
    // Build a simple hierarchy: Object -> Parent -> Child
    Set<MethodSignature> methods = new HashSet<>();
    methods.add(new MethodSignature("java/lang/Object", "toString", "()Ljava/lang/String;"));

    ClassInfo objectClass = new ClassInfo("java/lang/Object", null, Collections.emptySet(),
        methods, Opcodes.ACC_PUBLIC, ClassLoaderType.PRIMORDIAL);

    Set<MethodSignature> parentMethods = new HashSet<>();
    parentMethods.add(new MethodSignature("com/example/Parent", "parentMethod", "()V"));

    ClassInfo parentClass = new ClassInfo("com/example/Parent", "java/lang/Object",
        Collections.emptySet(), parentMethods, Opcodes.ACC_PUBLIC, ClassLoaderType.APPLICATION);

    Set<MethodSignature> childMethods = new HashSet<>();
    childMethods.add(new MethodSignature("com/example/Child", "childMethod", "()V"));

    ClassInfo childClass = new ClassInfo("com/example/Child", "com/example/Parent",
        Collections.emptySet(), childMethods, Opcodes.ACC_PUBLIC, ClassLoaderType.APPLICATION);

    Map<String, ClassInfo> classes = Map.of(
        "java/lang/Object", objectClass,
        "com/example/Parent", parentClass,
        "com/example/Child", childClass
    );

    ClassHierarchy hierarchy = new ClassHierarchy(classes);

    // Test subtype queries
    Set<String> objectSubtypes = hierarchy.getAllSubtypes("java/lang/Object");
    assertThat(objectSubtypes).contains("java/lang/Object", "com/example/Parent", "com/example/Child");

    Set<String> parentSubtypes = hierarchy.getAllSubtypes("com/example/Parent");
    assertThat(parentSubtypes).contains("com/example/Parent", "com/example/Child");
    assertThat(parentSubtypes).doesNotContain("java/lang/Object");

    Set<String> childSubtypes = hierarchy.getAllSubtypes("com/example/Child");
    assertThat(childSubtypes).containsExactly("com/example/Child");
  }

  /**
   * Unit test: Tests ClassHierarchy method lookup with inheritance.
   * Expects methods to be found in parent classes.
   */
  @Test
  void testMethodLookup() {
    Set<MethodSignature> objectMethods = new HashSet<>();
    objectMethods.add(new MethodSignature("java/lang/Object", "toString", "()Ljava/lang/String;"));

    ClassInfo objectClass = new ClassInfo("java/lang/Object", null, Collections.emptySet(),
        objectMethods, Opcodes.ACC_PUBLIC, ClassLoaderType.PRIMORDIAL);

    Set<MethodSignature> childMethods = new HashSet<>();
    childMethods.add(new MethodSignature("com/example/Child", "myMethod", "()V"));

    ClassInfo childClass = new ClassInfo("com/example/Child", "java/lang/Object",
        Collections.emptySet(), childMethods, Opcodes.ACC_PUBLIC, ClassLoaderType.APPLICATION);

    Map<String, ClassInfo> classes = Map.of(
        "java/lang/Object", objectClass,
        "com/example/Child", childClass
    );

    ClassHierarchy hierarchy = new ClassHierarchy(classes);

    // Child should find inherited method
    MethodSignature inherited = hierarchy.lookupMethod("com/example/Child", "toString", "()Ljava/lang/String;");
    assertThat(inherited).isNotNull();
    assertThat(inherited.getOwner()).isEqualTo("java/lang/Object");

    // Child should find its own method
    MethodSignature own = hierarchy.lookupMethod("com/example/Child", "myMethod", "()V");
    assertThat(own).isNotNull();
    assertThat(own.getOwner()).isEqualTo("com/example/Child");
  }

  /**
   * Unit test: Tests ClassHierarchy CHA virtual call resolution.
   * Expects all possible implementations across class hierarchy.
   */
  @Test
  void testResolveVirtualCall() {
    Set<MethodSignature> parentMethods = new HashSet<>();
    parentMethods.add(new MethodSignature("com/example/Parent", "doSomething", "()V"));

    ClassInfo parentClass = new ClassInfo("com/example/Parent", "java/lang/Object",
        Collections.emptySet(), parentMethods, Opcodes.ACC_PUBLIC, ClassLoaderType.APPLICATION);

    Set<MethodSignature> child1Methods = new HashSet<>();
    child1Methods.add(new MethodSignature("com/example/Child1", "doSomething", "()V"));

    ClassInfo child1Class = new ClassInfo("com/example/Child1", "com/example/Parent",
        Collections.emptySet(), child1Methods, Opcodes.ACC_PUBLIC, ClassLoaderType.APPLICATION);

    Set<MethodSignature> child2Methods = new HashSet<>();
    // Child2 inherits doSomething from Parent

    ClassInfo child2Class = new ClassInfo("com/example/Child2", "com/example/Parent",
        Collections.emptySet(), child2Methods, Opcodes.ACC_PUBLIC, ClassLoaderType.APPLICATION);

    Map<String, ClassInfo> classes = Map.of(
        "com/example/Parent", parentClass,
        "com/example/Child1", child1Class,
        "com/example/Child2", child2Class
    );

    ClassHierarchy hierarchy = new ClassHierarchy(classes);

    // Resolve virtual call on Parent
    Set<MethodSignature> targets = hierarchy.resolveVirtualCall("com/example/Parent", "doSomething", "()V");

    // Should find: Parent.doSomething, Child1.doSomething, and Parent.doSomething (via Child2)
    assertThat(targets).hasSize(2);
    assertThat(targets).anyMatch(m -> m.getOwner().equals("com/example/Parent"));
    assertThat(targets).anyMatch(m -> m.getOwner().equals("com/example/Child1"));
  }

  /**
   * Unit test: Tests ClassHierarchy RTA virtual call resolution.
   * Expects only instantiated types to be considered.
   */
  @Test
  void testResolveVirtualCallRTA() {
    Set<MethodSignature> parentMethods = new HashSet<>();
    parentMethods.add(new MethodSignature("com/example/Parent", "doSomething", "()V"));

    ClassInfo parentClass = new ClassInfo("com/example/Parent", "java/lang/Object",
        Collections.emptySet(), parentMethods, Opcodes.ACC_PUBLIC, ClassLoaderType.APPLICATION);

    Set<MethodSignature> child1Methods = new HashSet<>();
    child1Methods.add(new MethodSignature("com/example/Child1", "doSomething", "()V"));

    ClassInfo child1Class = new ClassInfo("com/example/Child1", "com/example/Parent",
        Collections.emptySet(), child1Methods, Opcodes.ACC_PUBLIC, ClassLoaderType.APPLICATION);

    Set<MethodSignature> child2Methods = new HashSet<>();
    // Child2 inherits doSomething from Parent

    ClassInfo child2Class = new ClassInfo("com/example/Child2", "com/example/Parent",
        Collections.emptySet(), child2Methods, Opcodes.ACC_PUBLIC, ClassLoaderType.APPLICATION);

    Map<String, ClassInfo> classes = Map.of(
        "com/example/Parent", parentClass,
        "com/example/Child1", child1Class,
        "com/example/Child2", child2Class
    );

    ClassHierarchy hierarchy = new ClassHierarchy(classes);

    // Only Child1 is instantiated
    Set<String> instantiatedTypes = Set.of("com/example/Child1");

    // RTA should only find Child1.doSomething
    Set<MethodSignature> rtaTargets = hierarchy.resolveVirtualCallRTA(
        "com/example/Parent", "doSomething", "()V", instantiatedTypes);

    assertThat(rtaTargets).hasSize(1);
    assertThat(rtaTargets).anyMatch(m -> m.getOwner().equals("com/example/Child1"));

    // Compare with CHA which finds more targets
    Set<MethodSignature> chaTargets = hierarchy.resolveVirtualCall(
        "com/example/Parent", "doSomething", "()V");

    assertThat(chaTargets.size()).isGreaterThan(rtaTargets.size());
  }

  /**
   * Unit test: Tests ClassHierarchy resolveCallSiteRTA method.
   * Expects static calls to resolve regardless of instantiation and virtual calls to respect instantiation.
   */
  @Test
  void testResolveCallSiteRTA() {
    Set<MethodSignature> methods = new HashSet<>();
    methods.add(new MethodSignature("com/example/MyClass", "staticMethod", "()V",
        Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC));
    methods.add(new MethodSignature("com/example/MyClass", "instanceMethod", "()V",
        Opcodes.ACC_PUBLIC));

    ClassInfo myClass = new ClassInfo("com/example/MyClass", "java/lang/Object",
        Collections.emptySet(), methods, Opcodes.ACC_PUBLIC, ClassLoaderType.APPLICATION);

    Map<String, ClassInfo> classes = Map.of("com/example/MyClass", myClass);
    ClassHierarchy hierarchy = new ClassHierarchy(classes);

    Set<String> instantiatedTypes = Set.of("com/example/MyClass");

    // Static call should resolve regardless of instantiation
    CallSite staticCall = new CallSite(Opcodes.INVOKESTATIC, "com/example/MyClass",
        "staticMethod", "()V", false);
    Set<MethodSignature> staticTargets = hierarchy.resolveCallSiteRTA(staticCall, Set.of());
    assertThat(staticTargets).hasSize(1);

    // Virtual call should respect instantiated types
    CallSite virtualCall = new CallSite(Opcodes.INVOKEVIRTUAL, "com/example/MyClass",
        "instanceMethod", "()V", false);
    Set<MethodSignature> virtualTargets = hierarchy.resolveCallSiteRTA(virtualCall, instantiatedTypes);
    assertThat(virtualTargets).hasSize(1);

    // Virtual call with empty instantiated types should return empty
    Set<MethodSignature> emptyTargets = hierarchy.resolveCallSiteRTA(virtualCall, Set.of());
    assertThat(emptyTargets).isEmpty();
  }
}
