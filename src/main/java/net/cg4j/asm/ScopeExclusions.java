package net.cg4j.asm;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/**
 * Loads and applies scope exclusion patterns to filter Primordial classes from the hierarchy.
 *
 * <p>Exclusion files use one Java regex pattern per line with '/' as the package separator.
 * Lines starting with '#' are comments. Blank lines are ignored.
 * This format is compatible with WALA exclusion files.</p>
 */
public final class ScopeExclusions {

  private static final Logger logger = LogManager.getLogger(ScopeExclusions.class);
  private static final String DEFAULT_RESOURCE = "/default-exclusions.txt";

  private final List<Pattern> patterns;

  private ScopeExclusions(List<Pattern> patterns) {
    this.patterns = Collections.unmodifiableList(new ArrayList<>(patterns));
  }

  /**
   * Loads default exclusion patterns from the built-in resource file.
   *
   * @return scope exclusions with default patterns
   * @throws IOException if the resource cannot be read
   */
  public static ScopeExclusions loadDefaults() throws IOException {
    try (InputStream is = ScopeExclusions.class.getResourceAsStream(DEFAULT_RESOURCE)) {
      if (is == null) {
        throw new IOException("Default exclusions resource not found: " + DEFAULT_RESOURCE);
      }
      List<String> lines;
      try (BufferedReader reader = new BufferedReader(
          new InputStreamReader(is, StandardCharsets.UTF_8))) {
        lines = reader.lines().collect(Collectors.toList());
      }
      return parseLines(lines, DEFAULT_RESOURCE);
    }
  }

  /**
   * Loads exclusion patterns from a user-provided file.
   *
   * @param file the exclusion patterns file
   * @return scope exclusions with patterns from the file
   * @throws IOException if the file cannot be read
   */
  public static ScopeExclusions loadFromFile(File file) throws IOException {
    if (!file.exists()) {
      throw new IOException("Exclusions file not found: " + file.getPath());
    }
    List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
    return parseLines(lines, file.getPath());
  }

  /**
   * Creates an instance with no exclusion patterns.
   *
   * @return scope exclusions that exclude nothing
   */
  public static ScopeExclusions none() {
    return new ScopeExclusions(Collections.emptyList());
  }

  /**
   * Parses lines from an exclusion file into compiled regex patterns.
   */
  private static ScopeExclusions parseLines(List<String> lines, String source) {
    List<Pattern> compiled = new ArrayList<>();

    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i).trim();

      // Skip comments and blank lines
      if (line.isEmpty() || line.startsWith("#")) {
        continue;
      }

      try {
        compiled.add(Pattern.compile(line));
      } catch (PatternSyntaxException e) {
        throw new IllegalArgumentException(
            "Invalid regex pattern at line " + (i + 1) + " in " + source + ": " + line, e);
      }
    }

    logger.debug("Loaded {} exclusion patterns from {}", compiled.size(), source);
    return new ScopeExclusions(compiled);
  }

  /**
   * Returns true if the given class name matches any exclusion pattern.
   *
   * @param className class name in internal format (e.g., "java/util/HashMap")
   * @return true if the class should be excluded
   */
  public boolean isExcluded(String className) {
    for (Pattern pattern : patterns) {
      if (pattern.matcher(className).matches()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Filters classes by removing Primordial entries that match exclusion patterns.
   * Non-Primordial classes (Extension, Application) are never excluded.
   *
   * @param classes map of class name to ClassInfo
   * @return new map with excluded Primordial classes removed
   */
  public Map<String, ClassInfo> applyExclusions(Map<String, ClassInfo> classes) {
    if (patterns.isEmpty()) {
      return new HashMap<>(classes);
    }

    Map<String, ClassInfo> filtered = new HashMap<>();
    int excludedCount = 0;

    for (Map.Entry<String, ClassInfo> entry : classes.entrySet()) {
      ClassInfo info = entry.getValue();

      // Only exclude Primordial classes
      if (info.getLoaderType() == ClassLoaderType.PRIMORDIAL && isExcluded(entry.getKey())) {
        excludedCount++;
      } else {
        filtered.put(entry.getKey(), info);
      }
    }

    if (excludedCount > 0) {
      logger.info("Excluded {} Primordial classes from scope", excludedCount);
    }

    return filtered;
  }

  /**
   * Returns the number of loaded exclusion patterns.
   */
  public int patternCount() {
    return patterns.size();
  }
}
