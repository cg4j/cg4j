package io.drmir.asm;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ASM MethodVisitor that extracts call sites and instantiated types from method bytecode.
 */
public final class CallSiteExtractor extends MethodVisitor {

  private final List<CallSite> callSites = new ArrayList<>();
  private final Set<String> instantiatedTypes = new HashSet<>();

  /**
   * Creates a call site extractor.
   */
  public CallSiteExtractor() {
    super(Opcodes.ASM9);
  }

  @Override
  public void visitMethodInsn(int opcode, String owner, String name, String descriptor,
                              boolean isInterface) {
    // Skip lambda metafactory calls (INVOKEDYNAMIC bootstrap target)
    if (owner.equals("java/lang/invoke/LambdaMetafactory")) {
      return;
    }

    callSites.add(new CallSite(opcode, owner, name, descriptor, isInterface));
  }

  @Override
  public void visitTypeInsn(int opcode, String type) {
    if (opcode == Opcodes.NEW) {
      instantiatedTypes.add(type);
    }
  }

  /**
   * Returns the extracted call sites.
   */
  public List<CallSite> getCallSites() {
    return callSites;
  }

  /**
   * Returns the types instantiated via NEW instructions.
   */
  public Set<String> getInstantiatedTypes() {
    return instantiatedTypes;
  }
}
