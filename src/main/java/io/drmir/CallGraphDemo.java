package io.drmir;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.Language;
import com.ibm.wala.core.util.config.AnalysisScopeReader;
import com.ibm.wala.ipa.callgraph.*;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.util.config.FileOfClasses;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Call Graph Demo using WALA with CHA-based analysis.
 * Based on ml4cgp_study implementation.
 * 
 * Features:
 *  1. Builds Class Hierarchy Analysis (CHA) with support for JAR dependencies
 *  2. Creates 0-CFA call graph using all public methods as entry points
 *  3. Supports exclusion files to filter out standard library classes
 *  4. Outputs call graph edges to CSV file
 *
 * Usage: java -jar callgraph-demo.jar <path-to-target.jar> [output-csv] [dependencies-dir]
 */
public class CallGraphDemo {

  public static void main(String[] args) throws Exception {
    // 1️⃣  Target to analyze
    if (args.length == 0) {
      System.err.println("Usage: java -jar callgraph-demo.jar <path-to-target.jar> [output-csv] [dependencies-dir]");
      System.exit(1);
    }
    
    String targetJar = args[0];
    File targetFile = new File(targetJar);
    
    if (!targetFile.exists()) {
      System.err.println("Error: Target JAR file not found: " + targetJar);
      System.exit(1);
    }
    
    // 2️⃣  Determine output CSV file
    String outputCsv = "callgraph.csv";
    int depsArgIndex = 1;
    if (args.length > 1 && !new File(args[1]).isDirectory()) {
      outputCsv = args[1];
      depsArgIndex = 2;
    }
    
    // 3️⃣  Find JAR dependencies (if dependencies directory is provided)
    List<File> dependencies = new ArrayList<>();
    if (args.length > depsArgIndex) {
      File depsDir = new File(args[depsArgIndex]);
      if (depsDir.exists() && depsDir.isDirectory()) {
        dependencies = findJarsInDirectory(depsDir);
        System.out.println("Found " + dependencies.size() + " dependency JARs");
      }
    }
    
    // 4️⃣  Create exclusion file to filter out standard library classes
    Path exclusionFile = createWalaExclusionFile();
    System.out.println("Created exclusion file: " + exclusionFile);
    
    // 5️⃣  Build Class Hierarchy with CHA
    ClassHierarchy cha = constructCHA(targetJar, dependencies, exclusionFile.toFile());
    System.out.println("Built Class Hierarchy with " + dependencies.size() + " dependencies");
    
    // 6️⃣  Generate entry points (all public methods in application/library classes)
    List<Entrypoint> entrypoints = generateEntryPoints(cha);
    System.out.println("Generated " + entrypoints.size() + " entry points");
    
    if (entrypoints.isEmpty()) {
      System.err.println("Warning: No entry points found!");
      return;
    }
    
    // 7️⃣  Configure analysis options
    AnalysisOptions options = new AnalysisOptions();
    options.setEntrypoints(entrypoints);
    options.setReflectionOptions(AnalysisOptions.ReflectionOptions.NONE);
    
    // 8️⃣  Build 0-CFA call graph
    System.out.println("Building call graph with 0-CFA...");
    long startTime = System.nanoTime();
    
    AnalysisCacheImpl cache = new AnalysisCacheImpl();
    SSAPropagationCallGraphBuilder cgBuilder = Util.makeZeroCFABuilder(
        Language.JAVA, options, cache, cha);
    
    CallGraph cg = cgBuilder.makeCallGraph(options, null);
    
    double duration = (System.nanoTime() - startTime) / 1_000_000_000.0;
    System.out.println("Call graph built in " + String.format("%.2f", duration) + " seconds");
    System.out.println("Total nodes: " + cg.getNumberOfNodes());
    
    // 9️⃣  Write call graph to CSV file
    System.out.println("\nWriting call graph to: " + outputCsv);
    int edgeCount = writeCallGraphToCSV(cg, outputCsv);
    System.out.println("Total edges written: " + edgeCount);
  }

  /**
   * Constructs Class Hierarchy with exclusions.
   * Based on ml4cgp_study CHA.java implementation.
   */
  private static ClassHierarchy constructCHA(String programJarFile, 
                                             List<File> dependenciesJarFiles,
                                             File exclusionFile) throws Exception {
    AnalysisScope scope = AnalysisScopeReader.instance.makeJavaBinaryAnalysisScope(
        programJarFile, null);
    
    // Add dependency JARs to Extension class loader
    for (File depJarFile : dependenciesJarFiles) {
      scope.addToScope(ClassLoaderReference.Extension, new JarFile(depJarFile));
    }
    
    // Set exclusions
    if (exclusionFile != null && exclusionFile.exists()) {
      try (FileInputStream fis = new FileInputStream(exclusionFile)) {
        scope.setExclusions(new FileOfClasses(fis));
      }
    }
    
    return ClassHierarchyFactory.make(scope);
  }

  /**
   * Generates entry points from all public methods in application and library classes.
   * Based on ml4cgp_study EntryPointsGenerator.java.
   */
  private static List<Entrypoint> generateEntryPoints(IClassHierarchy cha) {
    return StreamSupport.stream(cha.spliterator(), false)
        .filter(CallGraphDemo::isPublicClass)
        .flatMap(klass -> klass.getDeclaredMethods().stream())
        .filter(CallGraphDemo::isPublicMethod)
        .map(m -> new DefaultEntrypoint(m, cha))
        .collect(Collectors.toList());
  }

  /**
   * Checks if a class is public and from Application or Extension loader.
   */
  private static boolean isPublicClass(IClass klass) {
    return isApplicationOrLibrary(klass)
        && !klass.isInterface()
        && klass.isPublic();
  }

  /**
   * Checks if a method is public and non-abstract.
   */
  private static boolean isPublicMethod(IMethod method) {
    return isApplicationOrLibrary(method.getDeclaringClass())
        && method.isPublic()
        && !method.isAbstract();
  }

  /**
   * Checks if class is from Application or Extension (library) loader.
   */
  private static boolean isApplicationOrLibrary(IClass klass) {
    ClassLoaderReference ref = klass.getClassLoader().getReference();
    return ref.equals(ClassLoaderReference.Application) ||
           ref.equals(ClassLoaderReference.Extension);
  }

  /**
   * Writes call graph to CSV file with format: source_method,target_method
   */
  private static int writeCallGraphToCSV(CallGraph cg, String outputFile) throws IOException {
    int edgeCount = 0;
    
    try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
      // Write CSV header
      writer.println("source_method,target_method");
      
      for (Iterator<CGNode> it = cg.iterator(); it.hasNext();) {
        CGNode sourceNode = it.next();
        IMethod sourceMethod = sourceNode.getMethod();
        TypeName sourceType = sourceMethod.getDeclaringClass().getName();
        Selector sourceSelector = sourceMethod.getSelector();
        
        String sourceUri = formatMethod(sourceType, sourceSelector);
        if (sourceUri == null) continue;
        
        // Skip fake root methods
        boolean isBootMethod = sourceUri.equals("com/ibm/wala/FakeRootClass.fakeRootMethod:()V") ||
                              sourceUri.equals("com/ibm/wala/FakeRootClass.fakeWorldClinit:()V");
        
        if (isBootMethod) {
          sourceUri = "<boot>";
        }
        
        // Iterate through call sites
        Iterator<CGNode> targets = cg.getSuccNodes(sourceNode);
        while (targets.hasNext()) {
          CGNode targetNode = targets.next();
          IMethod targetMethod = targetNode.getMethod();
          TypeName targetType = targetMethod.getDeclaringClass().getName();
          Selector targetSelector = targetMethod.getSelector();
          
          String targetUri = formatMethod(targetType, targetSelector);
          if (targetUri == null) continue;
          
          // Skip fake world clinit
          if (targetUri.equals("com/ibm/wala/FakeRootClass.fakeWorldClinit:()V")) {
            continue;
          }
          
          writer.println(sourceUri + "," + targetUri);
          edgeCount++;
        }
      }
    }
    
    return edgeCount;
  }

  /**
   * Formats a method to URI format: package/Class.method:descriptor
   */
  private static String formatMethod(TypeName type, Selector selector) {
    String packageName = type.getPackage() == null ? "" : type.getPackage() + "/";
    String className = type.getClassName().toString();
    String methodName = selector.getName().toString();
    String descriptor = selector.getDescriptor().toString();
    
    String qualifiedClassName = packageName + className;
    
    // Skip lambda metafactory
    if (qualifiedClassName.equals("java/lang/invoke/LambdaMetafactory")) {
      return null;
    }
    
    return qualifiedClassName + "." + methodName + ":" + descriptor;
  }

  /**
   * Creates a WALA exclusion file to filter standard library classes.
   * Based on ml4cgp_study CGUtils.createWalaExclusionFile.
   */
  private static Path createWalaExclusionFile() throws IOException {
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
