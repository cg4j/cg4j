package net.cg4j;

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

  /**
   * Integration test: Analyzes simple library (slf4j) without Java runtime classes.
   * Expects 611±6 edges, no RT classes, and only org/slf4j/* source prefixes.
   */
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

  /**
   * Integration test: Analyzes simple library (slf4j) including Java runtime classes.
   * Expects 2,221±22 edges, RT classes present, more edges than no-RT version.
   */
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

  /**
   * Integration test: Analyzes library with dependencies (OkHttp) without Java runtime.
   * Expects 6,902±69 edges, no RT classes, sources from Application and Extension loaders.
   */
  @Test
  void testAppWithDeps_NoRT_OkHttp() throws IOException {
    outputFile = TestUtils.createTempOutputFile();

    int exitCode = TestUtils.runMain(
        okhttpJar.getPath(),
        "-d", okhttpDeps.getPath(),
        "-o", outputFile.getPath(),
        "--include-rt=false"
    );

    assertThat(exitCode).isEqualTo(0);
    assertThat(outputFile).exists();

    // Edge count: 6,902 ±1% = 6,902±69
    TestUtils.assertEdgeCount(outputFile, 6902, 1.0);

    // No RT classes
    assertThat(TestUtils.hasRTClasses(outputFile)).isFalse();

    // Sources include Application and Extension loaders
    Set<String> prefixes = TestUtils.getSourcePrefixes(outputFile);
    assertThat(prefixes).anyMatch(p -> p.startsWith("okhttp3/"));      // Application
    assertThat(prefixes).anyMatch(p -> p.startsWith("okio/"));         // Extension
    assertThat(prefixes).anyMatch(p -> p.startsWith("kotlin/"));       // Extension
  }

  /**
   * Integration test: Analyzes library with dependencies (OkHttp) including Java runtime.
   * Expects 13,319±133 edges, RT classes present, sources from all loaders.
   */
  @Test
  void testAppWithDeps_WithRT_OkHttp() throws IOException {
    outputFile = TestUtils.createTempOutputFile();

    int exitCode = TestUtils.runMain(
        okhttpJar.getPath(),
        "-d", okhttpDeps.getPath(),
        "-o", outputFile.getPath(),
        "--include-rt=true"
    );

    assertThat(exitCode).isEqualTo(0);
    assertThat(outputFile).exists();

    // Edge count: 13,319 ±1% = 13,319±133
    TestUtils.assertEdgeCount(outputFile, 13319, 1.0);

    // Has RT classes
    assertThat(TestUtils.hasRTClasses(outputFile)).isTrue();

    // Sources include loaders
    Set<String> prefixes = TestUtils.getSourcePrefixes(outputFile);
    assertThat(prefixes).anyMatch(p -> p.startsWith("okhttp3/"));  // Application
    assertThat(prefixes).anyMatch(p -> p.startsWith("java/"));                  // Primordial
  }

  /**
   * Integration test: Tests error handling for non-existent JAR file.
   * Expects exit code 1 (error) when JAR file does not exist.
   */
  @Test
  void testInvalidJarPath_ReturnsErrorCode() {
    int exitCode = TestUtils.runMain("nonexistent.jar");
    assertThat(exitCode).isEqualTo(1);
  }

  /**
   * Integration test: Tests custom output file path functionality.
   * Expects file created at specified path with correct name.
   */
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

  /**
   * Integration test: Tests analysis without specifying dependencies flag.
   * Expects successful execution with 611±6 edges when no -d flag provided.
   */
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

  /**
   * Integration test: Tests handling of empty dependencies directory.
   * Expects successful execution (exit code 0) when deps directory is empty.
   */
  @Test
  void testEmptyDepsDirectory_HandlesGracefully() throws IOException {
    File emptyDir = Files.createTempDirectory("empty-deps").toFile();
    emptyDir.deleteOnExit();

    outputFile = TestUtils.createTempOutputFile();

    int exitCode = TestUtils.runMain(
        okhttpJar.getPath(),
        "-d", emptyDir.getPath(),
        "-o", outputFile.getPath(),
        "--include-rt=false"
    );

    assertThat(exitCode).isEqualTo(0);
    // Should work but without dependency benefits
  }

  /**
   * Integration test: Tests default value of --include-rt flag.
   * Expects RT classes included by default (2,221±22 edges) when flag not specified.
   */
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

  /**
   * Integration test: Tests file overwriting behavior.
   * Expects existing output file to be overwritten with valid CSV content.
   */
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

  /**
   * Integration test: Tests CSV output format validation.
   * Expects CSV file with correct header: "source_method,target_method".
   */
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

  /**
   * Integration test: Tests entry points are only from Application ClassLoader.
   * Expects boot edges (if any) to originate only from application code (okhttp3/*).
   */
  @Test
  void testEntryPointsNeverFromExtension_OkHttp() throws IOException {
    outputFile = TestUtils.createTempOutputFile();

    int exitCode = TestUtils.runMain(
        okhttpJar.getPath(),
        "-d", okhttpDeps.getPath(),
        "-o", outputFile.getPath(),
        "--include-rt=false"
    );

    assertThat(exitCode).isEqualTo(0);

    // Verify entry points are from Application loader
    // (Entry points should only be from Application loader - jackson-databind)
    List<String[]> edges = TestUtils.parseCSV(outputFile);

    for (String[] edge : edges) {
      if (edge[0].equals("<boot>")) {
        // If there are boot edges (shouldn't be many), verify they're from jackson
        assertThat(edge[1]).matches("okhttp3//.*");
      }
    }
  }
}
