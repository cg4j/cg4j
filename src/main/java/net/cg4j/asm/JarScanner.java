package net.cg4j.asm;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

/**
 * Scans JAR files and JRT filesystem to extract class information using ASM.
 */
public final class JarScanner {

  private static final Logger logger = LogManager.getLogger(JarScanner.class);
  private static final int PARSE_OPTIONS = ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES;

  private JarScanner() {}

  /**
   * Scans a JAR file and returns class information.
   *
   * @param jarFile the JAR file to scan
   * @param loaderType the loader type to assign to classes
   * @return map of class name to ClassInfo
   * @throws IOException if the JAR cannot be opened or read
   */
  public static Map<String, ClassInfo> scanJar(File jarFile, ClassLoaderType loaderType)
      throws IOException {
    Map<String, ClassInfo> classes = new HashMap<>();

    try (JarFile jar = new JarFile(jarFile)) {
      Enumeration<JarEntry> entries = jar.entries();
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        if (entry.getName().endsWith(".class")) {
          try (InputStream is = jar.getInputStream(entry)) {
            ClassInfo info = parseClass(is, loaderType);
            if (info != null) {
              classes.put(info.getName(), info);
            }
          } catch (Exception e) {
            logger.warn("Failed to parse class {}: {}", entry.getName(), e.getMessage());
          }
        }
      }
    }

    logger.debug("Scanned {} classes from {} ({})", classes.size(), jarFile.getName(), loaderType);
    return classes;
  }

  /**
   * Scans multiple JAR files.
   *
   * @param jarFiles the JAR files to scan
   * @param loaderType the loader type to assign to classes
   * @return map of class name to ClassInfo
   * @throws IOException if any JAR cannot be opened or read
   */
  public static Map<String, ClassInfo> scanJars(List<File> jarFiles, ClassLoaderType loaderType)
      throws IOException {
    Map<String, ClassInfo> classes = new HashMap<>();

    for (File jarFile : jarFiles) {
      classes.putAll(scanJar(jarFile, loaderType));
    }

    return classes;
  }

  /**
   * Scans JDK classes from rt.jar (Java 8) or jrt:/ filesystem (Java 9+).
   *
   * @return map of class name to ClassInfo
   * @throws IOException if the runtime classes cannot be scanned
   */
  public static Map<String, ClassInfo> scanJdk() throws IOException {
    File rtJar = JdkLocator.getRtJar();
    if (rtJar != null) {
      logger.info("Scanning JDK classes from rt.jar");
      return scanJar(rtJar, ClassLoaderType.PRIMORDIAL);
    }

    // Java 9+: scan jrt:/ filesystem
    logger.info("Scanning JDK classes from jrt:/ filesystem");
    return scanJrtFileSystem();
  }

  /**
   * Scans the jrt:/ filesystem for Java 9+ JDK classes.
   */
  private static Map<String, ClassInfo> scanJrtFileSystem() throws IOException {
    Map<String, ClassInfo> classes = new HashMap<>();

    try (FileSystem jrtFs = JdkLocator.getJrtFileSystem()) {
      if (jrtFs == null) {
        logger.warn("JRT filesystem not available");
        return classes;
      }

      Path modulesPath = JdkLocator.getJrtModulesPath(jrtFs);
      try (Stream<Path> modules = Files.list(modulesPath)) {
        modules.forEach(modulePath -> {
          try {
            scanJrtModule(modulePath, classes);
          } catch (IOException e) {
            logger.warn("Failed to scan module {}: {}", modulePath, e.getMessage());
          }
        });
      }
    }

    logger.debug("Scanned {} JDK classes from jrt:/", classes.size());
    return classes;
  }

  /**
   * Scans a single module in the jrt:/ filesystem.
   */
  private static void scanJrtModule(Path modulePath, Map<String, ClassInfo> classes)
      throws IOException {
    try (Stream<Path> walk = Files.walk(modulePath)) {
      walk.filter(p -> p.toString().endsWith(".class"))
          .forEach(classPath -> {
            try (InputStream is = Files.newInputStream(classPath)) {
              ClassInfo info = parseClass(is, ClassLoaderType.PRIMORDIAL);
              if (info != null) {
                classes.put(info.getName(), info);
              }
            } catch (Exception e) {
              logger.trace("Failed to parse {}: {}", classPath, e.getMessage());
            }
          });
    }
  }

  /**
   * Parses a class file and returns ClassInfo.
   */
  private static ClassInfo parseClass(InputStream is, ClassLoaderType loaderType)
      throws IOException {
    ClassReader reader = new ClassReader(is);
    ClassInfoVisitor visitor = new ClassInfoVisitor(loaderType);
    reader.accept(visitor, PARSE_OPTIONS);
    return visitor.getClassInfo();
  }

  /**
   * Parses a class from raw bytecode.
   *
   * @param bytecode raw class bytecode
   * @param loaderType the loader type to assign to the parsed class
   * @return the parsed class metadata
   */
  public static ClassInfo parseClass(byte[] bytecode, ClassLoaderType loaderType) {
    ClassReader reader = new ClassReader(bytecode);
    ClassInfoVisitor visitor = new ClassInfoVisitor(loaderType);
    reader.accept(visitor, PARSE_OPTIONS);
    return visitor.getClassInfo();
  }

  /**
   * Creates a loader for lazily loading primordial (JDK) class bytecode.
   * The returned loader caches the JDK file handle (jrt:/ filesystem or rt.jar)
   * for efficient repeated lookups. Caller must close the loader when done.
   *
   * @return a new PrimordialBytecodeLoader
   * @throws IOException if the JDK runtime cannot be opened
   */
  public static PrimordialBytecodeLoader createPrimordialLoader() throws IOException {
    File rtJar = JdkLocator.getRtJar();
    if (rtJar != null) {
      return new RtJarBytecodeLoader(rtJar);
    }
    return new JrtBytecodeLoader();
  }

  /**
   * Loads primordial class bytecode on demand. Implementations cache the underlying
   * JDK file handle for efficient repeated lookups.
   */
  public interface PrimordialBytecodeLoader extends AutoCloseable {

    /**
     * Loads bytecode for a single primordial class.
     *
     * @param className the internal class name (e.g., "java/lang/String")
     * @return the raw class bytecode, or null if the class cannot be found
     */
    byte[] loadBytecode(String className);

    @Override
    void close() throws IOException;
  }

  /**
   * Loads class bytecode from rt.jar (Java 8).
   */
  private static class RtJarBytecodeLoader implements PrimordialBytecodeLoader {
    private final JarFile jar;

    RtJarBytecodeLoader(File rtJar) throws IOException {
      this.jar = new JarFile(rtJar);
    }

    @Override
    public byte[] loadBytecode(String className) {
      ZipEntry entry = jar.getEntry(className + ".class");
      if (entry == null) {
        return null;
      }
      try (InputStream is = jar.getInputStream(entry)) {
        return is.readAllBytes();
      } catch (IOException e) {
        logger.trace("Failed to load bytecode for {} from rt.jar: {}", className, e.getMessage());
        return null;
      }
    }

    @Override
    public void close() throws IOException {
      jar.close();
    }
  }

  /**
   * Loads class bytecode from the jrt:/ filesystem (Java 9+).
   */
  private static class JrtBytecodeLoader implements PrimordialBytecodeLoader {
    private final FileSystem jrtFs;
    private final List<Path> modulePaths;

    JrtBytecodeLoader() throws IOException {
      this.jrtFs = JdkLocator.getJrtFileSystem();
      if (jrtFs != null) {
        Path modulesPath = JdkLocator.getJrtModulesPath(jrtFs);
        try (Stream<Path> modules = Files.list(modulesPath)) {
          this.modulePaths = modules.collect(Collectors.toList());
        }
      } else {
        this.modulePaths = List.of();
      }
    }

    @Override
    public byte[] loadBytecode(String className) {
      if (jrtFs == null) {
        return null;
      }
      for (Path modulePath : modulePaths) {
        Path classPath = modulePath.resolve(className + ".class");
        if (Files.exists(classPath)) {
          try {
            return Files.readAllBytes(classPath);
          } catch (IOException e) {
            logger.trace("Failed to read {}: {}", classPath, e.getMessage());
          }
        }
      }
      return null;
    }

    @Override
    public void close() throws IOException {
      if (jrtFs != null) {
        jrtFs.close();
      }
    }
  }
}
