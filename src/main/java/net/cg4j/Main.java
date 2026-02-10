package net.cg4j;

import com.ibm.wala.ipa.callgraph.CallGraph;
import net.cg4j.asm.AsmCallGraphBuilder;
import net.cg4j.asm.CallGraphResult;
import net.cg4j.asm.ScopeExclusions;
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
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Main entry point for the call graph generation tool.
 */
@Command(name = "cg4j",
         mixinStandardHelpOptions = true,
         versionProvider = VersionProvider.class,
         description = "Builds call graphs from Java JAR files using WALA or ASM")
public class Main implements Callable<Integer> {

  private static final Logger logger = LogManager.getLogger(Main.class);

  @Option(names = {"-j", "--app-jar"},
          required = true,
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

  @Option(names = {"--engine"},
          description = "Call graph engine: wala or asm (default: ${DEFAULT-VALUE})",
          defaultValue = "wala")
  private String engine;

  @Option(names = {"--exclusions"},
          description = "Exclusion patterns file for ASM engine scope "
              + "(default: built-in WALA-compatible exclusions)")
  private File exclusionsFile;

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

    // Validate engine option
    if (!engine.equals("wala") && !engine.equals("asm")) {
      logger.error("Invalid engine: {}. Must be 'wala' or 'asm'", engine);
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

    logger.info("Building call graph with engine={} (includeRt={})...", engine, includeRt);
    long startTime = System.nanoTime();

    int edgeCount;
    if (engine.equals("asm")) {
      edgeCount = buildWithAsm(dependencies);
    } else {
      edgeCount = buildWithWala(dependencies);
    }

    double duration = (System.nanoTime() - startTime) / 1_000_000_000.0;
    logger.info("Call graph built in {} seconds", String.format("%.2f", duration));
    logger.info("Total edges written: {}", edgeCount);

    return 0;
  }

  /**
   * Builds call graph using WALA engine.
   */
  private int buildWithWala(List<File> dependencies) throws Exception {
    // Create exclusion file
    Path exclusionFile = createExclusionFile();
    logger.info("Created exclusion file: {}", exclusionFile);

    WalaCallGraphBuilder builder = new WalaCallGraphBuilder();
    CallGraph cg = builder.buildCallGraph(targetJar.getPath(), dependencies, exclusionFile.toFile());
    logger.info("Total nodes: {}", cg.getNumberOfNodes());

    // Write call graph to CSV
    logger.info("Writing call graph to: {}", outputCsv);
    return writeCallGraphToCSV(cg, outputCsv, includeRt);
  }

  /**
   * Builds call graph using ASM engine.
   */
  private int buildWithAsm(List<File> dependencies) throws Exception {
    // Load exclusions
    ScopeExclusions exclusions;
    if (exclusionsFile != null) {
      exclusions = ScopeExclusions.loadFromFile(exclusionsFile);
      logger.info("Loaded custom exclusions from: {}", exclusionsFile);
    } else {
      exclusions = ScopeExclusions.loadDefaults();
      logger.info("Using default scope exclusions ({} patterns)", exclusions.patternCount());
    }

    AsmCallGraphBuilder builder = new AsmCallGraphBuilder();
    CallGraphResult result = builder.buildCallGraph(
        targetJar.getPath(), dependencies, includeRt, exclusions);
    logger.info("Total reachable methods: {}", result.getReachableMethods().size());

    // Write call graph to CSV
    logger.info("Writing call graph to: {}", outputCsv);
    return writeAsmCallGraphToCSV(result, outputCsv);
  }

  /**
   * Writes ASM call graph result to CSV file.
   */
  private static int writeAsmCallGraphToCSV(CallGraphResult result, String outputFile)
      throws IOException {
    try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
      writer.println("source_method,target_method");

      for (CallGraphResult.Edge edge : result.getEdges()) {
        String sourceUri = edge.getSource().toUri();
        String targetUri = edge.getTarget().toUri();
        writer.println(sourceUri + "," + targetUri);
      }
    }

    return result.getEdgeCount();
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
      
      AtomicInteger edgeCounter = new AtomicInteger(0);
      Stream<WalaCallGraphBuilder.CallGraphEdge> edges = WalaCallGraphBuilder.extractEdgesAsStream(cg, includeRt);
      edges.forEach(edge -> {
        writer.println(edge.source + "," + edge.target);
        edgeCounter.incrementAndGet();
      });
      edgeCount = edgeCounter.get();
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
