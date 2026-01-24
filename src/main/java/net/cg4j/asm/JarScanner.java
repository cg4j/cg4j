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
import java.util.stream.Stream;

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
   */
  public static ClassInfo parseClass(byte[] bytecode, ClassLoaderType loaderType) {
    ClassReader reader = new ClassReader(bytecode);
    ClassInfoVisitor visitor = new ClassInfoVisitor(loaderType);
    reader.accept(visitor, PARSE_OPTIONS);
    return visitor.getClassInfo();
  }
}
