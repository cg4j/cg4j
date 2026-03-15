package net.cg4j.asm;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Maintains class hierarchy information and resolves virtual calls using RTA.
 */
public final class ClassHierarchy {

  private static final Logger logger = LogManager.getLogger(ClassHierarchy.class);

  private final Map<String, ClassInfo> classes;
  private final Map<String, Set<String>> subclasses;
  private final Map<String, Set<String>> implementors;
  private final Map<String, Set<String>> subtypesCache;
  private final Map<String, Set<String>> implementedInterfacesCache;

  /**
   * Creates a class hierarchy from class information.
   *
   * @param classes map of class name to ClassInfo
   */
  public ClassHierarchy(Map<String, ClassInfo> classes) {
    this.classes = new HashMap<>(classes);
    this.subclasses = new HashMap<>();
    this.implementors = new HashMap<>();
    this.subtypesCache = new HashMap<>();
    this.implementedInterfacesCache = new HashMap<>();
    buildReverseRelations();
  }

  /**
   * Builds reverse relations (subclass and implementor maps).
   */
  private void buildReverseRelations() {
    for (ClassInfo info : classes.values()) {
      // Track subclass relation
      String superName = info.getSuperName();
      if (superName != null) {
        subclasses.computeIfAbsent(superName, k -> new HashSet<>()).add(info.getName());
      }

      // Track implementor relation for interfaces
      for (String iface : info.getInterfaces()) {
        implementors.computeIfAbsent(iface, k -> new HashSet<>()).add(info.getName());
      }
    }
  }

  /**
   * Returns the ClassInfo for a class name, or null if not found.
   */
  public ClassInfo getClass(String name) {
    return classes.get(name);
  }

  /**
   * Returns true if the class exists in the hierarchy.
   */
  public boolean hasClass(String name) {
    return classes.containsKey(name);
  }

  /**
   * Returns all classes in the hierarchy.
   */
  public Map<String, ClassInfo> getAllClasses() {
    return Collections.unmodifiableMap(classes);
  }

  /**
   * Returns direct subclasses of a class.
   */
  public Set<String> getDirectSubclasses(String className) {
    return subclasses.getOrDefault(className, Collections.emptySet());
  }

  /**
   * Returns direct implementors of an interface.
   */
  public Set<String> getDirectImplementors(String interfaceName) {
    return implementors.getOrDefault(interfaceName, Collections.emptySet());
  }

  /**
   * Returns all subtypes (transitive) of a type, including itself.
   * Results are cached for performance.
   */
  public Set<String> getAllSubtypes(String typeName) {
    return subtypesCache.computeIfAbsent(typeName, this::computeAllSubtypes);
  }

  /**
   * Computes all subtypes transitively.
   */
  private Set<String> computeAllSubtypes(String typeName) {
    Set<String> result = new HashSet<>();
    result.add(typeName);
    collectSubtypes(typeName, result);
    return result;
  }

  /**
   * Recursively collects all subtypes.
   */
  private void collectSubtypes(String typeName, Set<String> result) {
    // Collect subclasses
    for (String subclass : getDirectSubclasses(typeName)) {
      if (result.add(subclass)) {
        collectSubtypes(subclass, result);
      }
    }

    // Collect implementors
    for (String impl : getDirectImplementors(typeName)) {
      if (result.add(impl)) {
        collectSubtypes(impl, result);
      }
    }
  }

  /**
   * Looks up a method in a class, walking up the hierarchy if needed.
   *
   * @param className the class to start searching from
   * @param methodName the method name
   * @param descriptor the method descriptor
   * @return the resolved method signature, or null if not found
   */
  public MethodSignature lookupMethod(String className, String methodName, String descriptor) {
    String current = className;

    while (current != null) {
      ClassInfo info = classes.get(current);
      if (info == null) {
        return null; // Class not found in hierarchy
      }

      MethodSignature method = info.getMethod(methodName, descriptor);
      if (method != null) {
        return method;
      }

      current = info.getSuperName();
    }

    return null;
  }

  /**
   * Returns all interfaces implemented by a class or interface, transitively.
   */
  public Set<String> getAllImplementedInterfaces(String className) {
    return implementedInterfacesCache.computeIfAbsent(className, this::computeAllImplementedInterfaces);
  }

  /**
   * Computes all interfaces implemented by a class or interface, transitively.
   */
  private Set<String> computeAllImplementedInterfaces(String className) {
    ClassInfo info = classes.get(className);
    if (info == null) {
      return Collections.emptySet();
    }

    Set<String> interfaces = new LinkedHashSet<>();
    collectInterfaces(info, interfaces);
    return interfaces;
  }

  /**
   * Returns true if {@code concreteType} is assignable to {@code declaredType}.
   */
  public boolean isAssignableTo(String concreteType, String declaredType) {
    if (concreteType.equals(declaredType)) {
      return true;
    }

    ClassInfo concreteInfo = classes.get(concreteType);
    if (concreteInfo == null) {
      return false;
    }

    String current = concreteType;
    while (current != null) {
      if (current.equals(declaredType)) {
        return true;
      }
      ClassInfo currentInfo = classes.get(current);
      if (currentInfo == null) {
        break;
      }
      current = currentInfo.getSuperName();
    }

    return getAllImplementedInterfaces(concreteType).contains(declaredType);
  }

  /**
   * Resolves the concrete target of a virtual/interface dispatch for a receiver type.
   */
  public MethodSignature resolveVirtualTarget(String concreteType, String methodName,
                                              String descriptor) {
    String current = concreteType;

    while (current != null) {
      ClassInfo info = classes.get(current);
      if (info == null) {
        return null;
      }

      MethodSignature method = info.getMethod(methodName, descriptor);
      if (method != null && !method.isAbstract()) {
        return method;
      }

      current = info.getSuperName();
    }

    return lookupDefaultInterfaceMethod(concreteType, methodName, descriptor, new HashSet<>());
  }

  /**
   * Resolves a virtual call using RTA (Rapid Type Analysis).
   * Only considers types that have been instantiated.
   *
   * @param receiverType the declared receiver type
   * @param methodName the method name
   * @param descriptor the method descriptor
   * @param instantiatedTypes set of types that have been instantiated via NEW
   * @return set of possible target methods
   */
  public Set<MethodSignature> resolveVirtualCallRTA(String receiverType, String methodName,
                                                     String descriptor,
                                                     Set<String> instantiatedTypes) {
    Set<MethodSignature> targets = new HashSet<>();

    Set<String> subtypes = getAllSubtypes(receiverType);
    for (String subtype : subtypes) {
      // RTA: only consider instantiated types
      if (!instantiatedTypes.contains(subtype)) {
        continue;
      }

      ClassInfo info = classes.get(subtype);
      if (info == null) {
        continue;
      }

      // Skip abstract classes and interfaces for virtual call targets
      if (info.isAbstract() || info.isInterface()) {
        continue;
      }

      MethodSignature method = resolveVirtualTarget(subtype, methodName, descriptor);
      if (method != null) {
        targets.add(method);
      }
    }

    return targets;
  }

  /**
   * Resolves a call site to its possible targets using RTA.
   *
   * @param callSite the call site to resolve
   * @param instantiatedTypes set of types that have been instantiated via NEW
   * @return set of possible target methods
   */
  public Set<MethodSignature> resolveCallSiteRTA(CallSite callSite, Set<String> instantiatedTypes) {
    if (callSite.isStatic() || callSite.isSpecial()) {
      return resolveStaticOrSpecialCall(callSite.getOwner(), callSite.getName(),
          callSite.getDescriptor());
    } else if (callSite.isVirtual()) {
      return resolveVirtualCallRTA(callSite.getOwner(), callSite.getName(),
          callSite.getDescriptor(), instantiatedTypes);
    }
    // Unknown opcode
    return Collections.emptySet();
  }

  /**
   * Resolves a static or special (constructor/private/super) call.
   * Returns exactly one target method or empty set if not found.
   *
   * @param ownerType the owner class
   * @param methodName the method name
   * @param descriptor the method descriptor
   * @return set containing the target method, or empty set
   */
  public Set<MethodSignature> resolveStaticOrSpecialCall(String ownerType, String methodName,
                                                          String descriptor) {
    MethodSignature method = lookupMethod(ownerType, methodName, descriptor);
    if (method != null) {
      return Collections.singleton(method);
    }
    return Collections.emptySet();
  }

  /**
   * Registers a synthetic class (e.g., lambda) into the hierarchy.
   * Updates reverse relations and invalidates hierarchy caches.
   *
   * @param syntheticClass the synthetic class to register
   */
  public void registerSyntheticClass(ClassInfo syntheticClass) {
    classes.put(syntheticClass.getName(), syntheticClass);

    // Update reverse relations
    if (syntheticClass.getSuperName() != null) {
      subclasses.computeIfAbsent(syntheticClass.getSuperName(), k -> new HashSet<>())
          .add(syntheticClass.getName());
    }
    for (String iface : syntheticClass.getInterfaces()) {
      implementors.computeIfAbsent(iface, k -> new HashSet<>())
          .add(syntheticClass.getName());
    }

    // Invalidate caches — synthetic classes are leaf nodes but affect parent lookups
    subtypesCache.clear();
    implementedInterfacesCache.clear();
  }

  /**
   * Returns the number of classes in the hierarchy.
   */
  public int size() {
    return classes.size();
  }

  /**
   * Collects transitive interfaces for a class.
   */
  private void collectInterfaces(ClassInfo info, Set<String> result) {
    for (String iface : info.getInterfaces()) {
      if (result.add(iface)) {
        ClassInfo ifaceInfo = classes.get(iface);
        if (ifaceInfo != null) {
          collectInterfaces(ifaceInfo, result);
        }
      }
    }

    String superName = info.getSuperName();
    if (superName != null) {
      ClassInfo superInfo = classes.get(superName);
      if (superInfo != null) {
        collectInterfaces(superInfo, result);
      }
    }
  }

  /**
   * Looks for a non-abstract default method on implemented interfaces.
   */
  private MethodSignature lookupDefaultInterfaceMethod(String className, String methodName,
                                                       String descriptor,
                                                       Set<String> visitedInterfaces) {
    ClassInfo info = classes.get(className);
    if (info == null) {
      return null;
    }

    for (String iface : info.getInterfaces()) {
      MethodSignature method =
          lookupInterfaceMethod(iface, methodName, descriptor, visitedInterfaces);
      if (method != null) {
        return method;
      }
    }

    String superName = info.getSuperName();
    if (superName != null) {
      return lookupDefaultInterfaceMethod(superName, methodName, descriptor, visitedInterfaces);
    }

    return null;
  }

  /**
   * Looks for a concrete method declaration on an interface hierarchy.
   */
  private MethodSignature lookupInterfaceMethod(String interfaceName, String methodName,
                                                String descriptor,
                                                Set<String> visitedInterfaces) {
    if (!visitedInterfaces.add(interfaceName)) {
      return null;
    }

    ClassInfo info = classes.get(interfaceName);
    if (info == null) {
      return null;
    }

    MethodSignature method = info.getMethod(methodName, descriptor);
    if (method != null && !method.isAbstract()) {
      return method;
    }

    for (String parentInterface : info.getInterfaces()) {
      MethodSignature inherited =
          lookupInterfaceMethod(parentInterface, methodName, descriptor, visitedInterfaces);
      if (inherited != null) {
        return inherited;
      }
    }

    return null;
  }
}
