package io.drmir;

import com.ibm.wala.ipa.callgraph.CallGraph;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
@Command(name = "cg4j", 
         mixinStandardHelpOptions = true,
         version = "1.0-SNAPSHOT",
         description = "Builds call graphs from Java JAR files using WALA")
public class Main implements Callable<Integer> {

  private static final Logger logger = LogManager.getLogger(Main.class);

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

  @Option(names = {"--include-rt"}, 
          description = "Include Java Runtime (RT) jar in call graph analysis (default: ${DEFAULT-VALUE})",
          defaultValue = "true")
  private boolean includeRt;

  public static void main(String[] args) {
    int exitCode = new CommandLine(new Main()).execute(args);
    System.exit(exitCode);
  }

  @Override
  public Integer call() throws Exception {
    // Validate target JAR exists
    if (!targetJar.exists()) {
      logger.error("Target JAR file not found: {}", targetJar);
      return 1;
    }
    
    // Find JAR dependencies
    List<File> dependencies = new ArrayList<>();
    if (depsDir != null) {
      if (depsDir.exists() && depsDir.isDirectory()) {
        dependencies = findJarsInDirectory(depsDir);
        logger.info("Found {} dependency JARs", dependencies.size());
      } else {
        logger.warn("Dependencies directory not found: {}", depsDir);
      }
    }
    
    // Create exclusion file
    Path exclusionFile = createExclusionFile();
    logger.info("Created exclusion file: {}", exclusionFile);
    
    // Build call graph
    logger.info("Building call graph with 0-CFA (includeRt={})...", includeRt);
    long startTime = System.nanoTime();
    
    CallGraphBuilder builder = new CallGraphBuilder();
    CallGraph cg = builder.buildCallGraph(targetJar.getPath(), dependencies, exclusionFile.toFile());
    
    double duration = (System.nanoTime() - startTime) / 1_000_000_000.0;
    logger.info("Call graph built in {} seconds", String.format("%.2f", duration));
    logger.info("Total nodes: {}", cg.getNumberOfNodes());
    
    // Write call graph to CSV
    logger.info("\nWriting call graph to: {}", outputCsv);
    int edgeCount = writeCallGraphToCSV(cg, outputCsv, includeRt);
    logger.info("Total edges written: {}", edgeCount);
    
    return 0;
  }

  /**
   * Writes call graph to CSV file with format: source_method,target_method
   * 
   * @param includeRt if false, filters out edges involving RT (Primordial) methods
   */
  private static int writeCallGraphToCSV(CallGraph cg, String outputFile, boolean includeRt) throws IOException {
    int edgeCount = 0;
    
    try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
      writer.println("source_method,target_method");
      
      Iterator<CallGraphBuilder.CallGraphEdge> edges = CallGraphBuilder.extractEdges(cg, includeRt);
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
