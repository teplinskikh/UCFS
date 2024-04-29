/*
 * Copyright (C) 2014 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http:
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.common.graph;

import static com.google.common.graph.TestUtil.EdgeType.DIRECTED;
import static com.google.common.graph.TestUtil.EdgeType.UNDIRECTED;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.graph.TestUtil.EdgeType;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@AndroidIncompatible
@RunWith(Parameterized.class)
public final class GraphEquivalenceTest {
  private static final Integer N1 = 1;
  private static final Integer N2 = 2;
  private static final Integer N3 = 3;

  private final EdgeType edgeType;
  private final MutableGraph<Integer> graph;

  @Parameters
  public static Collection<Object[]> parameters() {
    return Arrays.asList(new Object[][] {{EdgeType.UNDIRECTED}, {EdgeType.DIRECTED}});
  }

  public GraphEquivalenceTest(EdgeType edgeType) {
    this.edgeType = edgeType;
    this.graph = createGraph(edgeType);
  }

  private static MutableGraph<Integer> createGraph(EdgeType edgeType) {
    switch (edgeType) {
      case UNDIRECTED:
        return GraphBuilder.undirected().allowsSelfLoops(true).build();
      case DIRECTED:
        return GraphBuilder.directed().allowsSelfLoops(true).build();
      default:
        throw new IllegalStateException("Unexpected edge type: " + edgeType);
    }
  }

  private static EdgeType oppositeType(EdgeType edgeType) {
    switch (edgeType) {
      case UNDIRECTED:
        return EdgeType.DIRECTED;
      case DIRECTED:
        return EdgeType.UNDIRECTED;
      default:
        throw new IllegalStateException("Unexpected edge type: " + edgeType);
    }
  }

  @Test
  public void equivalent_nodeSetsDiffer() {
    graph.addNode(N1);

    MutableGraph<Integer> g2 = createGraph(edgeType);
    g2.addNode(N2);

    assertThat(graph).isNotEqualTo(g2);
  }

  @Test
  public void equivalent_directedVsUndirected() {
    graph.putEdge(N1, N2);

    MutableGraph<Integer> g2 = createGraph(oppositeType(edgeType));
    g2.putEdge(N1, N2);

    assertThat(graph).isNotEqualTo(g2);
  }

  @Test
  public void equivalent_selfLoop_directedVsUndirected() {
    graph.putEdge(N1, N1);

    MutableGraph<Integer> g2 = createGraph(oppositeType(edgeType));
    g2.putEdge(N1, N1);

    assertThat(graph).isNotEqualTo(g2);
  }

  @Test
  public void equivalent_propertiesDiffer() {
    graph.putEdge(N1, N2);

    MutableGraph<Integer> g2 =
        GraphBuilder.from(graph).allowsSelfLoops(!graph.allowsSelfLoops()).build();
    g2.putEdge(N1, N2);

    assertThat(graph).isEqualTo(g2);
  }

  @Test
  public void equivalent_edgeAddOrdersDiffer() {
    GraphBuilder<Integer> builder = GraphBuilder.from(graph);
    MutableGraph<Integer> g1 = builder.build();
    MutableGraph<Integer> g2 = builder.build();

    g1.putEdge(N1, N2);
    g1.putEdge(N3, N1);

    g2.putEdge(N3, N1);
    g2.putEdge(N1, N2);

    assertThat(g1).isEqualTo(g2);
  }

  @Test
  public void equivalent_edgeDirectionsDiffer() {
    graph.putEdge(N1, N2);

    MutableGraph<Integer> g2 = createGraph(edgeType);
    g2.putEdge(N2, N1);

    switch (edgeType) {
      case UNDIRECTED:
        assertThat(graph).isEqualTo(g2);
        break;
      case DIRECTED:
        assertThat(graph).isNotEqualTo(g2);
        break;
      default:
        throw new IllegalStateException("Unexpected edge type: " + edgeType);
    }
  }
}