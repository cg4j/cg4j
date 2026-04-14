package net.cg4j.asm;

/** Represents the different class loader scopes in the analysis. */
public enum ClassLoaderType {

  /** Java runtime classes (JDK/rt.jar). */
  PRIMORDIAL,

  /** Dependency JARs provided via -d option. */
  EXTENSION,

  /** Target JAR being analyzed. */
  APPLICATION
}
