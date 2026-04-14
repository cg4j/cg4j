package net.cg4j.asm;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/** ASM MethodVisitor that extracts call sites and instantiated types from method bytecode. */
public final class CallSiteExtractor extends MethodVisitor {

  private static final String LAMBDA_METAFACTORY = "java/lang/invoke/LambdaMetafactory";

  private final List<CallSite> callSites = new ArrayList<>();
  private final List<LambdaCallSite> lambdaCallSites = new ArrayList<>();
  private final Set<String> instantiatedTypes = new HashSet<>();
  private final Set<String> staticFieldOwners = new HashSet<>();

  /** Creates a call site extractor. */
  public CallSiteExtractor() {
    super(Opcodes.ASM9);
  }

  @Override
  public void visitMethodInsn(
      int opcode, String owner, String name, String descriptor, boolean isInterface) {
    // Skip lambda metafactory calls (INVOKEDYNAMIC bootstrap target)
    if (owner.equals(LAMBDA_METAFACTORY)) {
      return;
    }

    callSites.add(new CallSite(opcode, owner, name, descriptor, isInterface));
  }

  @Override
  public void visitInvokeDynamicInsn(
      String name,
      String descriptor,
      Handle bootstrapMethodHandle,
      Object... bootstrapMethodArguments) {
    if (!isLambdaMetafactory(bootstrapMethodHandle)) {
      return;
    }

    // bootstrapMethodArguments layout for LambdaMetafactory.metafactory:
    //   [0] Type  - erased SAM method type
    //   [1] Handle - implementation method handle (lambda body)
    //   [2] Type  - specialized SAM method type
    if (bootstrapMethodArguments.length >= 2
        && bootstrapMethodArguments[0] instanceof Type
        && bootstrapMethodArguments[1] instanceof Handle) {
      Type samType = (Type) bootstrapMethodArguments[0];
      Handle implHandle = (Handle) bootstrapMethodArguments[1];
      lambdaCallSites.add(
          new LambdaCallSite(
              name,
              samType.getDescriptor(),
              descriptor,
              implHandle.getOwner(),
              implHandle.getName(),
              implHandle.getDesc(),
              implHandle.getTag()));
    }
  }

  @Override
  public void visitTypeInsn(int opcode, String type) {
    if (opcode == Opcodes.NEW) {
      instantiatedTypes.add(type);
    }
  }

  @Override
  public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
    if (opcode == Opcodes.GETSTATIC || opcode == Opcodes.PUTSTATIC) {
      staticFieldOwners.add(owner);
    }
  }

  /**
   * Returns the extracted call sites.
   *
   * @return the extracted method invocation sites
   */
  public List<CallSite> getCallSites() {
    return callSites;
  }

  /**
   * Returns the lambda/method-reference call sites from INVOKEDYNAMIC instructions.
   *
   * @return the extracted lambda call sites
   */
  public List<LambdaCallSite> getLambdaCallSites() {
    return lambdaCallSites;
  }

  /**
   * Returns the types instantiated via NEW instructions.
   *
   * @return the set of instantiated internal type names
   */
  public Set<String> getInstantiatedTypes() {
    return instantiatedTypes;
  }

  /**
   * Returns the owners of classes whose static fields are accessed.
   *
   * @return the set of internal class names with static field access
   */
  public Set<String> getStaticFieldOwners() {
    return staticFieldOwners;
  }

  /** Checks if a bootstrap method handle is LambdaMetafactory.metafactory or altMetafactory. */
  private static boolean isLambdaMetafactory(Handle handle) {
    return handle.getOwner().equals(LAMBDA_METAFACTORY)
        && (handle.getName().equals("metafactory") || handle.getName().equals("altMetafactory"));
  }
}
