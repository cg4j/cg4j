package net.cg4j.asm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for JdkLocator class.
 */
class JdkLocatorTest {

  /**
   * Unit test: Tests JdkLocator Java version detection.
   * Expects version to be at least 11 and isJava9OrLater to return true.
   */
  @Test
  void testJavaVersion() {
    int version = JdkLocator.getJavaVersion();
    assertThat(version).isGreaterThanOrEqualTo(11);

    // We're running on Java 11+, so should return true
    assertThat(JdkLocator.isJava9OrLater()).isTrue();
  }
}
