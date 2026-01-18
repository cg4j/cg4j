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

import java.io.File;
import java.io.FileInputStream;
import java.util.Iterator;
import java.util.List;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Builds call graphs using WALA with CHA-based analysis.
 * Based on ml4cgp_study implementation.
 */
public class CallGraphBuilder {

  /**
   * Builds a 0-CFA call graph for the given JAR file.
   */
  public CallGraph buildCallGraph(String jarFile, List<File> dependencies, File exclusionFile) 
      throws Exception {
    
    // Build Class Hierarchy with CHA
    ClassHierarchy cha = constructCHA(jarFile, dependencies, exclusionFile);
    
    // Generate entry points (all public methods in application/library classes)
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
   */
  private ClassHierarchy constructCHA(String programJarFile, 
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
   * Checks if a class is public and from Application or Extension loader.
   */
  private boolean isPublicClass(IClass klass) {
    return isApplicationOrLibrary(klass)
        && !klass.isInterface()
        && klass.isPublic();
  }

  /**
   * Checks if a method is public and non-abstract.
   */
  private boolean isPublicMethod(IMethod method) {
    return isApplicationOrLibrary(method.getDeclaringClass())
        && method.isPublic()
        && !method.isAbstract();
  }

  /**
   * Checks if class is from Application or Extension (library) loader.
   */
  private boolean isApplicationOrLibrary(IClass klass) {
    ClassLoaderReference ref = klass.getClassLoader().getReference();
    return ref.equals(ClassLoaderReference.Application) ||
           ref.equals(ClassLoaderReference.Extension);
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
   * Extracts call graph edges as an iterator for processing.
   */
  public static Iterator<CallGraphEdge> extractEdges(CallGraph cg) {
    return new Iterator<CallGraphEdge>() {
      private final Iterator<CGNode> nodeIterator = cg.iterator();
      private CGNode currentSource;
      private Iterator<CGNode> currentTargets;
      private CallGraphEdge nextEdge;

      @Override
      public boolean hasNext() {
        if (nextEdge != null) return true;
        
        while (true) {
          // If we have targets for current source, try to get next target
          if (currentTargets != null && currentTargets.hasNext()) {
            CGNode targetNode = currentTargets.next();
            IMethod targetMethod = targetNode.getMethod();
            TypeName targetType = targetMethod.getDeclaringClass().getName();
            Selector targetSelector = targetMethod.getSelector();
            
            String targetUri = formatMethod(targetType, targetSelector);
            if (targetUri == null || 
                targetUri.equals("com/ibm/wala/FakeRootClass.fakeWorldClinit:()V")) {
              continue; // Skip this target
            }
            
            // Get source info
            IMethod sourceMethod = currentSource.getMethod();
            TypeName sourceType = sourceMethod.getDeclaringClass().getName();
            Selector sourceSelector = sourceMethod.getSelector();
            String sourceUri = formatMethod(sourceType, sourceSelector);
            if (sourceUri == null) continue;
            
            // Check if boot method
            boolean isBootMethod = sourceUri.equals("com/ibm/wala/FakeRootClass.fakeRootMethod:()V") ||
                                  sourceUri.equals("com/ibm/wala/FakeRootClass.fakeWorldClinit:()V");
            if (isBootMethod) {
              sourceUri = "<boot>";
            }
            
            nextEdge = new CallGraphEdge(sourceUri, targetUri);
            return true;
          }
          
          // Get next source node
          if (nodeIterator.hasNext()) {
            currentSource = nodeIterator.next();
            currentTargets = cg.getSuccNodes(currentSource);
          } else {
            return false; // No more nodes
          }
        }
      }

      @Override
      public CallGraphEdge next() {
        if (!hasNext()) {
          throw new java.util.NoSuchElementException();
        }
        CallGraphEdge edge = nextEdge;
        nextEdge = null;
        return edge;
      }
    };
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
