package net.cg4j.asm;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ClassInfo class.
 */
class ClassInfoTest {

  /**
   * Unit test: Tests ClassInfo properties and getters.
   * Expects correct name, superclass, interfaces, methods, loader type, and access flags.
   */
  @Test
  void testProperties() {
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
   * Unit test: Tests ClassInfo method lookup.
   * Expects hasMethod and getMethod to correctly find declared methods.
   */
  @Test
  void testHasMethod() {
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
   * Unit test: Tests ClassInfo for interface class.
   * Expects correct interface and abstract flags.
   */
  @Test
  void testInterfaceClass() {
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
}
