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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Builds call graphs using ASM with RTA-based virtual call resolution.
 */
public final class AsmCallGraphBuilder {

  private static final Logger logger = LogManager.getLogger(AsmCallGraphBuilder.class);
  private static final int PARSE_OPTIONS = ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES;
  private static final String LAMBDA_CLASS_PREFIX = "wala/lambda$";
  private static final MethodSignature BOOT_METHOD =
      new MethodSignature("<boot>", "fakeRoot", "()V");
  private static final MethodSignature FAKE_WORLD_CLINIT_METHOD =
      new MethodSignature("<boot>", "fakeWorldClinit", "()V");
  private static final List<String> PRE_ALLOCATED_TYPES = List.of(
      "java/lang/Object",
      "java/lang/ArithmeticException",
      "java/lang/ArrayStoreException",
      "java/lang/ClassCastException",
      "java/lang/ClassNotFoundException",
      "java/lang/IndexOutOfBoundsException",
      "java/lang/NegativeArraySizeException",
      "java/lang/ExceptionInInitializerError",
      "java/lang/NullPointerException"
  );

  private ClassHierarchy hierarchy;
  private Map<String, byte[]> classBytecode;
  private JarScanner.PrimordialBytecodeLoader primordialLoader;
  private final Map<String, Integer> lambdaCounters = new HashMap<>();

  /**
   * Builds a call graph for the given JAR file using RTA algorithm.
   *
   * @param jarFile the target JAR to analyze
   * @param dependencies list of dependency JAR files
   * @param includeRt whether to include JDK classes in the output
   * @param exclusions scope exclusions to filter Primordial classes from hierarchy
   * @return the call graph result
   */
  public CallGraphResult buildCallGraph(String jarFile, List<File> dependencies,
                                        boolean includeRt, ScopeExclusions exclusions)
      throws IOException {
    logger.info("Loading classes...");

    Map<String, ClassInfo> allClasses = new HashMap<>();
    classBytecode = new HashMap<>();

    Map<String, ClassInfo> jdkClasses = JarScanner.scanJdk();
    allClasses.putAll(jdkClasses);
    logger.info("Loaded {} JDK classes", jdkClasses.size());

    if (dependencies != null && !dependencies.isEmpty()) {
      Map<String, ClassInfo> depClasses =
          JarScanner.scanJars(dependencies, ClassLoaderType.EXTENSION);
      allClasses.putAll(depClasses);
      loadBytecode(dependencies, classBytecode);
      logger.info("Loaded {} dependency classes", depClasses.size());
    }

    File targetJar = new File(jarFile);
    Map<String, ClassInfo> appClasses = JarScanner.scanJar(targetJar, ClassLoaderType.APPLICATION);
    allClasses.putAll(appClasses);
    loadBytecode(List.of(targetJar), classBytecode);
    logger.info("Loaded {} application classes", appClasses.size());

    int beforeSize = allClasses.size();
    allClasses = exclusions.applyExclusions(allClasses);
    int excludedCount = beforeSize - allClasses.size();
    if (excludedCount > 0) {
      logger.info("Scope reduced to {} classes ({} excluded, {} patterns)",
          allClasses.size(), excludedCount, exclusions.patternCount());
    }

    logger.info("Building class hierarchy...");
    hierarchy = new ClassHierarchy(allClasses);
    logger.info("Class hierarchy built with {} classes", hierarchy.size());

    Set<MethodSignature> entryPoints = findEntryPoints(appClasses);
    logger.info("Found {} entry points", entryPoints.size());

    if (entryPoints.isEmpty()) {
      throw new RuntimeException("No entry points found in JAR file");
    }

    logger.info("Running worklist algorithm with RTA...");
    WorklistResult result;
    try (JarScanner.PrimordialBytecodeLoader loader = JarScanner.createPrimordialLoader()) {
      primordialLoader = loader;
      result = runWorklist(entryPoints);
    } finally {
      primordialLoader = null;
    }

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
      if (!classInfo.isPublic() || classInfo.isInterface()) {
        continue;
      }

      for (MethodSignature method : classInfo.getMethods()) {
        if (method.isPublic() && !method.isAbstract()) {
          entryPoints.add(method);
        }
      }
    }

    return entryPoints;
  }

  /**
   * Runs the worklist algorithm to compute reachable methods and call edges.
   */
  private WorklistResult runWorklist(Set<MethodSignature> entryPoints) {
    RtaState state = new RtaState();

    for (MethodSignature entryPoint : entryPoints) {
      state.edges.add(new CallGraphResult.Edge(BOOT_METHOD, entryPoint));
      state.methodQueue.add(entryPoint);

      if (!entryPoint.isStatic()) {
        handleNewAllocation(entryPoint.getOwner(), state);
      }
    }

    for (String preAllocatedType : PRE_ALLOCATED_TYPES) {
      handleNewAllocation(preAllocatedType, state);
    }

    int processedCount = 0;
    while (!state.methodQueue.isEmpty() || !state.receiverEvents.isEmpty()) {
      while (!state.methodQueue.isEmpty()) {
        MethodSignature method = state.methodQueue.removeFirst();
        if (!state.reachable.add(method)) {
          continue;
        }

        processedCount++;
        if (processedCount % 1000 == 0) {
          logger.debug("Processed {} methods, pending methods: {}, pending receiver events: {}",
              processedCount, state.methodQueue.size(), state.receiverEvents.size());
        }

        processReachableMethod(method, state);
      }

      while (!state.receiverEvents.isEmpty()) {
        ReceiverEvent event = state.receiverEvents.removeFirst();
        for (VirtualCallUse use :
            state.virtualCallIndex.getOrDefault(event.selector, Collections.emptyList())) {
          tryDispatch(use, event.concreteType, state);
        }
      }
    }

    logger.info("Worklist complete: {} reachable methods, {} edges",
        state.reachable.size(), state.edges.size());
    return new WorklistResult(state.reachable, state.edges);
  }

  /**
   * Processes the bytecode summary for a newly reachable method.
   */
  private void processReachableMethod(MethodSignature method, RtaState state) {
    MethodAnalysisResult analysisResult = analyzeMethod(method);

    for (String instantiatedType : analysisResult.instantiatedTypes) {
      handleNewAllocation(instantiatedType, state);
    }

    for (CallSite callSite : analysisResult.callSites) {
      processCallSite(method, callSite, state);
    }

    for (String fieldOwner : analysisResult.staticFieldOwners) {
      processClassInitializer(fieldOwner, state);
    }

    for (LambdaCallSite lambdaCallSite : analysisResult.lambdaCallSites) {
      processLambdaCallSite(method, lambdaCallSite, state);
    }
  }

  /**
   * Processes a single call site according to its JVM dispatch semantics.
   */
  private void processCallSite(MethodSignature caller, CallSite callSite, RtaState state) {
    if (callSite.isStatic()) {
      for (MethodSignature target : hierarchy.resolveStaticOrSpecialCall(
          callSite.getOwner(), callSite.getName(), callSite.getDescriptor())) {
        addResolvedTarget(caller, target, state);
      }
      processClassInitializer(callSite.getOwner(), state);
      return;
    }

    if (callSite.isSpecial()) {
      for (MethodSignature target : hierarchy.resolveStaticOrSpecialCall(
          callSite.getOwner(), callSite.getName(), callSite.getDescriptor())) {
        addResolvedTarget(caller, target, state);
      }
      return;
    }

    if (!callSite.isVirtual()) {
      return;
    }

    SelectorKey selector = SelectorKey.from(callSite);
    VirtualCallUse use = new VirtualCallUse(caller, callSite);
    state.virtualCallIndex.computeIfAbsent(selector, key -> new ArrayList<>()).add(use);

    for (String receiverType :
        state.selectorToReceiverTypes.getOrDefault(selector, Collections.emptySet())) {
      tryDispatch(use, receiverType, state);
    }
  }

  /**
   * Processes a newly allocated concrete type and registers it against selector buckets.
   */
  private void handleNewAllocation(String allocatedType, RtaState state) {
    ClassInfo klass = hierarchy.getClass(allocatedType);
    if (klass == null || klass.isInterface() || klass.isAbstract()) {
      return;
    }

    if (!state.allocatedTypes.add(allocatedType)) {
      return;
    }

    registerImplementedMethods(allocatedType, allocatedType, state);
    for (String iface : hierarchy.getAllImplementedInterfaces(allocatedType)) {
      registerImplementedMethods(iface, allocatedType, state);
    }

    String superName = klass.getSuperName();
    while (superName != null) {
      registerImplementedMethods(superName, allocatedType, state);
      ClassInfo superClass = hierarchy.getClass(superName);
      if (superClass == null) {
        break;
      }
      superName = superClass.getSuperName();
    }

    processClassInitializer(allocatedType, state);
  }

  /**
   * Registers selector-to-concrete-type membership for all methods declared on a type.
   */
  private void registerImplementedMethods(String declarerType, String concreteType, RtaState state) {
    ClassInfo declarer = hierarchy.getClass(declarerType);
    if (declarer == null) {
      return;
    }

    for (MethodSignature method : declarer.getMethods()) {
      SelectorKey selector = SelectorKey.from(method);
      Set<String> bucket = state.selectorToReceiverTypes.computeIfAbsent(
          selector, key -> new HashSet<>());
      if (bucket.add(concreteType)) {
        state.receiverEvents.addLast(new ReceiverEvent(selector, concreteType));
      }
    }
  }

  /**
   * Resolves a virtual call site for a newly discovered receiver type.
   */
  private void tryDispatch(VirtualCallUse use, String concreteType, RtaState state) {
    if (!use.processedTypes.add(concreteType)) {
      return;
    }

    if (!hierarchy.isAssignableTo(concreteType, use.callSite.getOwner())) {
      return;
    }

    MethodSignature target = hierarchy.resolveVirtualTarget(
        concreteType, use.callSite.getName(), use.callSite.getDescriptor());
    if (target == null || target.isAbstract()) {
      return;
    }

    addResolvedTarget(use.caller, target, state);
  }

  /**
   * Adds a resolved call graph edge and enqueues the target if it becomes reachable.
   */
  private void addResolvedTarget(MethodSignature caller, MethodSignature target, RtaState state) {
    if (state.edges.add(new CallGraphResult.Edge(caller, target))
        && !isCloneMethod(target)
        && !state.reachable.contains(target)) {
      state.methodQueue.add(target);
    }
  }

  /**
   * Triggers a class initializer and its superclass initializers exactly once.
   */
  private void processClassInitializer(String className, RtaState state) {
    ClassInfo klass = hierarchy.getClass(className);
    if (klass == null || !state.processedClinits.add(className)) {
      return;
    }

    if (klass.hasClinit()) {
      MethodSignature clinit = new MethodSignature(className, "<clinit>", "()V");
      if (state.edges.add(new CallGraphResult.Edge(FAKE_WORLD_CLINIT_METHOD, clinit))
          && !state.reachable.contains(clinit)) {
        state.methodQueue.add(clinit);
      }
    }

    if (klass.getSuperName() != null) {
      processClassInitializer(klass.getSuperName(), state);
    }
  }

  /**
   * Result of analyzing a method's bytecode.
   */
  private static class MethodAnalysisResult {
    final List<CallSite> callSites;
    final Set<String> instantiatedTypes;
    final Set<String> staticFieldOwners;
    final List<LambdaCallSite> lambdaCallSites;

    MethodAnalysisResult(List<CallSite> callSites, Set<String> instantiatedTypes,
                         Set<String> staticFieldOwners,
                         List<LambdaCallSite> lambdaCallSites) {
      this.callSites = callSites;
      this.instantiatedTypes = instantiatedTypes;
      this.staticFieldOwners = staticFieldOwners;
      this.lambdaCallSites = lambdaCallSites;
    }
  }

  /**
   * Analyzes a method's bytecode to extract call sites, allocations, and static field accesses.
   * Lazily loads primordial (JDK) bytecode on demand if not already cached.
   */
  private MethodAnalysisResult analyzeMethod(MethodSignature method) {
    byte[] bytecode = classBytecode.get(method.getOwner());
    if (bytecode == null && primordialLoader != null) {
      bytecode = primordialLoader.loadBytecode(method.getOwner());
      if (bytecode != null) {
        classBytecode.put(method.getOwner(), bytecode);
      }
    }
    if (bytecode == null) {
      return new MethodAnalysisResult(List.of(), Set.of(), Set.of(), List.of());
    }

    ClassReader reader = new ClassReader(bytecode);
    MethodAnalysisVisitor visitor =
        new MethodAnalysisVisitor(method.getName(), method.getDescriptor());
    reader.accept(visitor, PARSE_OPTIONS);
    return visitor.getResult();
  }

  /**
   * Processes a single lambda INVOKEDYNAMIC call site by creating a synthetic lambda class
   * and edges matching WALA's two-hop pattern: caller -> SAM, SAM -> impl.
   */
  private void processLambdaCallSite(MethodSignature caller, LambdaCallSite lambda, RtaState state) {
    String syntheticName = generateLambdaClassName(caller.getOwner());

    ClassInfo ownerClass = hierarchy.getClass(caller.getOwner());
    ClassLoaderType loaderType = ownerClass != null
        ? ownerClass.getLoaderType() : ClassLoaderType.APPLICATION;

    MethodSignature samMethod = new MethodSignature(
        syntheticName, lambda.getSamMethodName(), lambda.getSamDescriptor(),
        Opcodes.ACC_PUBLIC);

    String functionalInterface = extractFunctionalInterface(lambda);
    Set<String> interfaces = functionalInterface != null
        ? Collections.singleton(functionalInterface) : Collections.emptySet();

    ClassInfo syntheticClass = new ClassInfo(
        syntheticName,
        "java/lang/Object",
        interfaces,
        Collections.singleton(samMethod),
        Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC,
        loaderType,
        false);

    hierarchy.registerSyntheticClass(syntheticClass);
    handleNewAllocation(syntheticName, state);

    addResolvedTarget(caller, samMethod, state);

    MethodSignature implMethod = new MethodSignature(
        lambda.getImplOwner(), lambda.getImplName(), lambda.getImplDescriptor());
    addResolvedTarget(samMethod, implMethod, state);
  }

  /**
   * Generates a WALA-compatible synthetic lambda class name.
   * Format: wala/lambda$owner_with_slashes_as_dollars$index
   */
  private String generateLambdaClassName(String ownerClass) {
    int index = lambdaCounters.merge(ownerClass, 0, (old, value) -> old + 1);
    String encodedOwner = ownerClass.replace('/', '$');
    return LAMBDA_CLASS_PREFIX + encodedOwner + "$" + index;
  }

  /**
   * Extracts the functional interface class name from the invokedynamic descriptor's return type.
   */
  private String extractFunctionalInterface(LambdaCallSite lambda) {
    String indyDesc = lambda.getIndyDescriptor();
    if (indyDesc == null) {
      return null;
    }

    int closeParenIndex = indyDesc.lastIndexOf(')');
    if (closeParenIndex < 0 || closeParenIndex + 1 >= indyDesc.length()) {
      return null;
    }

    String returnType = indyDesc.substring(closeParenIndex + 1);
    if (returnType.startsWith("L") && returnType.endsWith(";")) {
      return returnType.substring(1, returnType.length() - 1);
    }

    return null;
  }

  /**
   * Returns true for the special clone() dispatch that WALA keeps only as an edge.
   */
  private boolean isCloneMethod(MethodSignature method) {
    return method.getOwner().equals("java/lang/Object")
        && method.getName().equals("clone")
        && method.getDescriptor().equals("()Ljava/lang/Object;");
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

      boolean sourceIsPrimordial = sourceClass != null
          && sourceClass.getLoaderType() == ClassLoaderType.PRIMORDIAL;
      boolean targetIsPrimordial = targetClass != null
          && targetClass.getLoaderType() == ClassLoaderType.PRIMORDIAL;

      boolean sourceIsBoot = edge.getSource().getOwner().equals("<boot>");

      if (!sourceIsPrimordial && !targetIsPrimordial && !sourceIsBoot) {
        filtered.add(edge);
      } else if (sourceIsBoot && !targetIsPrimordial) {
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
   * Mutable state for the RTA worklist.
   */
  private static final class RtaState {
    private final Set<MethodSignature> reachable = new HashSet<>();
    private final Set<CallGraphResult.Edge> edges = new HashSet<>();
    private final Deque<MethodSignature> methodQueue = new ArrayDeque<>();
    private final Set<String> allocatedTypes = new HashSet<>();
    private final Set<String> processedClinits = new HashSet<>();
    private final Map<SelectorKey, Set<String>> selectorToReceiverTypes = new HashMap<>();
    private final Map<SelectorKey, List<VirtualCallUse>> virtualCallIndex = new HashMap<>();
    private final Deque<ReceiverEvent> receiverEvents = new ArrayDeque<>();
  }

  /**
   * Records that a selector bucket gained a new concrete receiver type.
   */
  private static final class ReceiverEvent {
    private final SelectorKey selector;
    private final String concreteType;

    ReceiverEvent(SelectorKey selector, String concreteType) {
      this.selector = selector;
      this.concreteType = concreteType;
    }
  }

  /**
   * Tracks which receiver types have already been processed for a virtual call site.
   */
  private static final class VirtualCallUse {
    private final MethodSignature caller;
    private final CallSite callSite;
    private final Set<String> processedTypes = new HashSet<>();

    VirtualCallUse(MethodSignature caller, CallSite callSite) {
      this.caller = caller;
      this.callSite = callSite;
    }
  }

  /**
   * Selector identity used by the WALA-style receiver buckets.
   */
  private static final class SelectorKey {
    private final String name;
    private final String descriptor;

    private SelectorKey(String name, String descriptor) {
      this.name = name;
      this.descriptor = descriptor;
    }

    static SelectorKey from(CallSite callSite) {
      return new SelectorKey(callSite.getName(), callSite.getDescriptor());
    }

    static SelectorKey from(MethodSignature method) {
      return new SelectorKey(method.getName(), method.getDescriptor());
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (!(other instanceof SelectorKey)) {
        return false;
      }
      SelectorKey that = (SelectorKey) other;
      return Objects.equals(name, that.name)
          && Objects.equals(descriptor, that.descriptor);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, descriptor);
    }
  }

  /**
   * ClassVisitor that extracts call sites and allocations from a specific method.
   */
  private static class MethodAnalysisVisitor extends ClassVisitor {
    private final String targetMethodName;
    private final String targetDescriptor;
    private List<CallSite> callSites = List.of();
    private Set<String> instantiatedTypes = Set.of();
    private Set<String> staticFieldOwners = Set.of();
    private List<LambdaCallSite> lambdaCallSites = List.of();

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
        staticFieldOwners = extractor.getStaticFieldOwners();
        lambdaCallSites = extractor.getLambdaCallSites();
        return extractor;
      }
      return null;
    }

    MethodAnalysisResult getResult() {
      return new MethodAnalysisResult(
          callSites, instantiatedTypes, staticFieldOwners, lambdaCallSites);
    }
  }
}
