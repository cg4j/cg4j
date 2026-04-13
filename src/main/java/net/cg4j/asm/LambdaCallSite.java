package net.cg4j.asm;

/**
 * Represents a lambda or method reference captured from an INVOKEDYNAMIC instruction bootstrapped
 * by {@code LambdaMetafactory.metafactory} or {@code altMetafactory}.
 */
public final class LambdaCallSite {

  private final String samMethodName;
  private final String samDescriptor;
  private final String indyDescriptor;
  private final String implOwner;
  private final String implName;
  private final String implDescriptor;
  private final int implTag;

  /**
   * Creates a lambda call site.
   *
   * @param samMethodName SAM method name (e.g., "invoke", "run", "apply")
   * @param samDescriptor erased SAM method descriptor (e.g.,
   *     "(Ljava/lang/Object;)Ljava/lang/Object;")
   * @param indyDescriptor invokedynamic call-site descriptor (return type is the functional
   *     interface)
   * @param implOwner lambda body owner class in internal format
   * @param implName lambda body method name
   * @param implDescriptor lambda body method descriptor
   * @param implTag handle tag (e.g., Opcodes.H_INVOKESTATIC)
   */
  public LambdaCallSite(
      String samMethodName,
      String samDescriptor,
      String indyDescriptor,
      String implOwner,
      String implName,
      String implDescriptor,
      int implTag) {
    this.samMethodName = samMethodName;
    this.samDescriptor = samDescriptor;
    this.indyDescriptor = indyDescriptor;
    this.implOwner = implOwner;
    this.implName = implName;
    this.implDescriptor = implDescriptor;
    this.implTag = implTag;
  }

  /**
   * Returns the SAM method name on the functional interface.
   *
   * @return the functional interface method name
   */
  public String getSamMethodName() {
    return samMethodName;
  }

  /**
   * Returns the erased SAM method descriptor.
   *
   * @return the erased SAM method descriptor
   */
  public String getSamDescriptor() {
    return samDescriptor;
  }

  /**
   * Returns the invokedynamic call-site descriptor. The return type encodes the functional
   * interface.
   *
   * @return the invokedynamic call-site descriptor
   */
  public String getIndyDescriptor() {
    return indyDescriptor;
  }

  /**
   * Returns the lambda body owner class.
   *
   * @return the internal owner class name
   */
  public String getImplOwner() {
    return implOwner;
  }

  /**
   * Returns the lambda body method name.
   *
   * @return the implementation method name
   */
  public String getImplName() {
    return implName;
  }

  /**
   * Returns the lambda body method descriptor.
   *
   * @return the implementation method descriptor
   */
  public String getImplDescriptor() {
    return implDescriptor;
  }

  /**
   * Returns the handle tag for the implementation method.
   *
   * @return the ASM handle tag for the implementation method
   */
  public int getImplTag() {
    return implTag;
  }

  @Override
  public String toString() {
    return "LambdaCallSite{"
        + "sam="
        + samMethodName
        + samDescriptor
        + ", impl="
        + implOwner
        + "."
        + implName
        + implDescriptor
        + "}";
  }
}
