package io.drmir;

import com.ibm.wala.ipa.callgraph.CallGraph;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

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
import java.util.concurrent.Callable;

/**
 * Main entry point for the call graph generation tool.
 */
@Command(name = "java-cg-wala", 
         mixinStandardHelpOptions = true,
         version = "1.0-SNAPSHOT",
         description = "Builds call graphs from Java JAR files using WALA")
public class Main implements Callable<Integer> {

  @Parameters(index = "0", 
              description = "JAR file to analyze")
  private File targetJar;

  @Option(names = {"-o", "--output"}, 
          description = "Output CSV file (default: ${DEFAULT-VALUE})",
          defaultValue = "callgraph.csv")
  private String outputCsv;

  @Option(names = {"-d", "--deps"}, 
          description = "Directory containing dependency JAR files")
  private File depsDir;

  public static void main(String[] args) {
    int exitCode = new CommandLine(new Main()).execute(args);
    System.exit(exitCode);
  }

  @Override
  public Integer call() throws Exception {
    // Validate target JAR exists
    if (!targetJar.exists()) {
      System.err.println("Error: Target JAR file not found: " + targetJar);
      return 1;
    }
    
    // Find JAR dependencies
    List<File> dependencies = new ArrayList<>();
    if (depsDir != null) {
      if (depsDir.exists() && depsDir.isDirectory()) {
        dependencies = findJarsInDirectory(depsDir);
        System.out.println("Found " + dependencies.size() + " dependency JARs");
      } else {
        System.err.println("Warning: Dependencies directory not found: " + depsDir);
      }
    }
    
    // Create exclusion file
    Path exclusionFile = createExclusionFile();
    System.out.println("Created exclusion file: " + exclusionFile);
    
    // Build call graph
    System.out.println("Building call graph with 0-CFA...");
    long startTime = System.nanoTime();
    
    CallGraphBuilder builder = new CallGraphBuilder();
    CallGraph cg = builder.buildCallGraph(targetJar.getPath(), dependencies, exclusionFile.toFile());
    
    double duration = (System.nanoTime() - startTime) / 1_000_000_000.0;
    System.out.println("Call graph built in " + String.format("%.2f", duration) + " seconds");
    System.out.println("Total nodes: " + cg.getNumberOfNodes());
    
    // Write call graph to CSV
    System.out.println("\nWriting call graph to: " + outputCsv);
    int edgeCount = writeCallGraphToCSV(cg, outputCsv);
    System.out.println("Total edges written: " + edgeCount);
    
    return 0;
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

}
