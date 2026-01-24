package net.cg4j;

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

import java.io.File;
import java.io.FileInputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Builds call graphs using WALA with CHA-based analysis.
 */
public class CallGraphBuilder {

  /**
   * Builds a 0-CFA call graph for the given JAR file.
   */
  public CallGraph buildCallGraph(String jarFile, List<File> dependencies, File exclusionFile) 
      throws Exception {
    
    // Build Class Hierarchy with CHA
    ClassHierarchy cha = constructCHA(jarFile, dependencies, exclusionFile);
    
    // Generate entry points (only application and extension classes, never Primordial)
    List<Entrypoint> entrypoints = generateEntryPoints(cha);
    
    if (entrypoints.isEmpty()) {
      throw new RuntimeException("No entry points found in JAR file");
    }
    
    // Configure analysis options
    AnalysisOptions options = new AnalysisOptions();
    options.setEntrypoints(entrypoints);
    options.setReflectionOptions(AnalysisOptions.ReflectionOptions.NONE);
    
    // Build 0-CFA call graph
    AnalysisCacheImpl cache = new AnalysisCacheImpl();
    SSAPropagationCallGraphBuilder cgBuilder = Util.makeZeroCFABuilder(
        Language.JAVA, options, cache, cha);
    
    return cgBuilder.makeCallGraph(options, null);
  }

  /**
   * Constructs Class Hierarchy with exclusions.
   * 
   * Scope organization:
   * - Primordial: rt.jar (Java runtime) - always loaded for class hierarchy
   * - Extension: dependency JARs
   * - Application: target JAR being analyzed
   */
  private ClassHierarchy constructCHA(String programJarFile, 
                                      List<File> dependenciesJarFiles,
                                      File exclusionFile) throws Exception {
    // Always use standard scope with Primordial (rt.jar), Extension, and Application loaders
    // RT jar is required for class hierarchy construction (java.lang.Object, etc.)
    AnalysisScope scope = AnalysisScopeReader.instance.makeJavaBinaryAnalysisScope(
        programJarFile, exclusionFile);
    
    // Add dependency JARs to Extension class loader
    for (File depJarFile : dependenciesJarFiles) {
      scope.addToScope(ClassLoaderReference.Extension, new JarFile(depJarFile));
    }
    
    return ClassHierarchyFactory.make(scope);
  }

  /**
   * Generates entry points from all public methods in application classes only.
   * Only includes Application loader, not Extension or Primordial.
   */
  private List<Entrypoint> generateEntryPoints(IClassHierarchy cha) {
    return StreamSupport.stream(cha.spliterator(), false)
        .filter(this::isPublicClass)
        .flatMap(klass -> klass.getDeclaredMethods().stream())
        .filter(this::isPublicMethod)
        .map(m -> new DefaultEntrypoint(m, cha))
        .collect(Collectors.toList());
  }

  /**
   * Checks if a class is public and from Application loader only.
   */
  private boolean isPublicClass(IClass klass) {
    return isApplicationLoader(klass)
        && !klass.isInterface()
        && klass.isPublic();
  }

  /**
   * Checks if a method is public and non-abstract from Application loader.
   */
  private boolean isPublicMethod(IMethod method) {
    return isApplicationLoader(method.getDeclaringClass())
        && method.isPublic()
        && !method.isAbstract();
  }

  /**
   * Checks if class is from Application loader only.
   */
  private boolean isApplicationLoader(IClass klass) {
    ClassLoaderReference ref = klass.getClassLoader().getReference();
    return ref.equals(ClassLoaderReference.Application);
  }

  /**
   * Formats a method to URI format: package/Class.method:descriptor
   */
  public static String formatMethod(TypeName type, Selector selector) {
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
   * Checks if target URI should be skipped (null or fake clinit).
   */
  private static boolean shouldSkipTarget(String targetUri) {
    return targetUri == null || 
           targetUri.equals("com/ibm/wala/FakeRootClass.fakeWorldClinit:()V");
  }

  /**
   * Normalizes source URI, converting fake root methods to <boot>.
   */
  private static String normalizeSourceUri(String sourceUri) {
    if (sourceUri.equals("com/ibm/wala/FakeRootClass.fakeRootMethod:()V") ||
        sourceUri.equals("com/ibm/wala/FakeRootClass.fakeWorldClinit:()V")) {
      return "<boot>";
    }
    return sourceUri;
  }

  /**
   * Checks if edge should be filtered based on RT (Primordial) loader.
   */
  private static boolean shouldFilterRtEdge(IMethod sourceMethod, IMethod targetMethod, boolean includeRt) {
    if (includeRt) {
      return false; // Don't filter anything
    }
    
    ClassLoaderReference sourceLoader = sourceMethod.getDeclaringClass().getClassLoader().getReference();
    ClassLoaderReference targetLoader = targetMethod.getDeclaringClass().getClassLoader().getReference();
    
    boolean sourceIsPrimordial = sourceLoader.equals(ClassLoaderReference.Primordial);
    boolean targetIsPrimordial = targetLoader.equals(ClassLoaderReference.Primordial);
    
    // Filter out if either source or target is Primordial
    return sourceIsPrimordial || targetIsPrimordial;
  }

  /**
   * Extracts call graph edges as a stream for processing.
   * 
   * @param cg Call graph to extract edges from
   * @param includeRt if false, filters out edges where source or target is from Primordial loader
   * @return Stream of call graph edges (lazy evaluation)
   */
  public static Stream<CallGraphEdge> extractEdgesAsStream(CallGraph cg, boolean includeRt) {
    return StreamSupport.stream(
        Spliterators.spliteratorUnknownSize(cg.iterator(), Spliterator.ORDERED),
        false
      )
      .flatMap(sourceNode -> {
        IMethod sourceMethod = sourceNode.getMethod();
        TypeName sourceType = sourceMethod.getDeclaringClass().getName();
        Selector sourceSelector = sourceMethod.getSelector();
        String sourceUri = formatMethod(sourceType, sourceSelector);
        
        // Skip if source cannot be formatted
        if (sourceUri == null) {
          return Stream.empty();
        }
        
        // Normalize source (convert fake root to <boot>)
        String normalizedSourceUri = normalizeSourceUri(sourceUri);
        
        // Get successor nodes (targets) for this source
        Iterator<CGNode> targets = cg.getSuccNodes(sourceNode);
        
        return StreamSupport.stream(
            Spliterators.spliteratorUnknownSize(targets, Spliterator.ORDERED),
            false
          )
          .map(targetNode -> {
            IMethod targetMethod = targetNode.getMethod();
            TypeName targetType = targetMethod.getDeclaringClass().getName();
            Selector targetSelector = targetMethod.getSelector();
            String targetUri = formatMethod(targetType, targetSelector);
            
            return new EdgeCandidate(normalizedSourceUri, targetUri, sourceMethod, targetMethod);
          })
          .filter(candidate -> !shouldSkipTarget(candidate.targetUri))
          .filter(candidate -> !shouldFilterRtEdge(candidate.sourceMethod, candidate.targetMethod, includeRt))
          .map(candidate -> new CallGraphEdge(candidate.sourceUri, candidate.targetUri));
      });
  }

  /**
   * Temporary holder for edge candidate during stream processing.
   */
  private static class EdgeCandidate {
    final String sourceUri;
    final String targetUri;
    final IMethod sourceMethod;
    final IMethod targetMethod;
    
    EdgeCandidate(String sourceUri, String targetUri, IMethod sourceMethod, IMethod targetMethod) {
      this.sourceUri = sourceUri;
      this.targetUri = targetUri;
      this.sourceMethod = sourceMethod;
      this.targetMethod = targetMethod;
    }
  }

  /**
   * Represents a call graph edge.
   */
  public static class CallGraphEdge {
    public final String source;
    public final String target;

    public CallGraphEdge(String source, String target) {
      this.source = source;
      this.target = target;
    }
  }
}
