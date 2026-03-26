package net.cg4j;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the ASM-based call graph engine.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AsmIntegrationTest extends BaseIntegrationTest {

  /**
   * Integration test: Analyzes simple library (slf4j) without Java runtime classes using ASM.
   * RTA is more precise than CHA, producing fewer edges.
   */
  @Test
  void testAsmEngine_NoRT_Slf4j() throws IOException {
    outputFile = TestUtils.createTempOutputFile();

    int exitCode = TestUtils.runMain(
        slf4jJar.getPath(),
        "-o", outputFile.getPath(),
        "--engine=asm",
        "--include-rt=false"
    );

    assertThat(exitCode).isEqualTo(0);
    assertThat(outputFile).exists();

    // RTA produces edges based on instantiated types
    int edgeCount = TestUtils.countEdges(outputFile);
    assertThat(edgeCount).isGreaterThan(100);

    // No RT classes
    assertThat(TestUtils.hasRTClasses(outputFile)).isFalse();

    // Only slf4j sources (and synthetic lambda classes derived from them)
    assertThat(TestUtils.getSourcePrefixes(outputFile))
        .allMatch(prefix -> prefix.startsWith("org/slf4j")
            || prefix.equals("<boot")
            || prefix.startsWith("wala/lambda$org$slf4j"));
  }

  /**
   * Integration test: Analyzes simple library (slf4j) including Java runtime classes using ASM.
   * Expects more edges when RT is included.
   */
  @Test
  void testAsmEngine_WithRT_Slf4j() throws IOException {
    outputFile = TestUtils.createTempOutputFile();

    int exitCode = TestUtils.runMain(
        slf4jJar.getPath(),
        "-o", outputFile.getPath(),
        "--engine=asm",
        "--include-rt=true"
    );

    assertThat(exitCode).isEqualTo(0);
    assertThat(outputFile).exists();

    // With RT should have more edges than without RT
    int edgeCount = TestUtils.countEdges(outputFile);
    assertThat(edgeCount).isGreaterThan(1000);

    // Has RT classes
    assertThat(TestUtils.hasRTClasses(outputFile)).isTrue();
  }

  /**
   * Integration test: Analyzes library with dependencies (OkHttp) without Java runtime using ASM.
   * Expects sources from Application and Extension loaders.
   */
  @Test
  void testAsmEngine_WithDeps_NoRT_OkHttp() throws IOException {
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

    // Should have significant edges (RTA is more precise than CHA)
    int edgeCount = TestUtils.countEdges(outputFile);
    assertThat(edgeCount).isGreaterThan(1000);

    // No RT classes
    assertThat(TestUtils.hasRTClasses(outputFile)).isFalse();

    // Sources include Application and Extension loaders
    Set<String> prefixes = TestUtils.getSourcePrefixes(outputFile);
    assertThat(prefixes).anyMatch(p -> p.startsWith("okhttp3/"));
    assertThat(prefixes).anyMatch(p -> p.startsWith("okio/") || p.startsWith("kotlin/"));
  }

  /**
   * Integration test: Verifies ASM fully covers WALA edges for slf4j without RT classes.
   * Expects zero missing WALA edges in the ASM output.
   */
  @Test
  void testAsmEngine_CoversWalaEdges_NoRT_Slf4j() throws IOException {
    File walaOutput = TestUtils.createTempOutputFile();
    File asmOutput = TestUtils.createTempOutputFile();

    try {
      int walaExitCode = TestUtils.runMain(
          slf4jJar.getPath(),
          "-o", walaOutput.getPath(),
          "--engine=wala",
          "--include-rt=false"
      );
      int asmExitCode = TestUtils.runMain(
          slf4jJar.getPath(),
          "-o", asmOutput.getPath(),
          "--engine=asm",
          "--include-rt=false"
      );

      assertThat(walaExitCode).isEqualTo(0);
      assertThat(asmExitCode).isEqualTo(0);
      TestUtils.assertWalaEdgeCoverage(walaOutput, asmOutput, 0.0);
    } finally {
      TestUtils.cleanupFile(walaOutput);
      TestUtils.cleanupFile(asmOutput);
    }
  }

  /**
   * Integration test: Verifies ASM covers WALA edges for slf4j with RT classes enabled.
   * Expects at most 1% of WALA edges to be missing from the ASM output.
   */
  @Test
  void testAsmEngine_CoversWalaEdges_WithRT_Slf4j() throws IOException {
    File walaOutput = TestUtils.createTempOutputFile();
    File asmOutput = TestUtils.createTempOutputFile();

    try {
      int walaExitCode = TestUtils.runMain(
          slf4jJar.getPath(),
          "-o", walaOutput.getPath(),
          "--engine=wala",
          "--include-rt=true"
      );
      int asmExitCode = TestUtils.runMain(
          slf4jJar.getPath(),
          "-o", asmOutput.getPath(),
          "--engine=asm",
          "--include-rt=true"
      );

      assertThat(walaExitCode).isEqualTo(0);
      assertThat(asmExitCode).isEqualTo(0);
      TestUtils.assertWalaEdgeCoverage(walaOutput, asmOutput, 1.0);
    } finally {
      TestUtils.cleanupFile(walaOutput);
      TestUtils.cleanupFile(asmOutput);
    }
  }

  /**
   * Integration test: Verifies ASM fully covers WALA edges for OkHttp without RT classes.
   * Expects zero missing WALA edges in the ASM output.
   */
  @Test
  void testAsmEngine_CoversWalaEdges_NoRT_OkHttp() throws IOException {
    File walaOutput = TestUtils.createTempOutputFile();
    File asmOutput = TestUtils.createTempOutputFile();

    try {
      int walaExitCode = TestUtils.runMain(
          okhttpJar.getPath(),
          "-d", okhttpDeps.getPath(),
          "-o", walaOutput.getPath(),
          "--engine=wala",
          "--include-rt=false"
      );
      int asmExitCode = TestUtils.runMain(
          okhttpJar.getPath(),
          "-d", okhttpDeps.getPath(),
          "-o", asmOutput.getPath(),
          "--engine=asm",
          "--include-rt=false"
      );

      assertThat(walaExitCode).isEqualTo(0);
      assertThat(asmExitCode).isEqualTo(0);
      TestUtils.assertWalaEdgeCoverage(walaOutput, asmOutput, 0.0);
    } finally {
      TestUtils.cleanupFile(walaOutput);
      TestUtils.cleanupFile(asmOutput);
    }
  }

  /**
   * Integration test: Verifies ASM covers WALA edges for OkHttp with RT classes enabled.
   * Expects at most 1% of WALA edges to be missing from the ASM output.
   */
  @Test
  void testAsmEngine_CoversWalaEdges_WithRT_OkHttp() throws IOException {
    File walaOutput = TestUtils.createTempOutputFile();
    File asmOutput = TestUtils.createTempOutputFile();

    try {
      int walaExitCode = TestUtils.runMain(
          okhttpJar.getPath(),
          "-d", okhttpDeps.getPath(),
          "-o", walaOutput.getPath(),
          "--engine=wala",
          "--include-rt=true"
      );
      int asmExitCode = TestUtils.runMain(
          okhttpJar.getPath(),
          "-d", okhttpDeps.getPath(),
          "-o", asmOutput.getPath(),
          "--engine=asm",
          "--include-rt=true"
      );

      assertThat(walaExitCode).isEqualTo(0);
      assertThat(asmExitCode).isEqualTo(0);
      TestUtils.assertWalaEdgeCoverage(walaOutput, asmOutput, 1.0);
    } finally {
      TestUtils.cleanupFile(walaOutput);
      TestUtils.cleanupFile(asmOutput);
    }
  }

  /**
   * Integration test: Verifies ASM CSV output format.
   * Expects CSV file with correct header: "source_method,target_method".
   */
  @Test
  void testAsmEngine_CSVFormat_HasCorrectHeader() throws IOException {
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
   * Integration test: Verifies ASM engine produces valid method URIs.
   * Expects method URIs in format: package/Class.method:descriptor
   */
  @Test
  void testAsmEngine_MethodUriFormat() throws IOException {
    outputFile = TestUtils.createTempOutputFile();

    int exitCode = TestUtils.runMain(
        slf4jJar.getPath(),
        "-o", outputFile.getPath(),
        "--engine=asm",
        "--include-rt=false"
    );

    assertThat(exitCode).isEqualTo(0);

    List<String[]> edges = TestUtils.parseCSV(outputFile);
    for (String[] edge : edges) {
      String source = edge[0];
      String target = edge[1];

      // Source should be <boot> (matching WALA's convention) or have method URI format
      if (!source.equals("<boot>")) {
        assertThat(source).matches(".*\\..*:.*");
      }

      // Target should have method URI format
      assertThat(target).matches(".*\\..*:.*");
    }
  }

  /**
   * Integration test: Verifies ASM engine produces synthetic lambda edges for OkHttp with RT.
   * Expects edges with wala/lambda$ prefix following the two-hop pattern.
   */
  @Test
  void testAsmEngine_LambdaEdges_OkHttp() throws IOException {
    outputFile = TestUtils.createTempOutputFile();

    int exitCode = TestUtils.runMain(
        okhttpJar.getPath(),
        "-d", okhttpDeps.getPath(),
        "-o", outputFile.getPath(),
        "--engine=asm",
        "--include-rt=true"
    );

    assertThat(exitCode).isEqualTo(0);

    List<String[]> edges = TestUtils.parseCSV(outputFile);

    // Hop 1: Some caller -> wala/lambda$...
    boolean hasLambdaTarget = edges.stream()
        .anyMatch(e -> e[1].startsWith("wala/lambda$"));
    assertThat(hasLambdaTarget)
        .as("Expected edges targeting synthetic lambda classes")
        .isTrue();

    // Hop 2: wala/lambda$... -> impl method
    boolean hasLambdaSource = edges.stream()
        .anyMatch(e -> e[0].startsWith("wala/lambda$"));
    assertThat(hasLambdaSource)
        .as("Expected edges from synthetic lambda classes to impl methods")
        .isTrue();
  }

  /**
   * Integration test: Verifies fixed-point re-resolution connects virtual callers to lambda targets.
   * Expects callers like FaultHidingSink.write to reach multiple lambda classes via dispatch.
   */
  @Test
  void testAsmEngine_LambdaVirtualDispatch_OkHttp() throws IOException {
    outputFile = TestUtils.createTempOutputFile();

    int exitCode = TestUtils.runMain(
        okhttpJar.getPath(),
        "-d", okhttpDeps.getPath(),
        "-o", outputFile.getPath(),
        "--engine=asm",
        "--include-rt=true"
    );

    assertThat(exitCode).isEqualTo(0);

    List<String[]> edges = TestUtils.parseCSV(outputFile);

    // FaultHidingSink.write calls Function1.invoke via INVOKEINTERFACE.
    // Fixed-point re-resolution should connect it to multiple lambda targets:
    //   wala/lambda$okhttp3$internal$cache$DiskLruCache$0
    //   wala/lambda$okhttp3$internal$cache$DiskLruCache$Editor$0
    String faultHidingSinkWrite = "okhttp3/internal/cache/FaultHidingSink.write:";
    List<String> lambdaTargets = edges.stream()
        .filter(e -> e[0].startsWith(faultHidingSinkWrite))
        .map(e -> e[1])
        .filter(t -> t.startsWith("wala/lambda$"))
        .collect(Collectors.toList());

    assertThat(lambdaTargets)
        .as("FaultHidingSink.write should reach multiple lambda targets via virtual dispatch")
        .hasSizeGreaterThanOrEqualTo(2);

    // Verify at least two distinct lambda classes are targeted
    Set<String> distinctLambdaClasses = lambdaTargets.stream()
        .map(t -> t.substring(0, t.indexOf('.')))
        .collect(Collectors.toSet());

    assertThat(distinctLambdaClasses)
        .as("FaultHidingSink.write should dispatch to at least 2 distinct lambda classes")
        .hasSizeGreaterThanOrEqualTo(2);
  }

  /**
   * Integration test: Tests error handling for invalid engine option.
   * Expects exit code 1 (error) when invalid engine is specified.
   */
  @Test
  void testInvalidEngine_ReturnsErrorCode() {
    int exitCode = TestUtils.runMain(
        slf4jJar.getPath(),
        "--engine=invalid"
    );
    assertThat(exitCode).isEqualTo(1);
  }

  /**
   * Integration test: Verifies default engine is wala.
   * Expects same behavior as explicit --engine=wala.
   */
  @Test
  void testDefaultEngine_IsWala() throws IOException {
    File walaOutput = TestUtils.createTempOutputFile();
    File defaultOutput = TestUtils.createTempOutputFile();

    try {
      // Run with explicit wala
      TestUtils.runMain(
          slf4jJar.getPath(),
          "-o", walaOutput.getPath(),
          "--engine=wala",
          "--include-rt=false"
      );

      // Run with default (no --engine)
      TestUtils.runMain(
          slf4jJar.getPath(),
          "-o", defaultOutput.getPath(),
          "--include-rt=false"
      );

      // Both should have same edge count
      int walaEdges = TestUtils.countEdges(walaOutput);
      int defaultEdges = TestUtils.countEdges(defaultOutput);
      assertThat(defaultEdges).isEqualTo(walaEdges);
    } finally {
      TestUtils.cleanupFile(walaOutput);
      TestUtils.cleanupFile(defaultOutput);
    }
  }
}
