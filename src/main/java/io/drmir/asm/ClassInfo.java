package io.drmir.asm;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Stores metadata about a class extracted from bytecode.
 */
public final class ClassInfo {

  private final String name;
  private final String superName;
  private final Set<String> interfaces;
  private final Set<MethodSignature> methods;
  private final int access;
  private final ClassLoaderType loaderType;

  /**
   * Creates class info.
   *
   * @param name class name in internal format
   * @param superName superclass name in internal format (null for java/lang/Object)
   * @param interfaces interface names in internal format
   * @param methods declared methods
   * @param access class access flags
   * @param loaderType the class loader this class belongs to
   */
  public ClassInfo(String name, String superName, Set<String> interfaces,
                   Set<MethodSignature> methods, int access, ClassLoaderType loaderType) {
    this.name = name;
    this.superName = superName;
    this.interfaces = Collections.unmodifiableSet(new HashSet<>(interfaces));
    this.methods = Collections.unmodifiableSet(new HashSet<>(methods));
    this.access = access;
    this.loaderType = loaderType;
  }

  public String getName() {
    return name;
  }

  public String getSuperName() {
    return superName;
  }

  public Set<String> getInterfaces() {
    return interfaces;
  }

  public Set<MethodSignature> getMethods() {
    return methods;
  }

  public int getAccess() {
    return access;
  }

  public ClassLoaderType getLoaderType() {
    return loaderType;
  }

  /**
   * Returns true if this class is public.
   */
  public boolean isPublic() {
    return (access & org.objectweb.asm.Opcodes.ACC_PUBLIC) != 0;
  }

  /**
   * Returns true if this class is an interface.
   */
  public boolean isInterface() {
    return (access & org.objectweb.asm.Opcodes.ACC_INTERFACE) != 0;
  }

  /**
   * Returns true if this class is abstract.
   */
  public boolean isAbstract() {
    return (access & org.objectweb.asm.Opcodes.ACC_ABSTRACT) != 0;
  }

  /**
   * Checks if this class declares the given method.
   */
  public boolean hasMethod(String methodName, String descriptor) {
    for (MethodSignature m : methods) {
      if (m.getName().equals(methodName) && m.getDescriptor().equals(descriptor)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Gets a method signature if declared in this class.
   */
  public MethodSignature getMethod(String methodName, String descriptor) {
    for (MethodSignature m : methods) {
      if (m.getName().equals(methodName) && m.getDescriptor().equals(descriptor)) {
        return m;
      }
    }
    return null;
  }

  @Override
  public String toString() {
    return name + " (loader=" + loaderType + ", methods=" + methods.size() + ")";
  }
}
