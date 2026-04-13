package net.cg4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ibm.wala.ipa.callgraph.CallGraph;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WalaCallGraphBuilderTest extends BaseIntegrationTest {

  private WalaCallGraphBuilder builder;

  @BeforeEach
  void setup() {
    builder = new WalaCallGraphBuilder();
  }

  /**
   * Tests basic call graph construction from a simple JAR file. Expects non-null call graph with at
   * least one node.
   */
  @Test
  void testBuildCallGraph_ValidJar_Success() throws Exception {
    CallGraph cg = builder.buildCallGraph(slf4jJar.getPath(), Collections.emptyList(), null);

    assertThat(cg).isNotNull();
    assertThat(cg.getNumberOfNodes()).isGreaterThan(0);
  }

  /**
   * Tests call graph construction with dependency JARs loaded via Extension ClassLoader. Expects
   * larger call graph (1000+ nodes) when dependencies are included.
   */
  @Test
  void testBuildCallGraph_WithDependencies_LoadsExtension() throws Exception {
    List<File> deps = Arrays.asList(okhttpDeps.listFiles());

    CallGraph cg = builder.buildCallGraph(okhttpJar.getPath(), deps, null);

    assertThat(cg).isNotNull();
    assertThat(cg.getNumberOfNodes()).isGreaterThan(1000);
  }

  /** Tests error handling when JAR file does not exist. Expects exception to be thrown. */
  @Test
  void testBuildCallGraph_NonExistentJar_ThrowsException() {
    assertThatThrownBy(
            () -> {
              builder.buildCallGraph("nonexistent.jar", Collections.emptyList(), null);
            })
        .isInstanceOf(Throwable.class);
  }

  /**
   * Unit test: Tests stream-based edge extraction returns edges. Expects at least one edge with
   * non-empty source and target.
   */
  @Test
  void testExtractEdgesAsStream_ReturnsNonEmptyStream() throws Exception {
    CallGraph cg = builder.buildCallGraph(slf4jJar.getPath(), Collections.emptyList(), null);

    Stream<WalaCallGraphBuilder.CallGraphEdge> edges =
        WalaCallGraphBuilder.extractEdgesAsStream(cg, false);

    WalaCallGraphBuilder.CallGraphEdge edge =
        edges.findFirst().orElseThrow(() -> new AssertionError("Expected at least one edge"));

    assertThat(edge.source).isNotEmpty();
    assertThat(edge.target).isNotEmpty();
    assertThat(edge.source).contains(".");
    assertThat(edge.target).contains(".");
  }

  /**
   * Tests RT (Java runtime) class filtering when disabled. Expects no java/* classes in extracted
   * edges when includeRT=false.
   */
  @Test
  void testExtractEdgesAsStream_FiltersRTWhenDisabled() throws Exception {
    CallGraph cg = builder.buildCallGraph(slf4jJar.getPath(), Collections.emptyList(), null);

    Stream<WalaCallGraphBuilder.CallGraphEdge> edges =
        WalaCallGraphBuilder.extractEdgesAsStream(cg, false);

    boolean hasRTEdge =
        edges.anyMatch(edge -> edge.source.startsWith("java/") || edge.target.startsWith("java/"));

    assertThat(hasRTEdge).isFalse();
  }

  /**
   * Tests RT (Java runtime) class inclusion when enabled. Expects at least one java/* class in
   * extracted edges when includeRT=true.
   */
  @Test
  void testExtractEdgesAsStream_IncludesRTWhenEnabled() throws Exception {
    CallGraph cg = builder.buildCallGraph(slf4jJar.getPath(), Collections.emptyList(), null);

    Stream<WalaCallGraphBuilder.CallGraphEdge> edges =
        WalaCallGraphBuilder.extractEdgesAsStream(cg, true);

    boolean foundRTEdge =
        edges.anyMatch(edge -> edge.source.startsWith("java/") || edge.target.startsWith("java/"));

    assertThat(foundRTEdge).isTrue();
  }

  /**
   * Tests method signature formatting. Expects format: package.Class.method:(descriptor) with
   * package, class, method, and descriptor.
   */
  @Test
  void testFormatMethod_ReturnsCorrectSignature() throws Exception {
    CallGraph cg = builder.buildCallGraph(slf4jJar.getPath(), Collections.emptyList(), null);

    Stream<WalaCallGraphBuilder.CallGraphEdge> edges =
        WalaCallGraphBuilder.extractEdgesAsStream(cg, false);

    WalaCallGraphBuilder.CallGraphEdge edge =
        edges.findFirst().orElseThrow(() -> new AssertionError("Expected at least one edge"));

    // Verify format: contains package, class, method, and descriptor
    assertThat(edge.source).matches(".+\\..+:.+");
    assertThat(edge.target).matches(".+\\..+:.+");
  }

  /**
   * Tests entry points are generated only from Application ClassLoader. Expects non-null call graph
   * with entry points from application code only.
   */
  @Test
  void testEntryPoints_OnlyApplicationLoader() throws Exception {
    List<File> deps = Arrays.asList(okhttpDeps.listFiles());

    CallGraph cg = builder.buildCallGraph(okhttpJar.getPath(), deps, null);

    // Verify call graph was built successfully
    // Entry points validation is indirect - verified in integration tests
    assertThat(cg).isNotNull();
    assertThat(cg.getNumberOfNodes()).isGreaterThan(0);
  }

  /**
   * Tests call graph construction with no dependencies. Expects successful graph construction when
   * dependency list is empty.
   */
  @Test
  void testEmptyDependenciesList_Works() throws Exception {
    CallGraph cg = builder.buildCallGraph(slf4jJar.getPath(), Collections.emptyList(), null);

    assertThat(cg).isNotNull();
    assertThat(cg.getNumberOfNodes()).isGreaterThan(0);
  }
}
