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
        methods, Opcodes.ACC_PUBLIC, ClassLoaderType.PRIMORDIAL, false);

    Set<MethodSignature> parentMethods = new HashSet<>();
    parentMethods.add(new MethodSignature("com/example/Parent", "parentMethod", "()V"));

    ClassInfo parentClass = new ClassInfo("com/example/Parent", "java/lang/Object",
        Collections.emptySet(), parentMethods, Opcodes.ACC_PUBLIC, ClassLoaderType.APPLICATION, false);

    Set<MethodSignature> childMethods = new HashSet<>();
    childMethods.add(new MethodSignature("com/example/Child", "childMethod", "()V"));

    ClassInfo childClass = new ClassInfo("com/example/Child", "com/example/Parent",
        Collections.emptySet(), childMethods, Opcodes.ACC_PUBLIC, ClassLoaderType.APPLICATION, false);

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
        objectMethods, Opcodes.ACC_PUBLIC, ClassLoaderType.PRIMORDIAL, false);

    Set<MethodSignature> childMethods = new HashSet<>();
    childMethods.add(new MethodSignature("com/example/Child", "myMethod", "()V"));

    ClassInfo childClass = new ClassInfo("com/example/Child", "java/lang/Object",
        Collections.emptySet(), childMethods, Opcodes.ACC_PUBLIC, ClassLoaderType.APPLICATION, false);

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
   * Unit test: Tests ClassHierarchy RTA virtual call resolution.
   * Expects only instantiated types to be considered.
   */
  @Test
  void testResolveVirtualCallRTA() {
    Set<MethodSignature> parentMethods = new HashSet<>();
    parentMethods.add(new MethodSignature("com/example/Parent", "doSomething", "()V"));

    ClassInfo parentClass = new ClassInfo("com/example/Parent", "java/lang/Object",
        Collections.emptySet(), parentMethods, Opcodes.ACC_PUBLIC, ClassLoaderType.APPLICATION, false);

    Set<MethodSignature> child1Methods = new HashSet<>();
    child1Methods.add(new MethodSignature("com/example/Child1", "doSomething", "()V"));

    ClassInfo child1Class = new ClassInfo("com/example/Child1", "com/example/Parent",
        Collections.emptySet(), child1Methods, Opcodes.ACC_PUBLIC, ClassLoaderType.APPLICATION, false);

    Set<MethodSignature> child2Methods = new HashSet<>();
    // Child2 inherits doSomething from Parent

    ClassInfo child2Class = new ClassInfo("com/example/Child2", "com/example/Parent",
        Collections.emptySet(), child2Methods, Opcodes.ACC_PUBLIC, ClassLoaderType.APPLICATION, false);

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
        Collections.emptySet(), methods, Opcodes.ACC_PUBLIC, ClassLoaderType.APPLICATION, false);

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

  /**
   * Unit test: Tests registering a synthetic lambda class into the hierarchy.
   * Expects the class to appear in lookups, subclass maps, and implementor maps.
   */
  @Test
  void testRegisterSyntheticClass() {
    // Build hierarchy with an interface
    ClassInfo objectClass = new ClassInfo("java/lang/Object", null, Collections.emptySet(),
        Collections.emptySet(), Opcodes.ACC_PUBLIC, ClassLoaderType.PRIMORDIAL, false);

    Set<MethodSignature> ifaceMethods = new HashSet<>();
    ifaceMethods.add(new MethodSignature("com/example/MyInterface", "doIt", "()V",
        Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT));

    ClassInfo ifaceClass = new ClassInfo("com/example/MyInterface", "java/lang/Object",
        Collections.emptySet(), ifaceMethods,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT,
        ClassLoaderType.APPLICATION, false);

    Map<String, ClassInfo> classes = new java.util.HashMap<>(Map.of(
        "java/lang/Object", objectClass,
        "com/example/MyInterface", ifaceClass
    ));

    ClassHierarchy hierarchy = new ClassHierarchy(classes);

    // Verify interface has no implementors yet
    assertThat(hierarchy.getDirectImplementors("com/example/MyInterface")).isEmpty();

    // Register a synthetic lambda class implementing the interface
    Set<MethodSignature> lambdaMethods = new HashSet<>();
    lambdaMethods.add(new MethodSignature("wala/lambda$test$0", "doIt", "()V",
        Opcodes.ACC_PUBLIC));

    ClassInfo syntheticClass = new ClassInfo(
        "wala/lambda$test$0",
        "java/lang/Object",
        Set.of("com/example/MyInterface"),
        lambdaMethods,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC,
        ClassLoaderType.APPLICATION,
        false
    );

    hierarchy.registerSyntheticClass(syntheticClass);

    // Verify the class is registered
    assertThat(hierarchy.hasClass("wala/lambda$test$0")).isTrue();
    assertThat(hierarchy.getClass("wala/lambda$test$0")).isEqualTo(syntheticClass);
    assertThat(hierarchy.size()).isEqualTo(3);

    // Verify subclass relation
    assertThat(hierarchy.getDirectSubclasses("java/lang/Object"))
        .contains("wala/lambda$test$0");

    // Verify implementor relation
    assertThat(hierarchy.getDirectImplementors("com/example/MyInterface"))
        .contains("wala/lambda$test$0");

    // Verify subtypes include the new synthetic class
    Set<String> ifaceSubtypes = hierarchy.getAllSubtypes("com/example/MyInterface");
    assertThat(ifaceSubtypes).contains("wala/lambda$test$0");

    // Verify method lookup works on the synthetic class
    MethodSignature method = hierarchy.lookupMethod("wala/lambda$test$0", "doIt", "()V");
    assertThat(method).isNotNull();
    assertThat(method.getOwner()).isEqualTo("wala/lambda$test$0");
  }
}
