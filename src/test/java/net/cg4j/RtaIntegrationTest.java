package net.cg4j;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
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
        "--include-rt=false"
    );

    assertThat(exitCode).isEqualTo(0);
    assertThat(outputFile).exists();

    // RTA should produce edges
    int edgeCount = TestUtils.countEdges(outputFile);
    assertThat(edgeCount).isGreaterThan(0);

    // No RT classes
    assertThat(TestUtils.hasRTClasses(outputFile)).isFalse();

    // Only slf4j sources (and synthetic lambda classes derived from them)
    assertThat(TestUtils.getSourcePrefixes(outputFile))
        .allMatch(prefix -> prefix.startsWith("org/slf4j")
            || prefix.equals("<boot")
            || prefix.startsWith("wala/lambda$org$slf4j"));
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
        "--include-rt=false"
    );

    assertThat(exitCode).isEqualTo(0);

    String firstLine = Files.lines(outputFile.toPath()).findFirst().orElse("");
    assertThat(firstLine).isEqualTo("source_method,target_method");
  }

  /**
   * Integration test: Verifies fake-root edges are preserved for non-RT targets when RT is excluded.
   * Expects {@code <boot>} source edges to remain for application/extension reachability.
   */
  @Test
  void testRtaEngine_KeepsBootEdgesWhenRtExcluded() throws IOException {
    outputFile = TestUtils.createTempOutputFile();

    int exitCode = TestUtils.runMain(
        okhttpJar.getPath(),
        "-d", okhttpDeps.getPath(),
        "-o", outputFile.getPath(),
        "--engine=asm",
        "--include-rt=false"
    );

    assertThat(exitCode).isEqualTo(0);

    List<String[]> edges = TestUtils.parseCSV(outputFile);
    assertThat(edges)
        .filteredOn(e -> e[0].equals("<boot>"))
        .isNotEmpty()
        .allMatch(e -> !e[1].startsWith("java/"));
  }
}
