package net.cg4j.asm;

import org.objectweb.asm.Opcodes;

import java.util.Objects;

/**
 * Uniquely identifies a method by its owner class, name, and descriptor.
 */
public final class MethodSignature {

  private final String owner;
  private final String name;
  private final String descriptor;
  private final int access;

  /**
   * Creates a method signature.
   *
   * @param owner class name in internal format (e.g., "java/lang/String")
   * @param name method name
   * @param descriptor method descriptor (e.g., "(Ljava/lang/String;)V")
   */
  public MethodSignature(String owner, String name, String descriptor) {
    this(owner, name, descriptor, 0);
  }

  /**
   * Creates a method signature with access flags.
   *
   * @param owner class name in internal format (e.g., "java/lang/String")
   * @param name method name
   * @param descriptor method descriptor (e.g., "(Ljava/lang/String;)V")
   * @param access method access flags (e.g., ACC_PUBLIC | ACC_STATIC)
   */
  public MethodSignature(String owner, String name, String descriptor, int access) {
    this.owner = owner;
    this.name = name;
    this.descriptor = descriptor;
    this.access = access;
  }

  public String getOwner() {
    return owner;
  }

  public String getName() {
    return name;
  }

  public String getDescriptor() {
    return descriptor;
  }

  /**
   * Returns the method access flags.
   */
  public int getAccess() {
    return access;
  }

  /**
   * Returns true if this method is public.
   */
  public boolean isPublic() {
    return (access & Opcodes.ACC_PUBLIC) != 0;
  }

  /**
   * Returns true if this method is abstract.
   */
  public boolean isAbstract() {
    return (access & Opcodes.ACC_ABSTRACT) != 0;
  }

  /**
   * Returns true if this method is static.
   */
  public boolean isStatic() {
    return (access & Opcodes.ACC_STATIC) != 0;
  }

  /**
   * Formats the method as URI: owner.name:descriptor
   */
  public String toUri() {
    return owner + "." + name + ":" + descriptor;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    MethodSignature that = (MethodSignature) o;
    return Objects.equals(owner, that.owner)
        && Objects.equals(name, that.name)
        && Objects.equals(descriptor, that.descriptor);
  }

  @Override
  public int hashCode() {
    return Objects.hash(owner, name, descriptor);
  }

  @Override
  public String toString() {
    return toUri();
  }
}
