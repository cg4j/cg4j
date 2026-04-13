package net.cg4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Version information utility that loads version and git commit hash from properties file. */
public class Version {

  private static final String VERSION_PROPERTIES = "/version.properties";
  private static final String UNKNOWN = "unknown";

  private final String version;
  private final String gitCommitId;

  private static Version instance;

  /** Private constructor loads version properties from classpath. */
  private Version() {
    Properties props = new Properties();
    try (InputStream input = Version.class.getResourceAsStream(VERSION_PROPERTIES)) {
      if (input != null) {
        props.load(input);
      }
    } catch (IOException e) {
      // Silently fall back to defaults
    }

    this.version = props.getProperty("version", UNKNOWN);
    this.gitCommitId = props.getProperty("git.commit.id", UNKNOWN);
  }

  /**
   * Gets the singleton instance of Version.
   *
   * @return the shared Version instance
   */
  public static synchronized Version getInstance() {
    if (instance == null) {
      instance = new Version();
    }
    return instance;
  }

  /**
   * Returns the full version string including git commit hash for SNAPSHOT versions.
   *
   * @return formatted version string (e.g., "0.1.0-SNAPSHOT (abc1234)" or "0.1.0")
   */
  public String getFullVersion() {
    if (version.contains("SNAPSHOT") && !gitCommitId.equals(UNKNOWN)) {
      return version + " (" + gitCommitId + ")";
    }
    return version;
  }

  /**
   * Returns the base version without git commit hash.
   *
   * @return the project version string
   */
  public String getVersion() {
    return version;
  }

  /**
   * Returns the git commit hash.
   *
   * @return the git commit hash, or {@code unknown}
   */
  public String getGitCommitId() {
    return gitCommitId;
  }
}
