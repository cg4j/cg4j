package net.cg4j;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the RTA (Rapid Type Analysis) algorithm.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RtaIntegrationTest extends BaseIntegrationTest {

  /**
   * Integration test: Analyzes simple library (slf4j) without Java runtime classes using RTA.
   * Expects edges, validates output format.
   */
  @Test
  void testRtaEngine_NoRT_Slf4j() throws IOException {
    outputFile = TestUtils.createTempOutputFile();

    int exitCode = TestUtils.runMain(
        slf4jJar.getPath(),
        "-o", outputFile.getPath(),
        "--engine=asm",
        "--algorithm=rta",
        "--include-rt=false"
    );

    assertThat(exitCode).isEqualTo(0);
    assertThat(outputFile).exists();

    // RTA should produce edges
    int edgeCount = TestUtils.countEdges(outputFile);
    assertThat(edgeCount).isGreaterThan(0);

    // No RT classes
    assertThat(TestUtils.hasRTClasses(outputFile)).isFalse();

    // Only slf4j sources
    assertThat(TestUtils.getSourcePrefixes(outputFile))
        .allMatch(prefix -> prefix.startsWith("org/slf4j") || prefix.equals("<boot"));
  }

  /**
   * Integration test: Analyzes simple library (slf4j) including Java runtime classes using RTA.
   * Expects more edges when RT is included.
   */
  @Test
  void testRtaEngine_WithRT_Slf4j() throws IOException {
    outputFile = TestUtils.createTempOutputFile();

    int exitCode = TestUtils.runMain(
        slf4jJar.getPath(),
        "-o", outputFile.getPath(),
        "--engine=asm",
        "--algorithm=rta",
        "--include-rt=true"
    );

    assertThat(exitCode).isEqualTo(0);
    assertThat(outputFile).exists();

    // With RT should have more edges
    int edgeCount = TestUtils.countEdges(outputFile);
    assertThat(edgeCount).isGreaterThan(1000);

    // Has RT classes
    assertThat(TestUtils.hasRTClasses(outputFile)).isTrue();
  }

  /**
   * Integration test: Analyzes library with dependencies (OkHttp) without Java runtime using RTA.
   * Expects sources from Application and Extension loaders.
   */
  @Test
  void testRtaEngine_WithDeps_OkHttp() throws IOException {
    outputFile = TestUtils.createTempOutputFile();

    int exitCode = TestUtils.runMain(
        okhttpJar.getPath(),
        "-d", okhttpDeps.getPath(),
        "-o", outputFile.getPath(),
        "--engine=asm",
        "--algorithm=rta",
        "--include-rt=false"
    );

    assertThat(exitCode).isEqualTo(0);
    assertThat(outputFile).exists();

    // Should have significant edges
    int edgeCount = TestUtils.countEdges(outputFile);
    assertThat(edgeCount).isGreaterThan(1000);

    // No RT classes
    assertThat(TestUtils.hasRTClasses(outputFile)).isFalse();

    // Sources include Application and Extension loaders
    Set<String> prefixes = TestUtils.getSourcePrefixes(outputFile);
    assertThat(prefixes).anyMatch(p -> p.startsWith("okhttp3/"));
  }

  /**
   * Integration test: Verifies RTA produces fewer edges than CHA.
   * RTA should be more precise due to type instantiation filtering.
   */
  @Test
  void testRtaVsCha_RtaHasFewerEdges() throws IOException {
    File chaOutput = TestUtils.createTempOutputFile();
    File rtaOutput = TestUtils.createTempOutputFile();

    try {
      // Run CHA
      TestUtils.runMain(
          slf4jJar.getPath(),
          "-o", chaOutput.getPath(),
          "--engine=asm",
          "--algorithm=cha",
          "--include-rt=false"
      );

      // Run RTA
      TestUtils.runMain(
          slf4jJar.getPath(),
          "-o", rtaOutput.getPath(),
          "--engine=asm",
          "--algorithm=rta",
          "--include-rt=false"
      );

      int chaEdges = TestUtils.countEdges(chaOutput);
      int rtaEdges = TestUtils.countEdges(rtaOutput);

      // RTA should produce fewer or equal edges than CHA
      assertThat(rtaEdges).isLessThanOrEqualTo(chaEdges);
    } finally {
      TestUtils.cleanupFile(chaOutput);
      TestUtils.cleanupFile(rtaOutput);
    }
  }

  /**
   * Integration test: Verifies RTA CSV output format.
   * Expects CSV file with correct header: "source_method,target_method".
   */
  @Test
  void testRtaEngine_CSVFormat_HasCorrectHeader() throws IOException {
    outputFile = TestUtils.createTempOutputFile();

    int exitCode = TestUtils.runMain(
        slf4jJar.getPath(),
        "-o", outputFile.getPath(),
        "--engine=asm",
        "--algorithm=rta",
        "--include-rt=false"
    );

    assertThat(exitCode).isEqualTo(0);

    String firstLine = Files.lines(outputFile.toPath()).findFirst().orElse("");
    assertThat(firstLine).isEqualTo("source_method,target_method");
  }

  /**
   * Integration test: Tests invalid algorithm option.
   * Expects exit code 1 (error) when invalid algorithm is specified.
   */
  @Test
  void testInvalidAlgorithm_ReturnsErrorCode() {
    int exitCode = TestUtils.runMain(
        slf4jJar.getPath(),
        "--engine=asm",
        "--algorithm=invalid"
    );
    assertThat(exitCode).isEqualTo(1);
  }

  /**
   * Integration test: Verifies default algorithm is CHA when not specified.
   * Expects same behavior as explicit --algorithm=cha.
   */
  @Test
  void testDefaultAlgorithm_IsCha() throws IOException {
    File chaOutput = TestUtils.createTempOutputFile();
    File defaultOutput = TestUtils.createTempOutputFile();

    try {
      // Run with explicit cha
      TestUtils.runMain(
          slf4jJar.getPath(),
          "-o", chaOutput.getPath(),
          "--engine=asm",
          "--algorithm=cha",
          "--include-rt=false"
      );

      // Run with default (no --algorithm)
      TestUtils.runMain(
          slf4jJar.getPath(),
          "-o", defaultOutput.getPath(),
          "--engine=asm",
          "--include-rt=false"
      );

      // Both should have same edge count
      int chaEdges = TestUtils.countEdges(chaOutput);
      int defaultEdges = TestUtils.countEdges(defaultOutput);
      assertThat(defaultEdges).isEqualTo(chaEdges);
    } finally {
      TestUtils.cleanupFile(chaOutput);
      TestUtils.cleanupFile(defaultOutput);
    }
  }
}
