package net.cg4j.asm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.Opcodes;

/** Unit tests for ScopeExclusions class. */
class ScopeExclusionsTest {

  /**
   * Unit test: Loads default exclusion patterns from the built-in resource. Expects at least one
   * pattern loaded.
   */
  @Test
  void testLoadDefaults_HasPatterns() throws IOException {
    ScopeExclusions exclusions = ScopeExclusions.loadDefaults();

    assertThat(exclusions.patternCount()).isGreaterThan(0);
  }

  /**
   * Unit test: Tests that default exclusions match expected JDK packages. Expects java/util, javax,
   * sun packages to be excluded; java/lang to be kept.
   */
  @Test
  void testIsExcluded_MatchesExpectedPatterns() throws IOException {
    ScopeExclusions exclusions = ScopeExclusions.loadDefaults();

    // Should be excluded
    assertThat(exclusions.isExcluded("java/util/HashMap")).isTrue();
    assertThat(exclusions.isExcluded("java/util/ArrayList")).isTrue();
    assertThat(exclusions.isExcluded("java/io/InputStream")).isTrue();
    assertThat(exclusions.isExcluded("java/nio/ByteBuffer")).isTrue();
    assertThat(exclusions.isExcluded("javax/swing/JFrame")).isTrue();
    assertThat(exclusions.isExcluded("sun/misc/Unsafe")).isTrue();
    assertThat(exclusions.isExcluded("com/sun/proxy/Proxy")).isTrue();
    assertThat(exclusions.isExcluded("jdk/internal/misc/Unsafe")).isTrue();

    // Should NOT be excluded
    assertThat(exclusions.isExcluded("java/lang/Object")).isFalse();
    assertThat(exclusions.isExcluded("java/lang/String")).isFalse();
    assertThat(exclusions.isExcluded("java/lang/Enum")).isFalse();
    assertThat(exclusions.isExcluded("com/example/MyClass")).isFalse();
    assertThat(exclusions.isExcluded("okhttp3/OkHttpClient")).isFalse();
  }

  /**
   * Unit test: Verifies applyExclusions only removes Primordial classes. Expects Application
   * classes matching exclusion patterns to be preserved.
   */
  @Test
  void testApplyExclusions_OnlyRemovesPrimordial() throws IOException {
    ScopeExclusions exclusions = ScopeExclusions.loadDefaults();

    Map<String, ClassInfo> classes = new HashMap<>();

    // Primordial class matching exclusion -> should be removed
    classes.put(
        "java/util/HashMap", makeClassInfo("java/util/HashMap", ClassLoaderType.PRIMORDIAL));

    // Application class matching exclusion pattern -> should be preserved
    classes.put(
        "java/util/CustomAppUtil",
        makeClassInfo("java/util/CustomAppUtil", ClassLoaderType.APPLICATION));

    // Primordial class NOT matching exclusion -> should be preserved
    classes.put("java/lang/Object", makeClassInfo("java/lang/Object", ClassLoaderType.PRIMORDIAL));

    Map<String, ClassInfo> filtered = exclusions.applyExclusions(classes);

    assertThat(filtered).doesNotContainKey("java/util/HashMap");
    assertThat(filtered).containsKey("java/util/CustomAppUtil");
    assertThat(filtered).containsKey("java/lang/Object");
    assertThat(filtered).hasSize(2);
  }

  /**
   * Unit test: Verifies non-matching Primordial classes are preserved. Expects java/lang/* classes
   * to remain after applying default exclusions.
   */
  @Test
  void testApplyExclusions_PreservesNonMatchingPrimordial() throws IOException {
    ScopeExclusions exclusions = ScopeExclusions.loadDefaults();

    Map<String, ClassInfo> classes = new HashMap<>();
    classes.put("java/lang/Object", makeClassInfo("java/lang/Object", ClassLoaderType.PRIMORDIAL));
    classes.put("java/lang/String", makeClassInfo("java/lang/String", ClassLoaderType.PRIMORDIAL));
    classes.put("java/lang/Enum", makeClassInfo("java/lang/Enum", ClassLoaderType.PRIMORDIAL));

    Map<String, ClassInfo> filtered = exclusions.applyExclusions(classes);

    assertThat(filtered).hasSize(3);
    assertThat(filtered).containsKey("java/lang/Object");
    assertThat(filtered).containsKey("java/lang/String");
    assertThat(filtered).containsKey("java/lang/Enum");
  }

  /**
   * Unit test: Verifies ScopeExclusions.none() excludes nothing. Expects all classes to be
   * preserved regardless of loader type.
   */
  @Test
  void testNone_ExcludesNothing() {
    ScopeExclusions exclusions = ScopeExclusions.none();

    assertThat(exclusions.patternCount()).isZero();
    assertThat(exclusions.isExcluded("java/util/HashMap")).isFalse();

    Map<String, ClassInfo> classes = new HashMap<>();
    classes.put(
        "java/util/HashMap", makeClassInfo("java/util/HashMap", ClassLoaderType.PRIMORDIAL));
    classes.put(
        "com/example/MyClass", makeClassInfo("com/example/MyClass", ClassLoaderType.APPLICATION));

    Map<String, ClassInfo> filtered = exclusions.applyExclusions(classes);
    assertThat(filtered).hasSize(2);
  }

  /**
   * Unit test: Loads exclusion patterns from a custom file. Expects custom patterns to work
   * correctly.
   */
  @Test
  void testLoadFromFile_CustomPatterns(@TempDir Path tempDir) throws IOException {
    Path customFile = tempDir.resolve("custom-exclusions.txt");
    Files.write(
        customFile, List.of("# Custom exclusions", "com/example/internal/.*", "", "org/test/.*"));

    ScopeExclusions exclusions = ScopeExclusions.loadFromFile(customFile.toFile());

    assertThat(exclusions.patternCount()).isEqualTo(2);
    assertThat(exclusions.isExcluded("com/example/internal/Helper")).isTrue();
    assertThat(exclusions.isExcluded("org/test/TestClass")).isTrue();
    assertThat(exclusions.isExcluded("com/example/MyClass")).isFalse();
    assertThat(exclusions.isExcluded("java/util/HashMap")).isFalse();
  }

  /**
   * Unit test: Tests error handling for non-existent exclusion file. Expects IOException with
   * descriptive message.
   */
  @Test
  void testLoadFromFile_NonExistent() {
    File nonExistent = new File("/tmp/does-not-exist-exclusions.txt");

    assertThatThrownBy(() -> ScopeExclusions.loadFromFile(nonExistent))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("not found");
  }

  /**
   * Unit test: Tests error handling for invalid regex in exclusion file. Expects
   * IllegalArgumentException with line number context.
   */
  @Test
  void testLoadFromFile_InvalidRegex(@TempDir Path tempDir) throws IOException {
    Path badFile = tempDir.resolve("bad-exclusions.txt");
    Files.write(badFile, List.of("valid/pattern/.*", "[invalid regex"));

    assertThatThrownBy(() -> ScopeExclusions.loadFromFile(badFile.toFile()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("line 2");
  }

  /** Creates a minimal ClassInfo for testing. */
  private static ClassInfo makeClassInfo(String name, ClassLoaderType loaderType) {
    return new ClassInfo(
        name,
        "java/lang/Object",
        Collections.emptySet(),
        new HashSet<>(),
        Opcodes.ACC_PUBLIC,
        loaderType,
        false);
  }
}
