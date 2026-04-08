package net.cg4j.asm;

import java.util.Collections;
import java.util.Set;

/**
 * Contains the result of call graph construction.
 */
public final class CallGraphResult {

  private final Set<Edge> edges;
  private final Set<MethodSignature> reachableMethods;

  /**
   * Creates a call graph result.
   *
   * @param edges the set of call edges
   * @param reachableMethods the set of reachable methods
   */
  public CallGraphResult(Set<Edge> edges, Set<MethodSignature> reachableMethods) {
    this.edges = Collections.unmodifiableSet(edges);
    this.reachableMethods = Collections.unmodifiableSet(reachableMethods);
  }

  /**
   * Returns the call graph edges.
   *
   * @return the immutable set of call graph edges
   */
  public Set<Edge> getEdges() {
    return edges;
  }

  /**
   * Returns the reachable methods.
   *
   * @return the immutable set of reachable methods
   */
  public Set<MethodSignature> getReachableMethods() {
    return reachableMethods;
  }

  /**
   * Returns the number of edges in the call graph.
   *
   * @return the number of edges in the call graph
   */
  public int getEdgeCount() {
    return edges.size();
  }

  /**
   * Represents a directed edge in the call graph.
   */
  public static final class Edge {
    private final MethodSignature source;
    private final MethodSignature target;

    /**
     * Creates a call graph edge.
     *
     * @param source the calling method
     * @param target the called method
     */
    public Edge(MethodSignature source, MethodSignature target) {
      this.source = source;
      this.target = target;
    }

    /**
     * Returns the calling method.
     *
     * @return the source method
     */
    public MethodSignature getSource() {
      return source;
    }

    /**
     * Returns the called method.
     *
     * @return the target method
     */
    public MethodSignature getTarget() {
      return target;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Edge edge = (Edge) o;
      return source.equals(edge.source) && target.equals(edge.target);
    }

    @Override
    public int hashCode() {
      return 31 * source.hashCode() + target.hashCode();
    }

    @Override
    public String toString() {
      return source.toUri() + " -> " + target.toUri();
    }
  }
}
