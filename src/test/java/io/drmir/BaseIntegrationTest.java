package io.drmir;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;

import java.io.File;

public abstract class BaseIntegrationTest {

  protected static File slf4jJar;
  protected static File mockitoJar;
  protected static File mockitoDeps;

  protected File outputFile;

  @BeforeAll
  static void setupTestJars() {
    slf4jJar = TestUtils.getTestJar("slf4j-api-2.0.17.jar");
    mockitoJar = TestUtils.getTestJar("mockito-core-5.21.0.jar");
    mockitoDeps = TestUtils.getTestDepsDir();

    // Verify test JARs exist
    if (!slf4jJar.exists()) {
      throw new RuntimeException("slf4j test JAR not found");
    }
    if (!mockitoJar.exists()) {
      throw new RuntimeException("mockito test JAR not found");
    }
    if (!mockitoDeps.exists() || !mockitoDeps.isDirectory()) {
      throw new RuntimeException("mockito deps directory not found");
    }
  }

  @AfterEach
  void cleanupOutputFiles() {
    // Clean up output file created during test
    TestUtils.cleanupFile(outputFile);

    // Clean up default output if exists
    TestUtils.cleanupFile(new File("callgraph.csv"));
  }
}
