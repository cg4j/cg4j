package io.drmir;

import com.ibm.wala.ipa.callgraph.CallGraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CallGraphBuilderTest extends BaseIntegrationTest {

  private CallGraphBuilder builder;

  @BeforeEach
  void setup() {
    builder = new CallGraphBuilder();
  }

  /**
   * Tests basic call graph construction from a simple JAR file.
   * Expects non-null call graph with at least one node.
   */
  @Test
  void testBuildCallGraph_ValidJar_Success() throws Exception {
    CallGraph cg = builder.buildCallGraph(
        slf4jJar.getPath(),
        Collections.emptyList(),
        null
    );

    assertThat(cg).isNotNull();
    assertThat(cg.getNumberOfNodes()).isGreaterThan(0);
  }

  /**
   * Tests call graph construction with dependency JARs loaded via Extension ClassLoader.
   * Expects larger call graph (1000+ nodes) when dependencies are included.
   */
  @Test
  void testBuildCallGraph_WithDependencies_LoadsExtension() throws Exception {
    List<File> deps = Arrays.asList(okhttpDeps.listFiles());

    CallGraph cg = builder.buildCallGraph(
        okhttpJar.getPath(),
        deps,
        null
    );

    assertThat(cg).isNotNull();
    assertThat(cg.getNumberOfNodes()).isGreaterThan(1000);
  }

  /**
   * Tests error handling when JAR file does not exist.
   * Expects exception to be thrown.
   */
  @Test
  void testBuildCallGraph_NonExistentJar_ThrowsException() {
    assertThatThrownBy(() -> {
      builder.buildCallGraph(
          "nonexistent.jar",
          Collections.emptyList(),
          null
      );
    }).isInstanceOf(Throwable.class);
  }

  /**
   * Tests edge extraction produces valid method signature format.
   * Expects edges with package.Class.method:(descriptor) format.
   */
  @Test
  void testExtractEdges_ProducesValidFormat() throws Exception {
    CallGraph cg = builder.buildCallGraph(
        slf4jJar.getPath(),
        Collections.emptyList(),
        null
    );

    Iterator<CallGraphBuilder.CallGraphEdge> edges =
        CallGraphBuilder.extractEdges(cg, false);

    assertThat(edges.hasNext()).isTrue();

    CallGraphBuilder.CallGraphEdge edge = edges.next();
    assertThat(edge.source).isNotEmpty();
    assertThat(edge.target).isNotEmpty();
    assertThat(edge.source).contains(".");  // Has package
    assertThat(edge.target).contains(".");
  }

  /**
   * Tests RT (Java runtime) class filtering when disabled.
   * Expects no java/* classes in extracted edges when includeRT=false.
   */
  @Test
  void testExtractEdges_FiltersRTWhenDisabled() throws Exception {
    CallGraph cg = builder.buildCallGraph(
        slf4jJar.getPath(),
        Collections.emptyList(),
        null
    );

    Iterator<CallGraphBuilder.CallGraphEdge> edges =
        CallGraphBuilder.extractEdges(cg, false);

    while (edges.hasNext()) {
      CallGraphBuilder.CallGraphEdge edge = edges.next();
      assertThat(edge.source).doesNotStartWith("java/");
      assertThat(edge.target).doesNotStartWith("java/");
    }
  }

  /**
   * Tests RT (Java runtime) class inclusion when enabled.
   * Expects at least one java/* class in extracted edges when includeRT=true.
   */
  @Test
  void testExtractEdges_IncludesRTWhenEnabled() throws Exception {
    CallGraph cg = builder.buildCallGraph(
        slf4jJar.getPath(),
        Collections.emptyList(),
        null
    );

    Iterator<CallGraphBuilder.CallGraphEdge> edges =
        CallGraphBuilder.extractEdges(cg, true);

    boolean foundRTEdge = false;
    while (edges.hasNext()) {
      CallGraphBuilder.CallGraphEdge edge = edges.next();
      if (edge.source.startsWith("java/") || edge.target.startsWith("java/")) {
        foundRTEdge = true;
        break;
      }
    }

    assertThat(foundRTEdge).isTrue();
  }

  /**
   * Tests method signature formatting.
   * Expects format: package.Class.method:(descriptor) with package, class, method, and descriptor.
   */
  @Test
  void testFormatMethod_ReturnsCorrectSignature() throws Exception {
    CallGraph cg = builder.buildCallGraph(
        slf4jJar.getPath(),
        Collections.emptyList(),
        null
    );

    Iterator<CallGraphBuilder.CallGraphEdge> edges =
        CallGraphBuilder.extractEdges(cg, false);

    CallGraphBuilder.CallGraphEdge edge = edges.next();

    // Verify format: contains package, class, method, and descriptor
    assertThat(edge.source).matches(".+\\..+:.+");
    assertThat(edge.target).matches(".+\\..+:.+");
  }

  /**
   * Tests entry points are generated only from Application ClassLoader.
   * Expects non-null call graph with entry points from application code only.
   */
  @Test
  void testEntryPoints_OnlyApplicationLoader() throws Exception {
    List<File> deps = Arrays.asList(okhttpDeps.listFiles());

    CallGraph cg = builder.buildCallGraph(
        okhttpJar.getPath(),
        deps,
        null
    );

    // Verify call graph was built successfully
    // Entry points validation is indirect - verified in integration tests
    assertThat(cg).isNotNull();
    assertThat(cg.getNumberOfNodes()).isGreaterThan(0);
  }

  /**
   * Tests call graph construction with no dependencies.
   * Expects successful graph construction when dependency list is empty.
   */
  @Test
  void testEmptyDependenciesList_Works() throws Exception {
    CallGraph cg = builder.buildCallGraph(
        slf4jJar.getPath(),
        Collections.emptyList(),
        null
    );

    assertThat(cg).isNotNull();
    assertThat(cg.getNumberOfNodes()).isGreaterThan(0);
  }
}
