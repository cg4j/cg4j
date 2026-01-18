package io.drmir;

import picocli.CommandLine;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TestUtils {

  /**
   * Get test JAR file from resources
   */
  public static File getTestJar(String filename) {
    try {
      return new File(TestUtils.class.getResource("/test-jars/" + filename).toURI());
    } catch (URISyntaxException e) {
      throw new RuntimeException("Test JAR not found: " + filename, e);
    }
  }

  /**
   * Get test deps directory
   */
  public static File getTestDepsDir() {
    try {
      return new File(TestUtils.class.getResource("/test-jars/deps").toURI());
    } catch (URISyntaxException e) {
      throw new RuntimeException("Test deps directory not found", e);
    }
  }

  /**
   * Parse CSV file and return list of edges [source, target]
   */
  public static List<String[]> parseCSV(File csvFile) throws IOException {
    List<String[]> edges = new ArrayList<>();
    try (BufferedReader reader = Files.newBufferedReader(csvFile.toPath())) {
      String header = reader.readLine(); // Skip header
      String line;
      while ((line = reader.readLine()) != null) {
        edges.add(line.split(",", 2));
      }
    }
    return edges;
  }

  /**
   * Count total edges in CSV (excluding header)
   */
  public static int countEdges(File csvFile) throws IOException {
    return parseCSV(csvFile).size();
  }

  /**
   * Check if CSV contains java/* classes in sources or targets
   */
  public static boolean hasRTClasses(File csvFile) throws IOException {
    List<String[]> edges = parseCSV(csvFile);
    return edges.stream().anyMatch(edge ->
        edge[0].startsWith("java/") || edge[1].startsWith("java/"));
  }

  /**
   * Get unique source package prefixes (first 2 segments)
   */
  public static Set<String> getSourcePrefixes(File csvFile) throws IOException {
    List<String[]> edges = parseCSV(csvFile);
    Set<String> prefixes = new HashSet<>();
    for (String[] edge : edges) {
      String source = edge[0];
      if (!source.equals("<boot>")) {
        String[] parts = source.split("/");
        if (parts.length >= 2) {
          prefixes.add(parts[0] + "/" + parts[1]);
        }
      }
    }
    return prefixes;
  }

  /**
   * Create temporary output file for test
   */
  public static File createTempOutputFile() throws IOException {
    File tempFile = File.createTempFile("test-cg-", ".csv");
    tempFile.deleteOnExit();
    return tempFile;
  }

  /**
   * Delete file if exists
   */
  public static void cleanupFile(File file) {
    if (file != null && file.exists()) {
      file.delete();
    }
  }

  /**
   * Run Main programmatically without System.exit()
   */
  public static int runMain(String... args) {
    Main main = new Main();
    CommandLine cmd = new CommandLine(main);
    return cmd.execute(args);
  }

  /**
   * Assert edge count is within tolerance
   */
  public static void assertEdgeCount(File csv, int expected, double tolerancePercent)
      throws IOException {
    int actual = countEdges(csv);
    int tolerance = (int) Math.ceil(expected * tolerancePercent / 100.0);
    int min = expected - tolerance;
    int max = expected + tolerance;

    if (actual < min || actual > max) {
      throw new AssertionError(String.format(
          "Edge count %d not within %d±%d (%.1f%% tolerance)",
          actual, expected, tolerance, tolerancePercent));
    }
  }
}
