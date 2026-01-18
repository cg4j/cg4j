package io.drmir;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;

import java.io.File;

public abstract class BaseIntegrationTest {

  protected static File slf4jJar;
  protected static File okhttpJar;
  protected static File okhttpDeps;

  protected File outputFile;

  @BeforeAll
  static void setupTestJars() {
    slf4jJar = TestUtils.getTestJar("slf4j-api-2.0.17.jar");
    okhttpJar = TestUtils.getTestJar("okhttp-jvm-5.3.2.jar");
    okhttpDeps = TestUtils.getTestDepsDir();

    // Verify test JARs exist
    if (!slf4jJar.exists()) {
      throw new RuntimeException("slf4j test JAR not found");
    }
    if (!okhttpJar.exists()) {
      throw new RuntimeException("okhttp test JAR not found");
    }
    if (!okhttpDeps.exists() || !okhttpDeps.isDirectory()) {
      throw new RuntimeException("okhttp deps directory not found");
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
