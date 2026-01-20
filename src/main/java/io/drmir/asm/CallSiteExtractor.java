package io.drmir.asm;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.List;

/**
 * ASM MethodVisitor that extracts call sites from method bytecode.
 */
public final class CallSiteExtractor extends MethodVisitor {

  private final List<CallSite> callSites = new ArrayList<>();

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

  /**
   * Returns the extracted call sites.
   */
  public List<CallSite> getCallSites() {
    return callSites;
  }
}
