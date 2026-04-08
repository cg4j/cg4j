package net.cg4j.asm;

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
  private final boolean hasClinit;

  /**
   * Creates class info.
   *
   * @param name class name in internal format
   * @param superName superclass name in internal format (null for java/lang/Object)
   * @param interfaces interface names in internal format
   * @param methods declared methods
   * @param access class access flags
   * @param loaderType the class loader this class belongs to
   * @param hasClinit true if the class has a static initializer
   */
  public ClassInfo(String name, String superName, Set<String> interfaces,
                   Set<MethodSignature> methods, int access, ClassLoaderType loaderType,
                   boolean hasClinit) {
    this.name = name;
    this.superName = superName;
    this.interfaces = Collections.unmodifiableSet(new HashSet<>(interfaces));
    this.methods = Collections.unmodifiableSet(new HashSet<>(methods));
    this.access = access;
    this.loaderType = loaderType;
    this.hasClinit = hasClinit;
  }

  /**
   * Returns the internal class name.
   *
   * @return the internal class name
   */
  public String getName() {
    return name;
  }

  /**
   * Returns the internal superclass name.
   *
   * @return the superclass name, or {@code null} for {@code java/lang/Object}
   */
  public String getSuperName() {
    return superName;
  }

  /**
   * Returns the directly implemented interfaces.
   *
   * @return the immutable set of direct interfaces
   */
  public Set<String> getInterfaces() {
    return interfaces;
  }

  /**
   * Returns the declared methods.
   *
   * @return the immutable set of declared methods
   */
  public Set<MethodSignature> getMethods() {
    return methods;
  }

  /**
   * Returns the raw ASM access flags.
   *
   * @return the class access flags
   */
  public int getAccess() {
    return access;
  }

  /**
   * Returns the class loader scope for this class.
   *
   * @return the class loader type
   */
  public ClassLoaderType getLoaderType() {
    return loaderType;
  }

  /**
   * Returns true if this class has a static initializer ({@code <clinit>}).
   *
   * @return {@code true} if the class declares a static initializer
   */
  public boolean hasClinit() {
    return hasClinit;
  }

  /**
   * Returns true if this class is public.
   *
   * @return {@code true} if the class is public
   */
  public boolean isPublic() {
    return (access & org.objectweb.asm.Opcodes.ACC_PUBLIC) != 0;
  }

  /**
   * Returns true if this class is an interface.
   *
   * @return {@code true} if the class is an interface
   */
  public boolean isInterface() {
    return (access & org.objectweb.asm.Opcodes.ACC_INTERFACE) != 0;
  }

  /**
   * Returns true if this class is abstract.
   *
   * @return {@code true} if the class is abstract
   */
  public boolean isAbstract() {
    return (access & org.objectweb.asm.Opcodes.ACC_ABSTRACT) != 0;
  }

  /**
   * Checks if this class declares the given method.
   *
   * @param methodName the method name
   * @param descriptor the method descriptor
   * @return {@code true} if the class declares the method
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
   *
   * @param methodName the method name
   * @param descriptor the method descriptor
   * @return the declared method, or {@code null} if absent
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
