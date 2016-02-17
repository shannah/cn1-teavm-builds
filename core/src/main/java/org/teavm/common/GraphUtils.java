/*
 *  Copyright 2011 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.teavm.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 *
 * @author Alexey Andreev
 */
public final class GraphUtils {
    static final byte NONE = 0;
    static final byte VISITING = 1;
    static final byte VISITED = 2;

    private GraphUtils() {
    }

    public static int[] findBackEdges(Graph graph) {
        int sz = graph.size();
        int[] stack = new int[sz * 2];
        int stackSize = 0;
        byte[] state = new byte[sz];
        for (int i = 0; i < sz; ++i) {
            if (graph.incomingEdgesCount(i) == 0) {
                stack[stackSize++] = i;
            }
        }
        IntegerArray result = new IntegerArray(2);
        while (stackSize > 0) {
            int node = stack[--stackSize];
            switch (state[node]) {
                case NONE:
                    state[node] = VISITING;
                    stack[stackSize++] = node;
                    for (int next : graph.outgoingEdges(node)) {
                        switch (state[next]) {
                            case NONE:
                                stack[stackSize++] = next;
                                break;
                            case VISITING:
                                result.add(node);
                                result.add(next);
                                break;
                        }
                    }
                    break;
                case VISITING:
                    state[node] = VISITED;
                    break;
            }
        }
        return result.getAll();
    }

    public static boolean isIrreducible(Graph graph) {
        DominatorTree dom = buildDominatorTree(graph);
        int[] backEdges = findBackEdges(graph);
        for (int i = 0; i < backEdges.length; i += 2) {
            if (!dom.dominates(backEdges[i + 1], backEdges[i])) {
                return true;
            }
        }
        return false;
    }

    public static int[][] findStronglyConnectedComponents(Graph graph, int[] start) {
        return findStronglyConnectedComponents(graph, start, node -> true);
    }

    /*
     * Tarjan's algorithm
     */
    public static int[][] findStronglyConnectedComponents(Graph graph, int[] start, GraphNodeFilter filter) {
        List<int[]> components = new ArrayList<>();
        int[] visitIndex = new int[graph.size()];
        int[] headerIndex = new int[graph.size()];
        int lastIndex = 0;
        IntegerStack stack = new IntegerStack(graph.size());

        for (int startNode : start) {
            stack.push(startNode);
            IntegerStack currentComponent = new IntegerStack(1);
            while (!stack.isEmpty()) {
                int node = stack.pop();
                if (visitIndex[node] > 0) {
                    if (headerIndex[node] > 0) {
                        continue;
                    }
                    int hdr = visitIndex[node];
                    for (int successor : graph.outgoingEdges(node)) {
                        if (!filter.match(successor)) {
                            continue;
                        }
                        if (headerIndex[successor] == 0) {
                            hdr = Math.min(hdr, visitIndex[successor]);
                        } else {
                            hdr = Math.min(hdr, headerIndex[successor]);
                        }
                    }
                    if (hdr == visitIndex[node]) {
                        IntegerArray componentMembers = new IntegerArray(graph.size());
                        while (true) {
                            int componentMember = currentComponent.pop();
                            componentMembers.add(componentMember);
                            headerIndex[componentMember] = graph.size() + 1;
                            if (visitIndex[componentMember] == hdr) {
                                break;
                            }
                        }
                        components.add(componentMembers.getAll());
                    }
                    headerIndex[node] = hdr;
                } else {
                    visitIndex[node] = ++lastIndex;
                    currentComponent.push(node);
                    stack.push(node);
                    for (int successor : graph.outgoingEdges(node)) {
                        if (!filter.match(successor) || visitIndex[successor] > 0) {
                            continue;
                        }
                        stack.push(successor);
                    }
                }
            }
            for (int i = 0; i < headerIndex.length; ++i) {
                if (visitIndex[i] > 0) {
                    headerIndex[i] = graph.size() + 1;
                }
            }
        }

        return components.toArray(new int[0][]);
    }

    public static DominatorTree buildDominatorTree(Graph graph) {
        DominatorTreeBuilder builder = new DominatorTreeBuilder(graph);
        builder.build();
        return new DefaultDominatorTree(builder.dominators, builder.vertices);
    }

    public static Graph buildDominatorGraph(DominatorTree domTree, int sz) {
        GraphBuilder graph = new GraphBuilder(sz);
        for (int i = 0; i < sz; ++i) {
            int idom = domTree.immediateDominatorOf(i);
            if (idom >= 0) {
                graph.addEdge(idom, i);
            }
        }
        return graph.build();
    }

    public static void splitIrreducibleGraph(Graph graph, int[] weights, GraphSplittingBackend backend) {
        new IrreducibleGraphConverter().convertToReducible(graph, weights, backend);
    }

    public static int[][] findDominanceFrontiers(Graph cfg, DominatorTree domTree) {
        IntegerArray[] tmpFrontiers = new IntegerArray[cfg.size()];
        int[][] domFrontiers = new int[cfg.size()][];

        // For each node calculate the number of descendants in dominator tree
        int[] descCount = new int[cfg.size()];
        for (int i = 0; i < cfg.size(); ++i) {
            int idom = domTree.immediateDominatorOf(i);
            if (idom >= 0) {
                descCount[idom]++;
            }
        }

        // Push final nodes onto stack
        int[] stack = new int[cfg.size() * 2];
        int head = 0;
        for (int i = 0; i < cfg.size(); ++i) {
            if (descCount[i] == 0) {
                stack[head++] = i;
            }
        }

        // Process dominator tree in bottom-up order
        while (head > 0) {
            int node = stack[--head];
            IntegerArray frontier = tmpFrontiers[node];
            if (frontier == null) {
                frontier = new IntegerArray(1);
            }
            int idom = domTree.immediateDominatorOf(node);
            for (int successor : cfg.outgoingEdges(node)) {
                // If successor's immediate dominator is not the node,
                // then add successor to node's dominance frontiers
                if (domTree.immediateDominatorOf(successor) != node) {
                    frontier.add(successor);
                }
            }

            tmpFrontiers[node] = null;
            int[] frontierSet = makeSet(frontier);
            domFrontiers[node] = frontierSet;

            if (idom >= 0) {
                // Propagate current set to immediate dominator
                for (int element : frontierSet) {
                    if (domTree.immediateDominatorOf(element) != idom) {
                        IntegerArray idomFrontier = tmpFrontiers[idom];
                        if (idomFrontier == null) {
                            idomFrontier = new IntegerArray(1);
                            tmpFrontiers[idom] = idomFrontier;
                        }
                        idomFrontier.add(element);
                    }
                }

                // Schedule processing the immediate dominator if all of its ancestors
                // in dominator tree have been processed
                if (--descCount[idom] == 0) {
                    stack[head++] = idom;
                }
            }
        }

        return domFrontiers;
    }

    private static int[] makeSet(IntegerArray array) {
        int[] items = array.getAll();
        int[] set = new int[items.length];
        int sz = 0;
        int last = -1;
        for (int i = 0; i < items.length; ++i) {
            int item = items[i];
            if (item != last) {
                set[sz++] = item;
                last = item;
            }
        }
        if (sz != set.length) {
            set = Arrays.copyOf(set, sz);
        }
        return set;
    }
}
