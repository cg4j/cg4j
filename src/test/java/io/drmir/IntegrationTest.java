package io.drmir;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IntegrationTest extends BaseIntegrationTest {

  // Scenario 1: App Only (No RT)
  @Test
  void testAppOnly_NoRT_Slf4j() throws IOException {
    outputFile = TestUtils.createTempOutputFile();

    int exitCode = TestUtils.runMain(
        slf4jJar.getPath(),
        "-o", outputFile.getPath(),
        "--include-rt=false"
    );

    assertThat(exitCode).isEqualTo(0);
    assertThat(outputFile).exists();

    // Edge count: 611 ±1% = 611±6
    TestUtils.assertEdgeCount(outputFile, 611, 1.0);

    // No RT classes
    assertThat(TestUtils.hasRTClasses(outputFile)).isFalse();

    // Only slf4j sources
    assertThat(TestUtils.getSourcePrefixes(outputFile))
        .allMatch(prefix -> prefix.startsWith("org/slf4j"));
  }

  // Scenario 2: App Only (With RT)
  @Test
  void testAppOnly_WithRT_Slf4j() throws IOException {
    outputFile = TestUtils.createTempOutputFile();

    int exitCode = TestUtils.runMain(
        slf4jJar.getPath(),
        "-o", outputFile.getPath(),
        "--include-rt=true"
    );

    assertThat(exitCode).isEqualTo(0);
    assertThat(outputFile).exists();

    // Edge count: 2,221 ±1% = 2,221±22
    TestUtils.assertEdgeCount(outputFile, 2221, 1.0);

    // Has RT classes
    assertThat(TestUtils.hasRTClasses(outputFile)).isTrue();

    // More edges than no-RT version
    assertThat(TestUtils.countEdges(outputFile)).isGreaterThan(611);
  }

  // Scenario 3: App + Deps (No RT)
  @Test
  void testAppWithDeps_NoRT_Mockito() throws IOException {
    outputFile = TestUtils.createTempOutputFile();

    int exitCode = TestUtils.runMain(
        mockitoJar.getPath(),
        "-d", mockitoDeps.getPath(),
        "-o", outputFile.getPath(),
        "--include-rt=false"
    );

    assertThat(exitCode).isEqualTo(0);
    assertThat(outputFile).exists();

    // Edge count: 9,882 ±1% = 9,882±99
    TestUtils.assertEdgeCount(outputFile, 9882, 1.0);

    // No RT classes
    assertThat(TestUtils.hasRTClasses(outputFile)).isFalse();

    // Sources include Application and Extension loaders
    Set<String> prefixes = TestUtils.getSourcePrefixes(outputFile);
    assertThat(prefixes).anyMatch(p -> p.startsWith("org/mockito"));      // Application
    assertThat(prefixes).anyMatch(p -> p.startsWith("net/bytebuddy"));   // Extension
    assertThat(prefixes).anyMatch(p -> p.startsWith("org/objenesis"));   // Extension
  }

  // Scenario 4: App + Deps (With RT)
  @Test
  void testAppWithDeps_WithRT_Mockito() throws IOException {
    outputFile = TestUtils.createTempOutputFile();

    int exitCode = TestUtils.runMain(
        mockitoJar.getPath(),
        "-d", mockitoDeps.getPath(),
        "-o", outputFile.getPath(),
        "--include-rt=true"
    );

    assertThat(exitCode).isEqualTo(0);
    assertThat(outputFile).exists();

    // Edge count: More than no-RT, expect 15,000+
    int edgeCount = TestUtils.countEdges(outputFile);
    assertThat(edgeCount).isGreaterThan(9882);
    assertThat(edgeCount).isGreaterThan(15000);

    // Has RT classes
    assertThat(TestUtils.hasRTClasses(outputFile)).isTrue();

    // Sources include all three loaders
    Set<String> prefixes = TestUtils.getSourcePrefixes(outputFile);
    assertThat(prefixes).anyMatch(p -> p.startsWith("org/mockito"));      // Application
    assertThat(prefixes).anyMatch(p -> p.startsWith("net/bytebuddy"));   // Extension
    assertThat(prefixes).anyMatch(p -> p.startsWith("java/"));            // Primordial
  }

  @Test
  void testInvalidJarPath_ReturnsErrorCode() {
    int exitCode = TestUtils.runMain("nonexistent.jar");
    assertThat(exitCode).isEqualTo(1);
  }

  @Test
  void testCustomOutputFile_CreatesCorrectFile() throws IOException {
    outputFile = new File("custom-test-output.csv");

    int exitCode = TestUtils.runMain(
        slf4jJar.getPath(),
        "-o", outputFile.getPath(),
        "--include-rt=false"
    );

    assertThat(exitCode).isEqualTo(0);
    assertThat(outputFile).exists();
    assertThat(outputFile.getName()).isEqualTo("custom-test-output.csv");
  }

  @Test
  void testNoDepsFlag_WorksWithoutDependencies() throws IOException {
    outputFile = TestUtils.createTempOutputFile();

    int exitCode = TestUtils.runMain(
        slf4jJar.getPath(),
        "-o", outputFile.getPath(),
        "--include-rt=false"
        // No -d flag
    );

    assertThat(exitCode).isEqualTo(0);
    TestUtils.assertEdgeCount(outputFile, 611, 1.0);
  }

  @Test
  void testEmptyDepsDirectory_HandlesGracefully() throws IOException {
    File emptyDir = Files.createTempDirectory("empty-deps").toFile();
    emptyDir.deleteOnExit();

    outputFile = TestUtils.createTempOutputFile();

    int exitCode = TestUtils.runMain(
        mockitoJar.getPath(),
        "-d", emptyDir.getPath(),
        "-o", outputFile.getPath(),
        "--include-rt=false"
    );

    assertThat(exitCode).isEqualTo(0);
    // Should work but without dependency benefits
  }

  @Test
  void testDefaultIncludeRt_IsTrue() throws IOException {
    outputFile = TestUtils.createTempOutputFile();

    // Don't specify --include-rt flag
    int exitCode = TestUtils.runMain(
        slf4jJar.getPath(),
        "-o", outputFile.getPath()
    );

    assertThat(exitCode).isEqualTo(0);

    // Should have same edge count as explicit --include-rt=true
    TestUtils.assertEdgeCount(outputFile, 2221, 1.0);
    assertThat(TestUtils.hasRTClasses(outputFile)).isTrue();
  }

  @Test
  void testOutputFileAlreadyExists_Overwrites() throws IOException {
    outputFile = TestUtils.createTempOutputFile();

    // Write dummy content
    Files.write(outputFile.toPath(), "dummy content".getBytes());
    long originalSize = outputFile.length();

    int exitCode = TestUtils.runMain(
        slf4jJar.getPath(),
        "-o", outputFile.getPath(),
        "--include-rt=false"
    );

    assertThat(exitCode).isEqualTo(0);
    assertThat(outputFile.length()).isNotEqualTo(originalSize);

    // Verify it's valid CSV
    List<String[]> edges = TestUtils.parseCSV(outputFile);
    assertThat(edges).isNotEmpty();
  }

  @Test
  void testCSVFormat_HasCorrectHeader() throws IOException {
    outputFile = TestUtils.createTempOutputFile();

    int exitCode = TestUtils.runMain(
        slf4jJar.getPath(),
        "-o", outputFile.getPath(),
        "--include-rt=false"
    );

    assertThat(exitCode).isEqualTo(0);

    String firstLine = Files.lines(outputFile.toPath()).findFirst().orElse("");
    assertThat(firstLine).isEqualTo("source_method,target_method");
  }

  @Test
  void testEntryPointsNeverFromExtension_Mockito() throws IOException {
    outputFile = TestUtils.createTempOutputFile();

    int exitCode = TestUtils.runMain(
        mockitoJar.getPath(),
        "-d", mockitoDeps.getPath(),
        "-o", outputFile.getPath(),
        "--include-rt=false"
    );

    assertThat(exitCode).isEqualTo(0);

    // Verify no <boot> -> Extension loader edges
    // (Entry points should only be from Application loader - mockito)
    List<String[]> edges = TestUtils.parseCSV(outputFile);

    for (String[] edge : edges) {
      if (edge[0].equals("<boot>")) {
        // If there are boot edges (shouldn't be), verify they're not to Extension
        assertThat(edge[1]).doesNotStartWith("net/bytebuddy");
        assertThat(edge[1]).doesNotStartWith("org/objenesis");
      }
    }
  }
}
