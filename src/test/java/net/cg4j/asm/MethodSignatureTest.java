package net.cg4j.asm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;

/** Unit tests for MethodSignature class. */
class MethodSignatureTest {

  /**
   * Unit test: Tests MethodSignature creation and URI formatting. Expects correct getters and URI
   * format.
   */
  @Test
  void testToUri() {
    MethodSignature sig = new MethodSignature("java/lang/String", "length", "()I");

    assertThat(sig.getOwner()).isEqualTo("java/lang/String");
    assertThat(sig.getName()).isEqualTo("length");
    assertThat(sig.getDescriptor()).isEqualTo("()I");
    assertThat(sig.toUri()).isEqualTo("java/lang/String.length:()I");
    assertThat(sig.toString()).isEqualTo("java/lang/String.length:()I");
  }

  /**
   * Unit test: Tests MethodSignature equality and hashCode. Expects equal signatures to be equal
   * and have same hashCode.
   */
  @Test
  void testEquality() {
    MethodSignature sig1 = new MethodSignature("java/lang/String", "length", "()I");
    MethodSignature sig2 = new MethodSignature("java/lang/String", "length", "()I");
    MethodSignature sig3 = new MethodSignature("java/lang/String", "charAt", "(I)C");

    assertThat(sig1).isEqualTo(sig2);
    assertThat(sig1).isNotEqualTo(sig3);
    assertThat(sig1.hashCode()).isEqualTo(sig2.hashCode());
  }

  /**
   * Unit test: Tests MethodSignature access flag methods. Expects correct isPublic, isAbstract,
   * isStatic behavior.
   */
  @Test
  void testAccessFlags() {
    MethodSignature publicMethod =
        new MethodSignature("com/example/MyClass", "publicMethod", "()V", Opcodes.ACC_PUBLIC);
    MethodSignature privateMethod =
        new MethodSignature("com/example/MyClass", "privateMethod", "()V", Opcodes.ACC_PRIVATE);
    MethodSignature abstractMethod =
        new MethodSignature(
            "com/example/MyClass",
            "abstractMethod",
            "()V",
            Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT);
    MethodSignature staticMethod =
        new MethodSignature(
            "com/example/MyClass", "staticMethod", "()V", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC);
    MethodSignature noFlagsMethod =
        new MethodSignature("com/example/MyClass", "packageMethod", "()V");

    assertThat(publicMethod.isPublic()).isTrue();
    assertThat(publicMethod.isAbstract()).isFalse();
    assertThat(publicMethod.isStatic()).isFalse();

    assertThat(privateMethod.isPublic()).isFalse();
    assertThat(privateMethod.isAbstract()).isFalse();

    assertThat(abstractMethod.isPublic()).isTrue();
    assertThat(abstractMethod.isAbstract()).isTrue();

    assertThat(staticMethod.isPublic()).isTrue();
    assertThat(staticMethod.isStatic()).isTrue();

    // Method with no flags (default access)
    assertThat(noFlagsMethod.isPublic()).isFalse();
    assertThat(noFlagsMethod.isAbstract()).isFalse();
    assertThat(noFlagsMethod.getAccess()).isEqualTo(0);
  }

  /**
   * Unit test: Tests boot method URI format matches WALA's convention. Expects synthetic boot
   * method to return just "&lt;boot&gt;" instead of full URI.
   */
  @Test
  void testBootMethodUri_MatchesWalaConvention() {
    MethodSignature bootMethod = new MethodSignature("<boot>", "fakeRoot", "()V");

    // Boot method should return just "<boot>" to match WALA's convention
    assertThat(bootMethod.toUri()).isEqualTo("<boot>");
    assertThat(bootMethod.toString()).isEqualTo("<boot>");

    // Regular methods should still use full URI format
    MethodSignature regularMethod = new MethodSignature("com/example/MyClass", "method", "()V");
    assertThat(regularMethod.toUri()).isEqualTo("com/example/MyClass.method:()V");
  }
}
