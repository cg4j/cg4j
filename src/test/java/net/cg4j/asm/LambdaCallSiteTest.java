package net.cg4j.asm;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for LambdaCallSite class.
 */
class LambdaCallSiteTest {

  /**
   * Unit test: Tests constructor and all getter methods.
   * Expects all fields to be returned correctly.
   */
  @Test
  void testConstructorAndGetters() {
    LambdaCallSite site = new LambdaCallSite(
        "invoke",
        "(Ljava/lang/Object;)Ljava/lang/Object;",
        "()Lkotlin/jvm/functions/Function1;",
        "com/example/MyClass",
        "lambda$method$0",
        "(Ljava/lang/String;)Ljava/lang/String;",
        Opcodes.H_INVOKESTATIC
    );

    assertThat(site.getSamMethodName()).isEqualTo("invoke");
    assertThat(site.getSamDescriptor()).isEqualTo("(Ljava/lang/Object;)Ljava/lang/Object;");
    assertThat(site.getIndyDescriptor()).isEqualTo("()Lkotlin/jvm/functions/Function1;");
    assertThat(site.getImplOwner()).isEqualTo("com/example/MyClass");
    assertThat(site.getImplName()).isEqualTo("lambda$method$0");
    assertThat(site.getImplDescriptor()).isEqualTo("(Ljava/lang/String;)Ljava/lang/String;");
    assertThat(site.getImplTag()).isEqualTo(Opcodes.H_INVOKESTATIC);
  }

  /**
   * Unit test: Tests toString format includes SAM and impl information.
   * Expects a readable string with both SAM and impl details.
   */
  @Test
  void testToString() {
    LambdaCallSite site = new LambdaCallSite(
        "run",
        "()V",
        "()Ljava/lang/Runnable;",
        "com/example/Foo",
        "lambda$bar$0",
        "()V",
        Opcodes.H_INVOKESTATIC
    );

    String result = site.toString();
    assertThat(result).contains("run");
    assertThat(result).contains("com/example/Foo");
    assertThat(result).contains("lambda$bar$0");
  }
}
