package net.cg4j;

import picocli.CommandLine.IVersionProvider;

/**
 * Picocli version provider that returns the full version string with git commit hash.
 */
public class VersionProvider implements IVersionProvider {

  /**
   * Creates the version provider.
   */
  public VersionProvider() {}

  @Override
  public String[] getVersion() {
    return new String[] { Version.getInstance().getFullVersion() };
  }
}
