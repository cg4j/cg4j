package net.cg4j;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Integration test for the -q/--quiet CLI option. Runs cg4j as a subprocess to isolate stdout
 * capture from parallel tests.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QuietModeIntegrationTest extends BaseIntegrationTest {

  /**
   * Integration test: Verifies that -q suppresses INFO-level log output. Expects zero stdout output
   * and a valid CSV when quiet mode is enabled.
   */
  @Test
  @Disabled("Optional: requires fat JAR to be built first (mvn package)")
  void testQuietMode_SuppressesInfoLogs() throws IOException, InterruptedException {
    outputFile = TestUtils.createTempOutputFile();

    String jarPath = findFatJar();
    ProcessBuilder pb =
        new ProcessBuilder(
            "java",
            "-jar",
            jarPath,
            "-q",
            "-j",
            slf4jJar.getPath(),
            "-o",
            outputFile.getPath(),
            "--engine=asm",
            "--include-rt=false");
    pb.redirectErrorStream(true);

    Process process = pb.start();
    String stdout;
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(process.getInputStream()))) {
      stdout = reader.lines().collect(Collectors.joining("\n"));
    }

    boolean finished = process.waitFor(60, TimeUnit.SECONDS);
    assertThat(finished).as("Process should complete within 60 seconds").isTrue();
    assertThat(process.exitValue()).isEqualTo(0);

    // CSV should still be valid
    assertThat(outputFile).exists();
    int edgeCount = TestUtils.countEdges(outputFile);
    assertThat(edgeCount).isGreaterThan(0);

    // No log output should appear on stdout in quiet mode
    assertThat(stdout)
        .as("Expected no log output in quiet mode, but captured:\n%s", stdout)
        .isEmpty();
  }

  /** Locates the fat JAR (jar-with-dependencies) in the target directory. */
  private static String findFatJar() throws IOException {
    File targetDir = new File("target");
    assertThat(targetDir).exists();

    File[] jars = targetDir.listFiles((dir, name) -> name.endsWith("-jar-with-dependencies.jar"));
    assertThat(jars)
        .as("Expected fat JAR in target/. Run 'mvn package' first.")
        .isNotNull()
        .isNotEmpty();

    return jars[0].getAbsolutePath();
  }
}
