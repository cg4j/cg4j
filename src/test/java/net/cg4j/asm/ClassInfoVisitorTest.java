package net.cg4j.asm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.V11;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;

/** Unit tests for ClassInfoVisitor class. */
class ClassInfoVisitorTest {

  /**
   * Unit test: Tests that ClassInfoVisitor detects {@code <clinit>} method. Expects hasClinit() to
   * return true when class has static initializer.
   */
  @Test
  void testDetectsClinitMethod() {
    byte[] bytecode = createClassWithClinit();
    ClassReader reader = new ClassReader(bytecode);
    ClassInfoVisitor visitor = new ClassInfoVisitor(ClassLoaderType.APPLICATION);
    reader.accept(visitor, ClassReader.SKIP_DEBUG);

    ClassInfo info = visitor.getClassInfo();
    assertThat(info.hasClinit()).isTrue();
  }

  /**
   * Unit test: Tests that ClassInfoVisitor correctly reports no {@code <clinit>}. Expects
   * hasClinit() to return false when class has no static initializer.
   */
  @Test
  void testNoClinitMethod() {
    byte[] bytecode = createClassWithoutClinit();
    ClassReader reader = new ClassReader(bytecode);
    ClassInfoVisitor visitor = new ClassInfoVisitor(ClassLoaderType.APPLICATION);
    reader.accept(visitor, ClassReader.SKIP_DEBUG);

    ClassInfo info = visitor.getClassInfo();
    assertThat(info.hasClinit()).isFalse();
  }

  /**
   * Unit test: Tests that {@code <clinit>} with wrong descriptor is not detected. Expects
   * hasClinit() to return false when clinit has wrong signature.
   */
  @Test
  void testClinitWithWrongDescriptor() {
    byte[] bytecode = createClassWithWrongClinitDescriptor();
    ClassReader reader = new ClassReader(bytecode);
    ClassInfoVisitor visitor = new ClassInfoVisitor(ClassLoaderType.APPLICATION);
    reader.accept(visitor, ClassReader.SKIP_DEBUG);

    ClassInfo info = visitor.getClassInfo();
    assertThat(info.hasClinit()).isFalse();
  }

  /** Creates bytecode for a class with a valid {@code <clinit>} method. */
  private byte[] createClassWithClinit() {
    ClassWriter cw = new ClassWriter(0);
    cw.visit(V11, ACC_PUBLIC, "com/example/WithClinit", null, "java/lang/Object", null);

    MethodVisitor mv = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
    mv.visitCode();
    mv.visitInsn(RETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();

    cw.visitEnd();
    return cw.toByteArray();
  }

  /** Creates bytecode for a class without any {@code <clinit>} method. */
  private byte[] createClassWithoutClinit() {
    ClassWriter cw = new ClassWriter(0);
    cw.visit(V11, ACC_PUBLIC, "com/example/NoClinit", null, "java/lang/Object", null);

    // Add a regular static method (not clinit)
    MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "staticMethod", "()V", null, null);
    mv.visitCode();
    mv.visitInsn(RETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();

    cw.visitEnd();
    return cw.toByteArray();
  }

  /** Creates bytecode with a method named {@code <clinit>} but with wrong descriptor. */
  private byte[] createClassWithWrongClinitDescriptor() {
    ClassWriter cw = new ClassWriter(0);
    cw.visit(V11, ACC_PUBLIC, "com/example/BadClinit", null, "java/lang/Object", null);

    // Create <clinit> with wrong descriptor (takes int parameter)
    MethodVisitor mv = cw.visitMethod(ACC_STATIC, "<clinit>", "(I)V", null, null);
    mv.visitCode();
    mv.visitInsn(RETURN);
    mv.visitMaxs(1, 1);
    mv.visitEnd();

    cw.visitEnd();
    return cw.toByteArray();
  }
}
