package net.cg4j.asm;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Builds call graphs using ASM with RTA-based virtual call resolution.
 */
public final class AsmCallGraphBuilder {

  private static final Logger logger = LogManager.getLogger(AsmCallGraphBuilder.class);
  private static final int PARSE_OPTIONS = ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES;

  private ClassHierarchy hierarchy;
  private Map<String, byte[]> classBytecode;

  /**
   * Builds a call graph for the given JAR file using RTA algorithm.
   *
   * @param jarFile the target JAR to analyze
   * @param dependencies list of dependency JAR files
   * @param includeRt whether to include JDK classes in the output
   * @return the call graph result
   */
  public CallGraphResult buildCallGraph(String jarFile, List<File> dependencies, boolean includeRt)
      throws IOException {
    logger.info("Loading classes...");

    // Step 1: Load all classes
    Map<String, ClassInfo> allClasses = new HashMap<>();
    classBytecode = new HashMap<>();

    // Load JDK classes (Primordial)
    Map<String, ClassInfo> jdkClasses = JarScanner.scanJdk();
    allClasses.putAll(jdkClasses);
    logger.info("Loaded {} JDK classes", jdkClasses.size());

    // Load dependency classes (Extension)
    if (dependencies != null && !dependencies.isEmpty()) {
      Map<String, ClassInfo> depClasses = JarScanner.scanJars(dependencies, ClassLoaderType.EXTENSION);
      allClasses.putAll(depClasses);
      loadBytecode(dependencies, classBytecode);
      logger.info("Loaded {} dependency classes", depClasses.size());
    }

    // Load application classes (Application)
    File targetJar = new File(jarFile);
    Map<String, ClassInfo> appClasses = JarScanner.scanJar(targetJar, ClassLoaderType.APPLICATION);
    allClasses.putAll(appClasses);
    loadBytecode(List.of(targetJar), classBytecode);
    logger.info("Loaded {} application classes", appClasses.size());

    // Step 2: Build class hierarchy
    logger.info("Building class hierarchy...");
    hierarchy = new ClassHierarchy(allClasses);
    logger.info("Class hierarchy built with {} classes", hierarchy.size());

    // Step 3: Find entry points (public methods from APPLICATION classes)
    Set<MethodSignature> entryPoints = findEntryPoints(appClasses);
    logger.info("Found {} entry points", entryPoints.size());

    if (entryPoints.isEmpty()) {
      throw new RuntimeException("No entry points found in JAR file");
    }

    // Step 4: Run worklist algorithm
    logger.info("Running worklist algorithm with RTA...");
    WorklistResult result = runWorklist(entryPoints);

    // Step 5: Filter edges if needed
    Set<CallGraphResult.Edge> edges = result.edges;
    if (!includeRt) {
      edges = filterRtEdges(edges);
      logger.info("Filtered to {} edges (excludeRt=true)", edges.size());
    }

    return new CallGraphResult(edges, result.reachable);
  }

  /**
   * Finds all entry points from application classes.
   * Entry points are public, non-abstract methods from public, non-interface classes.
   */
  private Set<MethodSignature> findEntryPoints(Map<String, ClassInfo> appClasses) {
    Set<MethodSignature> entryPoints = new HashSet<>();

    for (ClassInfo classInfo : appClasses.values()) {
      // Only public, non-interface classes
      if (!classInfo.isPublic() || classInfo.isInterface()) {
        continue;
      }

      for (MethodSignature method : classInfo.getMethods()) {
        // Only include public, non-abstract methods as entry points
        if (method.isPublic() && !method.isAbstract()) {
          entryPoints.add(method);
        }
      }
    }

    return entryPoints;
  }

  /**
   * Finds all static initializer methods from APPLICATION and EXTENSION classes.
   *
   * @return set of clinit method signatures
   */
  private Set<MethodSignature> findClinitMethods() {
    Set<MethodSignature> clinits = new HashSet<>();

    for (ClassInfo classInfo : hierarchy.getAllClasses().values()) {
      if (classInfo.getLoaderType() == ClassLoaderType.PRIMORDIAL) {
        continue;
      }
      if (classInfo.hasClinit()) {
        clinits.add(new MethodSignature(classInfo.getName(), "<clinit>", "()V"));
      }
    }

    return clinits;
  }

  /**
   * Runs the worklist algorithm to compute reachable methods and call edges.
   *
   * @param entryPoints the set of entry point methods
   * @return the worklist result containing reachable methods and edges
   */
  private WorklistResult runWorklist(Set<MethodSignature> entryPoints) {
    Set<MethodSignature> reachable = new HashSet<>();
    Set<CallGraphResult.Edge> edges = new HashSet<>();
    Deque<MethodSignature> worklist = new ArrayDeque<>(entryPoints);

    // Track instantiated types for RTA
    Set<String> instantiatedTypes = new HashSet<>();

    // Entry point classes are considered instantiated
    for (MethodSignature entry : entryPoints) {
      instantiatedTypes.add(entry.getOwner());
    }

    // Add synthetic boot entry point
    MethodSignature bootMethod = new MethodSignature("<boot>", "fakeRoot", "()V");

    // Add edges from boot to all entry points
    for (MethodSignature entry : entryPoints) {
      edges.add(new CallGraphResult.Edge(bootMethod, entry));
    }

    // Add edges from boot to <clinit> methods for APPLICATION classes
    for (MethodSignature clinit : findClinitMethods()) {
      edges.add(new CallGraphResult.Edge(bootMethod, clinit));
      // Add <clinit> to worklist so its call sites are analyzed
      if (!reachable.contains(clinit)) {
        worklist.add(clinit);
      }
    }

    int processedCount = 0;
    while (!worklist.isEmpty()) {
      MethodSignature method = worklist.poll();

      if (reachable.contains(method)) {
        continue;
      }
      reachable.add(method);
      processedCount++;

      if (processedCount % 1000 == 0) {
        logger.debug("Processed {} methods, worklist size: {}", processedCount, worklist.size());
      }

      // Extract call sites and instantiated types
      MethodAnalysisResult analysisResult = analyzeMethod(method);
      instantiatedTypes.addAll(analysisResult.instantiatedTypes);

      for (CallSite callSite : analysisResult.callSites) {
        Set<MethodSignature> targets = hierarchy.resolveCallSiteRTA(callSite, instantiatedTypes);

        for (MethodSignature target : targets) {
          edges.add(new CallGraphResult.Edge(method, target));

          if (!reachable.contains(target)) {
            worklist.add(target);
          }
        }
      }
    }

    logger.info("Worklist complete: {} reachable methods, {} edges", reachable.size(), edges.size());
    return new WorklistResult(reachable, edges);
  }

  /**
   * Analyzes a method's bytecode to extract call sites and instantiated types.
   */
  private MethodAnalysisResult analyzeMethod(MethodSignature method) {
    byte[] bytecode = classBytecode.get(method.getOwner());
    if (bytecode == null) {
      // Class bytecode not available (e.g., JDK class)
      return new MethodAnalysisResult(List.of(), Set.of());
    }

    ClassReader reader = new ClassReader(bytecode);
    MethodAnalysisVisitor visitor = new MethodAnalysisVisitor(method.getName(), method.getDescriptor());
    reader.accept(visitor, PARSE_OPTIONS);
    return visitor.getResult();
  }

  /**
   * Result of analyzing a method's bytecode.
   */
  private static class MethodAnalysisResult {
    final List<CallSite> callSites;
    final Set<String> instantiatedTypes;

    MethodAnalysisResult(List<CallSite> callSites, Set<String> instantiatedTypes) {
      this.callSites = callSites;
      this.instantiatedTypes = instantiatedTypes;
    }
  }

  /**
   * Loads bytecode for classes from JAR files.
   */
  private void loadBytecode(List<File> jarFiles, Map<String, byte[]> bytecodeMap)
      throws IOException {
    for (File jarFile : jarFiles) {
      try (JarFile jar = new JarFile(jarFile)) {
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
          JarEntry entry = entries.nextElement();
          if (entry.getName().endsWith(".class")) {
            try (InputStream is = jar.getInputStream(entry)) {
              byte[] bytes = is.readAllBytes();
              String className = entry.getName()
                  .replace(".class", "")
                  .replace("/", "/"); // Keep internal format
              // Extract actual class name from the bytecode
              try {
                ClassReader reader = new ClassReader(bytes);
                bytecodeMap.put(reader.getClassName(), bytes);
              } catch (Exception e) {
                logger.trace("Failed to read class name from {}", entry.getName());
              }
            }
          }
        }
      }
    }
  }

  /**
   * Filters out edges involving Primordial (JDK) classes.
   */
  private Set<CallGraphResult.Edge> filterRtEdges(Set<CallGraphResult.Edge> edges) {
    Set<CallGraphResult.Edge> filtered = new HashSet<>();

    for (CallGraphResult.Edge edge : edges) {
      ClassInfo sourceClass = hierarchy.getClass(edge.getSource().getOwner());
      ClassInfo targetClass = hierarchy.getClass(edge.getTarget().getOwner());

      // Skip if either class is not found or is Primordial
      boolean sourceIsPrimordial = sourceClass != null
          && sourceClass.getLoaderType() == ClassLoaderType.PRIMORDIAL;
      boolean targetIsPrimordial = targetClass != null
          && targetClass.getLoaderType() == ClassLoaderType.PRIMORDIAL;

      // Also handle synthetic boot method
      boolean sourceIsBoot = edge.getSource().getOwner().equals("<boot>");

      if (!sourceIsPrimordial && !targetIsPrimordial && !sourceIsBoot) {
        filtered.add(edge);
      } else if (sourceIsBoot && !targetIsPrimordial) {
        // Keep boot -> non-primordial edges
        filtered.add(edge);
      }
    }

    return filtered;
  }

  /**
   * Temporary holder for worklist algorithm results.
   */
  private static class WorklistResult {
    final Set<MethodSignature> reachable;
    final Set<CallGraphResult.Edge> edges;

    WorklistResult(Set<MethodSignature> reachable, Set<CallGraphResult.Edge> edges) {
      this.reachable = reachable;
      this.edges = edges;
    }
  }

  /**
   * ClassVisitor that extracts call sites and instantiated types from a specific method.
   */
  private static class MethodAnalysisVisitor extends ClassVisitor {
    private final String targetMethodName;
    private final String targetDescriptor;
    private List<CallSite> callSites = List.of();
    private Set<String> instantiatedTypes = Set.of();

    MethodAnalysisVisitor(String methodName, String descriptor) {
      super(Opcodes.ASM9);
      this.targetMethodName = methodName;
      this.targetDescriptor = descriptor;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor,
                                     String signature, String[] exceptions) {
      if (name.equals(targetMethodName) && descriptor.equals(targetDescriptor)) {
        CallSiteExtractor extractor = new CallSiteExtractor();
        callSites = extractor.getCallSites();
        instantiatedTypes = extractor.getInstantiatedTypes();
        return extractor;
      }
      return null;
    }

    MethodAnalysisResult getResult() {
      return new MethodAnalysisResult(callSites, instantiatedTypes);
    }
  }
}
