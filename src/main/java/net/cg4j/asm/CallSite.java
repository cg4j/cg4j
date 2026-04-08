package net.cg4j.asm;

import org.objectweb.asm.Opcodes;

/**
 * Represents a method invocation instruction found in bytecode.
 */
public final class CallSite {

  private final int opcode;
  private final String owner;
  private final String name;
  private final String descriptor;
  private final boolean isInterface;

  /**
   * Creates a call site.
   *
   * @param opcode the invoke opcode (INVOKEVIRTUAL, INVOKEINTERFACE, etc.)
   * @param owner target class in internal format
   * @param name target method name
   * @param descriptor target method descriptor
   * @param isInterface true if the owner is an interface
   */
  public CallSite(int opcode, String owner, String name, String descriptor, boolean isInterface) {
    this.opcode = opcode;
    this.owner = owner;
    this.name = name;
    this.descriptor = descriptor;
    this.isInterface = isInterface;
  }

  /**
   * Returns the invoke opcode.
   *
   * @return the JVM invoke opcode for this call site
   */
  public int getOpcode() {
    return opcode;
  }

  /**
   * Returns the target owner class.
   *
   * @return the internal owner class name
   */
  public String getOwner() {
    return owner;
  }

  /**
   * Returns the target method name.
   *
   * @return the target method name
   */
  public String getName() {
    return name;
  }

  /**
   * Returns the target method descriptor.
   *
   * @return the JVM method descriptor
   */
  public String getDescriptor() {
    return descriptor;
  }

  /**
   * Returns whether the owner is an interface.
   *
   * @return {@code true} if the owner is an interface
   */
  public boolean isInterface() {
    return isInterface;
  }

  /**
   * Returns true if this is a static call (INVOKESTATIC).
   *
   * @return {@code true} if this call site uses {@code INVOKESTATIC}
   */
  public boolean isStatic() {
    return opcode == Opcodes.INVOKESTATIC;
  }

  /**
   * Returns true if this is a special call (INVOKESPECIAL - constructors, super, private).
   *
   * @return {@code true} if this call site uses {@code INVOKESPECIAL}
   */
  public boolean isSpecial() {
    return opcode == Opcodes.INVOKESPECIAL;
  }

  /**
   * Returns true if this is a virtual/interface call requiring CHA resolution.
   *
   * @return {@code true} if this call requires virtual dispatch resolution
   */
  public boolean isVirtual() {
    return opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKEINTERFACE;
  }

  /**
   * Creates a method signature for the target of this call site.
   *
   * @return the target method signature
   */
  public MethodSignature toMethodSignature() {
    return new MethodSignature(owner, name, descriptor);
  }

  @Override
  public String toString() {
    String opcodeStr;
    switch (opcode) {
      case Opcodes.INVOKEVIRTUAL: opcodeStr = "INVOKEVIRTUAL"; break;
      case Opcodes.INVOKEINTERFACE: opcodeStr = "INVOKEINTERFACE"; break;
      case Opcodes.INVOKESPECIAL: opcodeStr = "INVOKESPECIAL"; break;
      case Opcodes.INVOKESTATIC: opcodeStr = "INVOKESTATIC"; break;
      default: opcodeStr = "INVOKE_" + opcode; break;
    }
    return opcodeStr + " " + owner + "." + name + descriptor;
  }
}
