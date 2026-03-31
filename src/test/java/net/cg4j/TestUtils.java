package net.cg4j;

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
import java.util.stream.Collectors;

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
   * Parse CSV file and return a de-duplicated edge set.
   */
  public static Set<String> parseEdgeSet(File csvFile) throws IOException {
    return parseCSV(csvFile).stream()
        .map(edge -> edge[0] + "," + edge[1])
        .collect(Collectors.toCollection(HashSet::new));
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
   * 
   * First argument is treated as JAR path and automatically prefixed with -j flag.
   * Example: runMain("app.jar", "-o", "out.csv") -> executes with args: ["-j", "app.jar", "-o", "out.csv"]
   */
  public static int runMain(String... args) {
    if (args.length == 0) {
      throw new IllegalArgumentException("runMain requires at least one argument (JAR path)");
    }
    
    // Prepend -j flag before the first argument (JAR path)
    String[] newArgs = new String[args.length + 1];
    newArgs[0] = "-j";
    System.arraycopy(args, 0, newArgs, 1, args.length);
    
    Main main = new Main();
    CommandLine cmd = new CommandLine(main);
    return cmd.execute(newArgs);
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

  /**
   * Assert that ASM covers WALA's edge set within the allowed missing percentage.
   */
  public static void assertWalaEdgeCoverage(File walaCsv, File asmCsv, double allowedMissingPercent)
      throws IOException {
    Set<String> walaEdges = parseEdgeSet(walaCsv);
    Set<String> asmEdges = parseEdgeSet(asmCsv);

    Set<String> missingEdges = new HashSet<>(walaEdges);
    missingEdges.removeAll(asmEdges);

    int allowedMissing = (int) Math.ceil(walaEdges.size() * allowedMissingPercent / 100.0);
    if (missingEdges.size() > allowedMissing) {
      List<String> sampleMissing = missingEdges.stream()
          .sorted()
          .limit(10)
          .collect(Collectors.toList());
      throw new AssertionError(String.format(
          "Missing %d WALA edges (allowed %d, %.2f%% tolerance). Sample: %s",
          missingEdges.size(), allowedMissing, allowedMissingPercent, sampleMissing));
    }
  }
}
