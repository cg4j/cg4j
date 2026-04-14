package net.cg4j.asm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Unit tests for CallGraphResult class. */
class CallGraphResultTest {

  /**
   * Unit test: Tests CallGraphResult.Edge storage. Expects correct source and target method
   * signatures in edge.
   */
  @Test
  void testEdgeStorage() {
    MethodSignature source = new MethodSignature("com/example/A", "foo", "()V");
    MethodSignature target = new MethodSignature("com/example/B", "bar", "()V");

    CallGraphResult.Edge edge = new CallGraphResult.Edge(source, target);

    assertThat(edge.getSource()).isEqualTo(source);
    assertThat(edge.getTarget()).isEqualTo(target);
    assertThat(edge.toString()).contains("com/example/A.foo:()V");
    assertThat(edge.toString()).contains("com/example/B.bar:()V");
  }

  /**
   * Unit test: Tests CallGraphResult.Edge equality. Expects edges with same source and target to be
   * equal.
   */
  @Test
  void testEdgeEquality() {
    MethodSignature source = new MethodSignature("com/example/A", "foo", "()V");
    MethodSignature target = new MethodSignature("com/example/B", "bar", "()V");

    CallGraphResult.Edge edge1 = new CallGraphResult.Edge(source, target);
    CallGraphResult.Edge edge2 = new CallGraphResult.Edge(source, target);

    assertThat(edge1).isEqualTo(edge2);
    assertThat(edge1.hashCode()).isEqualTo(edge2.hashCode());
  }
}
