package io.drmir.asm;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for CallSiteExtractor class.
 */
class CallSiteExtractorTest {

  /**
   * Unit test: Tests CallSiteExtractor tracking of NEW instructions.
   * Expects NEW instructions to be tracked as instantiated types.
   */
  @Test
  void testTracksInstantiatedTypes() {
    CallSiteExtractor extractor = new CallSiteExtractor();

    // Simulate visiting NEW instructions
    extractor.visitTypeInsn(Opcodes.NEW, "com/example/MyClass");
    extractor.visitTypeInsn(Opcodes.NEW, "com/example/AnotherClass");
    extractor.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/String"); // Should not be tracked

    Set<String> instantiatedTypes = extractor.getInstantiatedTypes();

    assertThat(instantiatedTypes).hasSize(2);
    assertThat(instantiatedTypes).contains("com/example/MyClass", "com/example/AnotherClass");
    assertThat(instantiatedTypes).doesNotContain("java/lang/String");
  }
}
