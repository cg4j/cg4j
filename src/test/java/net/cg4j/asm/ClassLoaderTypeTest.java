package net.cg4j.asm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Unit tests for ClassLoaderType enum. */
class ClassLoaderTypeTest {

  /**
   * Unit test: Tests ClassLoaderType enum values. Expects correct enum values and valueOf behavior.
   */
  @Test
  void testValues() {
    assertThat(ClassLoaderType.values()).hasSize(3);
    assertThat(ClassLoaderType.valueOf("PRIMORDIAL")).isEqualTo(ClassLoaderType.PRIMORDIAL);
    assertThat(ClassLoaderType.valueOf("EXTENSION")).isEqualTo(ClassLoaderType.EXTENSION);
    assertThat(ClassLoaderType.valueOf("APPLICATION")).isEqualTo(ClassLoaderType.APPLICATION);
  }
}
