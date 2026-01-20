package io.drmir.asm;

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

  public int getOpcode() {
    return opcode;
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

  public boolean isInterface() {
    return isInterface;
  }

  /**
   * Returns true if this is a static call (INVOKESTATIC).
   */
  public boolean isStatic() {
    return opcode == Opcodes.INVOKESTATIC;
  }

  /**
   * Returns true if this is a special call (INVOKESPECIAL - constructors, super, private).
   */
  public boolean isSpecial() {
    return opcode == Opcodes.INVOKESPECIAL;
  }

  /**
   * Returns true if this is a virtual/interface call requiring CHA resolution.
   */
  public boolean isVirtual() {
    return opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKEINTERFACE;
  }

  /**
   * Creates a method signature for the target of this call site.
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
