package net.cg4j.asm;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Locates JDK runtime classes (rt.jar or jrt:/ for Java 9+). */
public final class JdkLocator {

  private static final Logger logger = LogManager.getLogger(JdkLocator.class);

  private JdkLocator() {}

  /**
   * Returns the path to rt.jar for Java 8 or null for Java 9+. For Java 9+, use getJrtFileSystem()
   * instead.
   *
   * @return the {@code rt.jar} file, or {@code null} on Java 9+
   */
  public static File getRtJar() {
    String javaHome = System.getProperty("java.home");

    // Java 8: rt.jar is in jre/lib
    Path rtJar = Paths.get(javaHome, "lib", "rt.jar");
    if (Files.exists(rtJar)) {
      logger.debug("Found rt.jar at: {}", rtJar);
      return rtJar.toFile();
    }

    // Might be running from JDK (not JRE)
    rtJar = Paths.get(javaHome, "jre", "lib", "rt.jar");
    if (Files.exists(rtJar)) {
      logger.debug("Found rt.jar at: {}", rtJar);
      return rtJar.toFile();
    }

    // Java 9+: no rt.jar
    logger.debug("No rt.jar found (Java 9+ detected)");
    return null;
  }

  /**
   * Returns the Java version as an integer (e.g., 8, 11, 17, 21).
   *
   * @return the detected Java feature version
   */
  public static int getJavaVersion() {
    String version = System.getProperty("java.version");
    if (version.startsWith("1.")) {
      // Java 8 and earlier: 1.8.0_xxx
      return Integer.parseInt(version.substring(2, 3));
    }
    // Java 9+: 9.0.x, 11.0.x, etc.
    int dotIndex = version.indexOf('.');
    if (dotIndex > 0) {
      return Integer.parseInt(version.substring(0, dotIndex));
    }
    // Handle versions like "17" without dot
    int dashIndex = version.indexOf('-');
    if (dashIndex > 0) {
      return Integer.parseInt(version.substring(0, dashIndex));
    }
    return Integer.parseInt(version);
  }

  /**
   * Returns true if running on Java 9 or later.
   *
   * @return {@code true} if the runtime is Java 9 or later
   */
  public static boolean isJava9OrLater() {
    return getJavaVersion() >= 9;
  }

  /**
   * Opens and returns the jrt:/ file system for Java 9+. Caller is responsible for closing the
   * returned FileSystem.
   *
   * @return FileSystem for jrt:/ or null if not available
   * @throws IOException if the JRT file system cannot be opened
   */
  public static FileSystem getJrtFileSystem() throws IOException {
    if (!isJava9OrLater()) {
      return null;
    }
    URI jrtUri = URI.create("jrt:/");
    return FileSystems.newFileSystem(jrtUri, Collections.emptyMap());
  }

  /**
   * Returns the path to the modules directory in jrt:/ file system.
   *
   * @param jrtFs the open JRT file system
   * @return the modules root path inside the JRT file system
   */
  public static Path getJrtModulesPath(FileSystem jrtFs) {
    return jrtFs.getPath("/modules");
  }
}
