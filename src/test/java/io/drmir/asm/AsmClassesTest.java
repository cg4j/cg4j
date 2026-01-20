package io.drmir.asm;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ASM-based call graph classes.
 */
class AsmClassesTest {

  /**
   * Tests MethodSignature creation and URI formatting.
   */
  @Test
  void testMethodSignature_ToUri() {
    MethodSignature sig = new MethodSignature("java/lang/String", "length", "()I");

    assertThat(sig.getOwner()).isEqualTo("java/lang/String");
    assertThat(sig.getName()).isEqualTo("length");
    assertThat(sig.getDescriptor()).isEqualTo("()I");
    assertThat(sig.toUri()).isEqualTo("java/lang/String.length:()I");
    assertThat(sig.toString()).isEqualTo("java/lang/String.length:()I");
  }

  /**
   * Tests MethodSignature equality.
   */
  @Test
  void testMethodSignature_Equality() {
    MethodSignature sig1 = new MethodSignature("java/lang/String", "length", "()I");
    MethodSignature sig2 = new MethodSignature("java/lang/String", "length", "()I");
    MethodSignature sig3 = new MethodSignature("java/lang/String", "charAt", "(I)C");

    assertThat(sig1).isEqualTo(sig2);
    assertThat(sig1).isNotEqualTo(sig3);
    assertThat(sig1.hashCode()).isEqualTo(sig2.hashCode());
  }

  /**
   * Tests CallSite properties for different opcodes.
   */
  @Test
  void testCallSite_OpcodeTypes() {
    CallSite staticCall = new CallSite(Opcodes.INVOKESTATIC, "java/lang/Math", "abs", "(I)I", false);
    CallSite virtualCall = new CallSite(Opcodes.INVOKEVIRTUAL, "java/lang/String", "length", "()I", false);
    CallSite interfaceCall = new CallSite(Opcodes.INVOKEINTERFACE, "java/util/List", "size", "()I", true);
    CallSite specialCall = new CallSite(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);

    assertThat(staticCall.isStatic()).isTrue();
    assertThat(staticCall.isVirtual()).isFalse();
    assertThat(staticCall.isSpecial()).isFalse();

    assertThat(virtualCall.isStatic()).isFalse();
    assertThat(virtualCall.isVirtual()).isTrue();
    assertThat(virtualCall.isSpecial()).isFalse();

    assertThat(interfaceCall.isStatic()).isFalse();
    assertThat(interfaceCall.isVirtual()).isTrue();
    assertThat(interfaceCall.isInterface()).isTrue();

    assertThat(specialCall.isStatic()).isFalse();
    assertThat(specialCall.isVirtual()).isFalse();
    assertThat(specialCall.isSpecial()).isTrue();
  }

  /**
   * Tests CallSite toMethodSignature conversion.
   */
  @Test
  void testCallSite_ToMethodSignature() {
    CallSite callSite = new CallSite(Opcodes.INVOKEVIRTUAL, "java/lang/String", "length", "()I", false);
    MethodSignature sig = callSite.toMethodSignature();

    assertThat(sig.getOwner()).isEqualTo("java/lang/String");
    assertThat(sig.getName()).isEqualTo("length");
    assertThat(sig.getDescriptor()).isEqualTo("()I");
  }

  /**
   * Tests CallSite toString includes opcode name.
   */
  @Test
  void testCallSite_ToString() {
    CallSite callSite = new CallSite(Opcodes.INVOKEVIRTUAL, "java/lang/String", "length", "()I", false);
    assertThat(callSite.toString()).contains("INVOKEVIRTUAL");
    assertThat(callSite.toString()).contains("java/lang/String");
    assertThat(callSite.toString()).contains("length");
  }

  /**
   * Tests ClassInfo properties and method lookup.
   */
  @Test
  void testClassInfo_Properties() {
    Set<String> interfaces = new HashSet<>();
    interfaces.add("java/io/Serializable");

    Set<MethodSignature> methods = new HashSet<>();
    methods.add(new MethodSignature("com/example/MyClass", "myMethod", "()V"));
    methods.add(new MethodSignature("com/example/MyClass", "<init>", "()V"));

    ClassInfo info = new ClassInfo(
        "com/example/MyClass",
        "java/lang/Object",
        interfaces,
        methods,
        Opcodes.ACC_PUBLIC,
        ClassLoaderType.APPLICATION
    );

    assertThat(info.getName()).isEqualTo("com/example/MyClass");
    assertThat(info.getSuperName()).isEqualTo("java/lang/Object");
    assertThat(info.getInterfaces()).contains("java/io/Serializable");
    assertThat(info.getMethods()).hasSize(2);
    assertThat(info.getLoaderType()).isEqualTo(ClassLoaderType.APPLICATION);
    assertThat(info.isPublic()).isTrue();
    assertThat(info.isInterface()).isFalse();
    assertThat(info.isAbstract()).isFalse();
  }

  /**
   * Tests ClassInfo method lookup.
   */
  @Test
  void testClassInfo_HasMethod() {
    Set<MethodSignature> methods = new HashSet<>();
    methods.add(new MethodSignature("com/example/MyClass", "myMethod", "()V"));

    ClassInfo info = new ClassInfo(
        "com/example/MyClass",
        "java/lang/Object",
        Collections.emptySet(),
        methods,
        Opcodes.ACC_PUBLIC,
        ClassLoaderType.APPLICATION
    );

    assertThat(info.hasMethod("myMethod", "()V")).isTrue();
    assertThat(info.hasMethod("otherMethod", "()V")).isFalse();
    assertThat(info.getMethod("myMethod", "()V")).isNotNull();
    assertThat(info.getMethod("otherMethod", "()V")).isNull();
  }

  /**
   * Tests ClassInfo for interface class.
   */
  @Test
  void testClassInfo_InterfaceClass() {
    ClassInfo info = new ClassInfo(
        "com/example/MyInterface",
        "java/lang/Object",
        Collections.emptySet(),
        Collections.emptySet(),
        Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT,
        ClassLoaderType.APPLICATION
    );

    assertThat(info.isInterface()).isTrue();
    assertThat(info.isAbstract()).isTrue();
  }

  /**
   * Tests ClassHierarchy subtype queries.
   */
  @Test
  void testClassHierarchy_SubtypeQueries() {
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
   * Tests ClassHierarchy method lookup with inheritance.
   */
  @Test
  void testClassHierarchy_MethodLookup() {
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
   * Tests ClassHierarchy CHA virtual call resolution.
   */
  @Test
  void testClassHierarchy_ResolveVirtualCall() {
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
   * Tests CallGraphResult edge storage.
   */
  @Test
  void testCallGraphResult_EdgeStorage() {
    MethodSignature source = new MethodSignature("com/example/A", "foo", "()V");
    MethodSignature target = new MethodSignature("com/example/B", "bar", "()V");

    CallGraphResult.Edge edge = new CallGraphResult.Edge(source, target);

    assertThat(edge.getSource()).isEqualTo(source);
    assertThat(edge.getTarget()).isEqualTo(target);
    assertThat(edge.toString()).contains("com/example/A.foo:()V");
    assertThat(edge.toString()).contains("com/example/B.bar:()V");
  }

  /**
   * Tests CallGraphResult edge equality.
   */
  @Test
  void testCallGraphResult_EdgeEquality() {
    MethodSignature source = new MethodSignature("com/example/A", "foo", "()V");
    MethodSignature target = new MethodSignature("com/example/B", "bar", "()V");

    CallGraphResult.Edge edge1 = new CallGraphResult.Edge(source, target);
    CallGraphResult.Edge edge2 = new CallGraphResult.Edge(source, target);

    assertThat(edge1).isEqualTo(edge2);
    assertThat(edge1.hashCode()).isEqualTo(edge2.hashCode());
  }

  /**
   * Tests JdkLocator Java version detection.
   */
  @Test
  void testJdkLocator_JavaVersion() {
    int version = JdkLocator.getJavaVersion();
    assertThat(version).isGreaterThanOrEqualTo(11);

    // We're running on Java 11+, so should return true
    assertThat(JdkLocator.isJava9OrLater()).isTrue();
  }

  /**
   * Tests ClassLoaderType enum values.
   */
  @Test
  void testClassLoaderType_Values() {
    assertThat(ClassLoaderType.values()).hasSize(3);
    assertThat(ClassLoaderType.valueOf("PRIMORDIAL")).isEqualTo(ClassLoaderType.PRIMORDIAL);
    assertThat(ClassLoaderType.valueOf("EXTENSION")).isEqualTo(ClassLoaderType.EXTENSION);
    assertThat(ClassLoaderType.valueOf("APPLICATION")).isEqualTo(ClassLoaderType.APPLICATION);
  }
}
