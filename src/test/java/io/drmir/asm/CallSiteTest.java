package io.drmir.asm;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for CallSite class.
 */
class CallSiteTest {

  /**
   * Unit test: Tests CallSite properties for different opcodes.
   * Expects correct identification of static, virtual, special, and interface calls.
   */
  @Test
  void testOpcodeTypes() {
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
   * Unit test: Tests CallSite toMethodSignature conversion.
   * Expects correct MethodSignature with same owner, name, and descriptor.
   */
  @Test
  void testToMethodSignature() {
    CallSite callSite = new CallSite(Opcodes.INVOKEVIRTUAL, "java/lang/String", "length", "()I", false);
    MethodSignature sig = callSite.toMethodSignature();

    assertThat(sig.getOwner()).isEqualTo("java/lang/String");
    assertThat(sig.getName()).isEqualTo("length");
    assertThat(sig.getDescriptor()).isEqualTo("()I");
  }

  /**
   * Unit test: Tests CallSite toString includes opcode name.
   * Expects string representation to contain opcode and method info.
   */
  @Test
  void testToString() {
    CallSite callSite = new CallSite(Opcodes.INVOKEVIRTUAL, "java/lang/String", "length", "()I", false);
    assertThat(callSite.toString()).contains("INVOKEVIRTUAL");
    assertThat(callSite.toString()).contains("java/lang/String");
    assertThat(callSite.toString()).contains("length");
  }
}
