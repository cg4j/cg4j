package net.cg4j.asm;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ASM method extractor that derives call sites and lightweight receiver type hints.
 */
public final class CallSiteExtractor extends MethodNode {

  private static final String LAMBDA_METAFACTORY = "java/lang/invoke/LambdaMetafactory";
  private static final Logger logger = LogManager.getLogger(CallSiteExtractor.class);

  private final String owner;
  private int lambdaOrdinal;
  private final List<CallSite> callSites = new ArrayList<>();
  private final List<LambdaCallSite> lambdaCallSites = new ArrayList<>();
  private final Set<String> instantiatedTypes = new HashSet<>();
  private final Set<String> staticFieldOwners = new HashSet<>();

  /**
   * Creates a call site extractor.
   */
  public CallSiteExtractor() {
    this("<unknown>", Opcodes.ACC_PUBLIC, "<unknown>", "()V", null, null);
  }

  /**
   * Creates a call site extractor for a concrete method.
   */
  public CallSiteExtractor(String owner, int access, String name, String descriptor,
                           String signature, String[] exceptions) {
    this(owner, access, name, descriptor, signature, exceptions, 0);
  }

  /**
   * Creates a call site extractor for a concrete method with a starting lambda ordinal.
   */
  public CallSiteExtractor(String owner, int access, String name, String descriptor,
                           String signature, String[] exceptions, int startingLambdaOrdinal) {
    super(Opcodes.ASM9, access, name, descriptor, signature, exceptions);
    this.owner = owner;
    this.lambdaOrdinal = startingLambdaOrdinal;
  }

  @Override
  public void visitMethodInsn(int opcode, String owner, String name, String descriptor,
                              boolean isInterface) {
    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);

    // Skip lambda metafactory calls (INVOKEDYNAMIC bootstrap target)
    if (owner.equals(LAMBDA_METAFACTORY)) {
      return;
    }

    callSites.add(new CallSite(opcode, owner, name, descriptor, isInterface));
  }

  @Override
  public void visitInvokeDynamicInsn(String name, String descriptor,
                                     Handle bootstrapMethodHandle,
                                     Object... bootstrapMethodArguments) {
    super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);

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
      lambdaCallSites.add(new LambdaCallSite(
          name,
          samType.getDescriptor(),
          descriptor,
          implHandle.getOwner(),
          implHandle.getName(),
          implHandle.getDesc(),
          implHandle.getTag(),
          lambdaOrdinal
      ));
    }

    lambdaOrdinal++;
  }

  @Override
  public void visitTypeInsn(int opcode, String type) {
    super.visitTypeInsn(opcode, type);

    if (opcode == Opcodes.NEW) {
      instantiatedTypes.add(type);
    }
  }

  @Override
  public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
    super.visitFieldInsn(opcode, owner, name, descriptor);

    if (opcode == Opcodes.GETSTATIC || opcode == Opcodes.PUTSTATIC) {
      staticFieldOwners.add(owner);
    }
  }

  @Override
  public void visitEnd() {
    super.visitEnd();

    callSites.clear();
    try {
      populateCallSitesWithReceiverHints();
    } catch (AnalyzerException e) {
      logger.warn("Failed to infer receiver hints for {}.{}{}: {}",
          owner, name, desc, e.getMessage());
      populateCallSitesWithoutHints();
    }
  }

  /**
   * Returns the extracted call sites.
   */
  public List<CallSite> getCallSites() {
    return callSites;
  }

  /**
   * Returns the lambda/method-reference call sites from INVOKEDYNAMIC instructions.
   */
  public List<LambdaCallSite> getLambdaCallSites() {
    return lambdaCallSites;
  }

  /**
   * Returns the next class-wide lambda ordinal after visiting this method.
   */
  public int getNextLambdaOrdinal() {
    return lambdaOrdinal;
  }

  /**
   * Returns the types instantiated via NEW instructions.
   */
  public Set<String> getInstantiatedTypes() {
    return instantiatedTypes;
  }

  /**
   * Returns the owners of classes whose static fields are accessed.
   */
  public Set<String> getStaticFieldOwners() {
    return staticFieldOwners;
  }

  /**
   * Populates call sites with receiver type hints using ASM frame analysis.
   */
  private void populateCallSitesWithReceiverHints() throws AnalyzerException {
    Analyzer<SourceValue> analyzer = new Analyzer<>(new SourceInterpreter());
    Frame<SourceValue>[] frames = analyzer.analyze(owner, this);

    for (int i = 0; i < instructions.size(); i++) {
      AbstractInsnNode instruction = instructions.get(i);
      if (instruction instanceof MethodInsnNode) {
        MethodInsnNode methodInsn = (MethodInsnNode) instruction;
        if (!methodInsn.owner.equals(LAMBDA_METAFACTORY)) {
          callSites.add(new CallSite(
              methodInsn.getOpcode(),
              methodInsn.owner,
              methodInsn.name,
              methodInsn.desc,
              methodInsn.itf,
              extractReceiverTypeHint(frames[i], methodInsn)
          ));
        }
      }
    }
  }

  /**
   * Falls back to plain call-site extraction when frame analysis is unavailable.
   */
  private void populateCallSitesWithoutHints() {
    for (int i = 0; i < instructions.size(); i++) {
      AbstractInsnNode instruction = instructions.get(i);
      if (instruction instanceof MethodInsnNode) {
        MethodInsnNode methodInsn = (MethodInsnNode) instruction;
        if (!methodInsn.owner.equals(LAMBDA_METAFACTORY)) {
          callSites.add(new CallSite(
              methodInsn.getOpcode(),
              methodInsn.owner,
              methodInsn.name,
              methodInsn.desc,
              methodInsn.itf
          ));
        }
      }
    }
  }

  /**
   * Extracts a more precise receiver type from the frame state immediately before an invocation.
   */
  private String extractReceiverTypeHint(Frame<SourceValue> frame, MethodInsnNode instruction) {
    if (frame == null || instruction.getOpcode() == Opcodes.INVOKESTATIC) {
      return null;
    }

    int receiverStackIndex = frame.getStackSize() - Type.getArgumentTypes(instruction.desc).length - 1;
    if (receiverStackIndex < 0 || receiverStackIndex >= frame.getStackSize()) {
      return null;
    }

    SourceValue receiverValue = frame.getStack(receiverStackIndex);
    if (receiverValue == null) {
      return null;
    }

    return inferReferenceType(receiverValue);
  }

  /**
   * Infers a single reference type from the producers of a source value.
   */
  private String inferReferenceType(SourceValue value) {
    if (value.insns == null || value.insns.isEmpty()) {
      return null;
    }

    String inferredType = null;
    for (AbstractInsnNode sourceInstruction : value.insns) {
      String currentType = inferReferenceType(sourceInstruction);
      if (currentType == null) {
        return null;
      }
      if (inferredType == null) {
        inferredType = currentType;
      } else if (!inferredType.equals(currentType)) {
        return null;
      }
    }

    return inferredType;
  }

  /**
   * Infers a reference type from a single producer instruction.
   */
  private String inferReferenceType(AbstractInsnNode instruction) {
    if (instruction instanceof VarInsnNode) {
      VarInsnNode varInsn = (VarInsnNode) instruction;
      if (varInsn.getOpcode() == Opcodes.ALOAD) {
        return inferParameterOrThisType(varInsn.var);
      }
      return null;
    }

    if (instruction instanceof TypeInsnNode) {
      TypeInsnNode typeInsn = (TypeInsnNode) instruction;
      if (typeInsn.getOpcode() == Opcodes.NEW || typeInsn.getOpcode() == Opcodes.CHECKCAST) {
        return typeInsn.desc;
      }
      return null;
    }

    if (instruction instanceof MethodInsnNode) {
      Type returnType = Type.getReturnType(((MethodInsnNode) instruction).desc);
      return toReferenceType(returnType);
    }

    if (instruction instanceof LdcInsnNode) {
      Object constant = ((LdcInsnNode) instruction).cst;
      if (constant instanceof String) {
        return "java/lang/String";
      }
      if (constant instanceof Type) {
        return "java/lang/Class";
      }
      return null;
    }

    if (instruction instanceof MultiANewArrayInsnNode) {
      return ((MultiANewArrayInsnNode) instruction).desc;
    }

    return null;
  }

  /**
   * Infers the initial type of a local that corresponds to {@code this} or a method parameter.
   */
  private String inferParameterOrThisType(int localIndex) {
    int currentLocal = 0;
    if ((access & Opcodes.ACC_STATIC) == 0) {
      if (localIndex == 0) {
        return owner;
      }
      currentLocal = 1;
    }

    for (Type argumentType : Type.getArgumentTypes(desc)) {
      if (currentLocal == localIndex) {
        return toReferenceType(argumentType);
      }
      currentLocal += argumentType.getSize();
    }

    return null;
  }

  /**
   * Converts an ASM type to the internal reference form used throughout the ASM engine.
   */
  private String toReferenceType(Type type) {
    if (type == null) {
      return null;
    }
    if (type.getSort() == Type.OBJECT) {
      return type.getInternalName();
    }
    if (type.getSort() == Type.ARRAY) {
      return type.getDescriptor();
    }
    return null;
  }

  /**
   * Checks if a bootstrap method handle is LambdaMetafactory.metafactory or altMetafactory.
   */
  private static boolean isLambdaMetafactory(Handle handle) {
    return handle.getOwner().equals(LAMBDA_METAFACTORY)
        && (handle.getName().equals("metafactory")
            || handle.getName().equals("altMetafactory"));
  }
}
