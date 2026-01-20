package io.drmir.asm;

import java.util.Objects;

/**
 * Uniquely identifies a method by its owner class, name, and descriptor.
 */
public final class MethodSignature {

  private final String owner;
  private final String name;
  private final String descriptor;

  /**
   * Creates a method signature.
   *
   * @param owner class name in internal format (e.g., "java/lang/String")
   * @param name method name
   * @param descriptor method descriptor (e.g., "(Ljava/lang/String;)V")
   */
  public MethodSignature(String owner, String name, String descriptor) {
    this.owner = owner;
    this.name = name;
    this.descriptor = descriptor;
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
