package net.cg4j.asm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/** Unit tests for CallSiteExtractor class. */
class CallSiteExtractorTest {

  /**
   * Unit test: Tests CallSiteExtractor tracking of NEW instructions. Expects NEW instructions to be
   * tracked as instantiated types.
   */
  @Test
  void testTracksInstantiatedTypes() {
    CallSiteExtractor extractor = new CallSiteExtractor();

    // Simulate visiting NEW instructions
    extractor.visitTypeInsn(Opcodes.NEW, "com/example/MyClass");
    extractor.visitTypeInsn(Opcodes.NEW, "com/example/AnotherClass");
    extractor.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/String"); // Should not be tracked

    Set<String> instantiatedTypes = extractor.getInstantiatedTypes();

    assertThat(instantiatedTypes).hasSize(2);
    assertThat(instantiatedTypes).contains("com/example/MyClass", "com/example/AnotherClass");
    assertThat(instantiatedTypes).doesNotContain("java/lang/String");
  }

  /**
   * Unit test: Tests CallSiteExtractor tracking of static field accesses. Expects GETSTATIC and
   * PUTSTATIC owners to be recorded once.
   */
  @Test
  void testTracksStaticFieldOwners() {
    CallSiteExtractor extractor = new CallSiteExtractor();

    extractor.visitFieldInsn(Opcodes.GETSTATIC, "com/example/Config", "VALUE", "I");
    extractor.visitFieldInsn(
        Opcodes.PUTSTATIC, "com/example/Holder", "CACHE", "Ljava/lang/Object;");
    extractor.visitFieldInsn(Opcodes.GETFIELD, "com/example/Instance", "field", "I");

    assertThat(extractor.getStaticFieldOwners())
        .containsExactlyInAnyOrder("com/example/Config", "com/example/Holder");
  }

  /**
   * Unit test: Tests extraction of LambdaMetafactory INVOKEDYNAMIC instructions. Expects lambda
   * call sites to be captured with correct SAM and impl info.
   */
  @Test
  void testExtractsLambdaMetafactory() {
    byte[] bytecode =
        buildClassWithLambda(
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            "run",
            "()V",
            "com/example/MyClass",
            "lambda$test$0",
            "()V",
            Opcodes.H_INVOKESTATIC,
            "()Ljava/lang/Runnable;");

    CallSiteExtractor extractor = visitMethod(bytecode, "testMethod", "()V");

    List<LambdaCallSite> lambdas = extractor.getLambdaCallSites();
    assertThat(lambdas).hasSize(1);

    LambdaCallSite site = lambdas.get(0);
    assertThat(site.getSamMethodName()).isEqualTo("run");
    assertThat(site.getSamDescriptor()).isEqualTo("()V");
    assertThat(site.getIndyDescriptor()).isEqualTo("()Ljava/lang/Runnable;");
    assertThat(site.getImplOwner()).isEqualTo("com/example/MyClass");
    assertThat(site.getImplName()).isEqualTo("lambda$test$0");
    assertThat(site.getImplDescriptor()).isEqualTo("()V");
    assertThat(site.getImplTag()).isEqualTo(Opcodes.H_INVOKESTATIC);

    // Regular call sites should be empty (no visitMethodInsn in this method)
    assertThat(extractor.getCallSites()).isEmpty();
  }

  /**
   * Unit test: Tests that non-LambdaMetafactory INVOKEDYNAMIC is ignored. Expects no lambda call
   * sites for StringConcatFactory bootstrap.
   */
  @Test
  void testIgnoresNonLambdaInvokeDynamic() {
    CallSiteExtractor extractor = new CallSiteExtractor();

    // Simulate StringConcatFactory.makeConcatWithConstants (Java 9+ string concat)
    Handle bootstrap =
        new Handle(
            Opcodes.H_INVOKESTATIC,
            "java/lang/invoke/StringConcatFactory",
            "makeConcatWithConstants",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;"
                + "Ljava/lang/invoke/MethodType;Ljava/lang/String;"
                + "[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
            false);
    extractor.visitInvokeDynamicInsn(
        "makeConcatWithConstants",
        "(Ljava/lang/String;)Ljava/lang/String;",
        bootstrap,
        "\u0001 world");

    assertThat(extractor.getLambdaCallSites()).isEmpty();
  }

  /**
   * Unit test: Tests extraction of altMetafactory INVOKEDYNAMIC instructions. Expects lambda call
   * sites to be captured for altMetafactory bootstrap.
   */
  @Test
  void testAltMetafactory() {
    byte[] bytecode =
        buildClassWithLambda(
            "java/lang/invoke/LambdaMetafactory",
            "altMetafactory",
            "apply",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            "com/example/MyClass",
            "lambda$map$0",
            "(Ljava/lang/String;)Ljava/lang/Integer;",
            Opcodes.H_INVOKESTATIC,
            "()Ljava/util/function/Function;");

    CallSiteExtractor extractor = visitMethod(bytecode, "testMethod", "()V");

    List<LambdaCallSite> lambdas = extractor.getLambdaCallSites();
    assertThat(lambdas).hasSize(1);
    assertThat(lambdas.get(0).getSamMethodName()).isEqualTo("apply");
    assertThat(lambdas.get(0).getImplName()).isEqualTo("lambda$map$0");
  }

  /**
   * Unit test: Tests that both regular call sites and lambda call sites are captured. Expects both
   * lists to be populated independently.
   */
  @Test
  void testLambdaAndRegularCallSites() {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    cw.visit(
        Opcodes.V11, Opcodes.ACC_PUBLIC, "com/example/TestClass", null, "java/lang/Object", null);

    MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "testMethod", "()V", null, null);
    mv.visitCode();

    // Regular method call
    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "com/example/Helper", "doWork", "()V", false);

    // Lambda INVOKEDYNAMIC
    Handle bootstrap =
        new Handle(
            Opcodes.H_INVOKESTATIC,
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;"
                + "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;"
                + "Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;"
                + ")Ljava/lang/invoke/CallSite;",
            false);
    Handle implHandle =
        new Handle(
            Opcodes.H_INVOKESTATIC, "com/example/TestClass", "lambda$testMethod$0", "()V", false);
    mv.visitInvokeDynamicInsn(
        "run",
        "()Ljava/lang/Runnable;",
        bootstrap,
        Type.getType("()V"),
        implHandle,
        Type.getType("()V"));

    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(1, 1);
    mv.visitEnd();
    cw.visitEnd();

    CallSiteExtractor extractor = visitMethod(cw.toByteArray(), "testMethod", "()V");

    assertThat(extractor.getCallSites()).hasSize(1);
    assertThat(extractor.getCallSites().get(0).getOwner()).isEqualTo("com/example/Helper");

    assertThat(extractor.getLambdaCallSites()).hasSize(1);
    assertThat(extractor.getLambdaCallSites().get(0).getImplOwner())
        .isEqualTo("com/example/TestClass");
  }

  /**
   * Unit test: Tests that LambdaMetafactory calls via visitMethodInsn are still skipped. Expects no
   * regular call site for LambdaMetafactory owner.
   */
  @Test
  void testSkipsLambdaMetafactoryMethodInsn() {
    CallSiteExtractor extractor = new CallSiteExtractor();

    extractor.visitMethodInsn(
        Opcodes.INVOKESTATIC,
        "java/lang/invoke/LambdaMetafactory",
        "metafactory",
        "(Ljava/lang/invoke/MethodHandles$Lookup;)Ljava/lang/invoke/CallSite;",
        false);

    assertThat(extractor.getCallSites()).isEmpty();
  }

  /** Builds a class with a method containing an INVOKEDYNAMIC instruction. */
  private static byte[] buildClassWithLambda(
      String bootstrapOwner,
      String bootstrapName,
      String samName,
      String samDesc,
      String implOwner,
      String implName,
      String implDesc,
      int implTag,
      String indyDescriptor) {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    cw.visit(
        Opcodes.V11, Opcodes.ACC_PUBLIC, "com/example/TestClass", null, "java/lang/Object", null);

    MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "testMethod", "()V", null, null);
    mv.visitCode();

    Handle bootstrap =
        new Handle(
            Opcodes.H_INVOKESTATIC,
            bootstrapOwner,
            bootstrapName,
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;"
                + "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;"
                + "Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;"
                + ")Ljava/lang/invoke/CallSite;",
            false);
    Handle implHandle = new Handle(implTag, implOwner, implName, implDesc, false);

    mv.visitInvokeDynamicInsn(
        samName,
        indyDescriptor,
        bootstrap,
        Type.getType(samDesc),
        implHandle,
        Type.getType(samDesc));

    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(1, 1);
    mv.visitEnd();
    cw.visitEnd();

    return cw.toByteArray();
  }

  /** Visits a specific method in bytecode and returns the extractor with results. */
  private static CallSiteExtractor visitMethod(
      byte[] bytecode, String methodName, String descriptor) {
    CallSiteExtractor extractor = new CallSiteExtractor();
    ClassReader reader = new ClassReader(bytecode);
    reader.accept(
        new org.objectweb.asm.ClassVisitor(Opcodes.ASM9) {
          @Override
          public MethodVisitor visitMethod(
              int access, String name, String desc, String signature, String[] exceptions) {
            if (name.equals(methodName) && desc.equals(descriptor)) {
              return extractor;
            }
            return null;
          }
        },
        ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    return extractor;
  }
}
