package net.cg4j.asm;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** ASM ClassVisitor that extracts class metadata. */
public final class ClassInfoVisitor extends ClassVisitor {

  private static final String CLINIT_NAME = "<clinit>";
  private static final String CLINIT_DESCRIPTOR = "()V";

  private String name;
  private String superName;
  private Set<String> interfaces = new HashSet<>();
  private Set<MethodSignature> methods = new HashSet<>();
  private int access;
  private final ClassLoaderType loaderType;
  private boolean hasClinit;

  /**
   * Creates a class info visitor.
   *
   * @param loaderType the loader type to assign to the parsed class
   */
  public ClassInfoVisitor(ClassLoaderType loaderType) {
    super(Opcodes.ASM9);
    this.loaderType = loaderType;
  }

  @Override
  public void visit(
      int version,
      int access,
      String name,
      String signature,
      String superName,
      String[] interfaces) {
    this.name = name;
    this.superName = superName;
    this.access = access;
    if (interfaces != null) {
      this.interfaces.addAll(Arrays.asList(interfaces));
    }
  }

  @Override
  public MethodVisitor visitMethod(
      int access, String name, String descriptor, String signature, String[] exceptions) {
    methods.add(new MethodSignature(this.name, name, descriptor, access));
    // Detect static initializer
    if (CLINIT_NAME.equals(name) && CLINIT_DESCRIPTOR.equals(descriptor)) {
      hasClinit = true;
    }
    // We don't need to visit method body for class info extraction
    return null;
  }

  /**
   * Returns the extracted class info. Must be called after the class has been visited.
   *
   * @return the extracted class metadata
   */
  public ClassInfo getClassInfo() {
    return new ClassInfo(name, superName, interfaces, methods, access, loaderType, hasClinit);
  }

  /**
   * Returns the class name.
   *
   * @return the visited internal class name
   */
  public String getClassName() {
    return name;
  }
}
