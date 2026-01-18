package io.drmir;

import com.ibm.wala.ipa.callgraph.CallGraph;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Main entry point for the call graph generation tool.
 */
public class Main {

  public static void main(String[] args) throws Exception {
    // Parse command line arguments
    if (args.length == 0) {
      printUsage();
      System.exit(1);
    }
    
    String targetJar = args[0];
    File targetFile = new File(targetJar);
    
    if (!targetFile.exists()) {
      System.err.println("Error: Target JAR file not found: " + targetJar);
      System.exit(1);
    }
    
    // Determine output CSV file
    String outputCsv = "callgraph.csv";
    int depsArgIndex = 1;
    if (args.length > 1 && !new File(args[1]).isDirectory()) {
      outputCsv = args[1];
      depsArgIndex = 2;
    }
    
    // Find JAR dependencies
    List<File> dependencies = new ArrayList<>();
    if (args.length > depsArgIndex) {
      File depsDir = new File(args[depsArgIndex]);
      if (depsDir.exists() && depsDir.isDirectory()) {
        dependencies = findJarsInDirectory(depsDir);
        System.out.println("Found " + dependencies.size() + " dependency JARs");
      }
    }
    
    // Create exclusion file
    Path exclusionFile = createExclusionFile();
    System.out.println("Created exclusion file: " + exclusionFile);
    
    // Build call graph
    System.out.println("Building call graph with 0-CFA...");
    long startTime = System.nanoTime();
    
    CallGraphBuilder builder = new CallGraphBuilder();
    CallGraph cg = builder.buildCallGraph(targetJar, dependencies, exclusionFile.toFile());
    
    double duration = (System.nanoTime() - startTime) / 1_000_000_000.0;
    System.out.println("Call graph built in " + String.format("%.2f", duration) + " seconds");
    System.out.println("Total nodes: " + cg.getNumberOfNodes());
    
    // Write call graph to CSV
    System.out.println("\nWriting call graph to: " + outputCsv);
    int edgeCount = writeCallGraphToCSV(cg, outputCsv);
    System.out.println("Total edges written: " + edgeCount);
  }

  /**
   * Writes call graph to CSV file with format: source_method,target_method
   */
  private static int writeCallGraphToCSV(CallGraph cg, String outputFile) throws IOException {
    int edgeCount = 0;
    
    try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
      writer.println("source_method,target_method");
      
      Iterator<CallGraphBuilder.CallGraphEdge> edges = CallGraphBuilder.extractEdges(cg);
      while (edges.hasNext()) {
        CallGraphBuilder.CallGraphEdge edge = edges.next();
        writer.println(edge.source + "," + edge.target);
        edgeCount++;
      }
    }
    
    return edgeCount;
  }

  /**
   * Creates a WALA exclusion file to filter standard library classes.
   */
  private static Path createExclusionFile() throws IOException {
    List<String> exclusions = Arrays.asList(
        "java/util/.*",
        "java/io/.*",
        "java/nio/.*",
        "java/net/.*",
        "java/math/.*",
        "java/awt/.*",
        "java/text/.*",
        "java/sql/.*",
        "java/security/.*",
        "java/time/.*",
        "javax/.*",
        "sun/.*",
        "com/sun/.*",
        "jdk/.*",
        "org/graalvm/.*"
    );
    
    Path tmpFile = Paths.get("/tmp/wala_exclusions.txt");
    Files.write(tmpFile, exclusions);
    return tmpFile;
  }

  /**
   * Finds all JAR files in a directory.
   */
  private static List<File> findJarsInDirectory(File directory) {
    List<File> jarFiles = new ArrayList<>();
    File[] files = directory.listFiles();
    if (files != null) {
      for (File file : files) {
        if (file.isFile() && file.getName().endsWith(".jar")) {
          jarFiles.add(file);
        }
      }
    }
    return jarFiles;
  }

  /**
   * Prints usage information.
   */
  private static void printUsage() {
    System.err.println("Usage: java -jar java-cg-wala.jar <target.jar> [output.csv] [dependencies-dir]");
    System.err.println();
    System.err.println("Arguments:");
    System.err.println("  target.jar         - JAR file to analyze (required)");
    System.err.println("  output.csv         - Output CSV file (default: callgraph.csv)");
    System.err.println("  dependencies-dir   - Directory containing dependency JAR files");
  }
}
