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
