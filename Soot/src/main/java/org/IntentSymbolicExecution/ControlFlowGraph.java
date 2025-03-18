package org.IntentSymbolicExecution;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleDirectedGraph;
import soot.Unit;
import soot.toolkits.graph.ExceptionalUnitGraph;

import java.util.*;
import java.util.stream.Collectors;

public class ControlFlowGraph {

    private final Graph<GraphNode, DefaultEdge> graph;

    public ControlFlowGraph() {
        graph = new SimpleDirectedGraph<>(DefaultEdge.class);
    }

    public ControlFlowGraph(ControlFlowGraph controlFlowGraph) {
        graph = new SimpleDirectedGraph<>(DefaultEdge.class);

        for (GraphNode entry : controlFlowGraph.vertexSet())
            addVertex(entry);

        for (DefaultEdge defaultEdge : controlFlowGraph.edgeSet())
            addEdge(defaultEdge);
    }

    public ControlFlowGraph(ExceptionalUnitGraph graph) {
        this.graph = new SimpleDirectedGraph<>(DefaultEdge.class);

        Map<Unit, GraphNode> nodeMap = new HashMap<>();

        for (Unit unit : graph) {
            GraphNode node = new GraphNode("node" + unit.hashCode(), unit.toString());
            addVertex(node);
            nodeMap.put(unit, node);
        }

        for (Unit unit : graph) {
            GraphNode node = nodeMap.get(unit);
            for (Unit pred : graph.getPredsOf(unit))
                addEdge(nodeMap.get(pred), node);
        }
    }

    public Set<GraphNode> vertexSet() {
        return graph.vertexSet();
    }

    public Set<DefaultEdge> edgeSet() {
        return graph.edgeSet();
    }

    public void addVertex(GraphNode vertex) {
        graph.addVertex(vertex);
    }

    public void addEdge(GraphNode from, GraphNode to) {
        graph.addEdge(from, to);
    }

    public void addEdge(DefaultEdge defaultEdge) {
        addEdge(getEdgeSource(defaultEdge), getEdgeTarget(defaultEdge));
    }

    public void removeEdge(DefaultEdge edge) {
        graph.removeEdge(edge);
    }

    public GraphNode getEdgeSource(DefaultEdge edge) {
        return graph.getEdgeSource(edge);
    }

    public GraphNode getEdgeTarget(DefaultEdge edge) {
        return graph.getEdgeTarget(edge);
    }


    /**
     * Finds a vertex in the graph by its key.
     *
     * @param nodeKey The key of the node to find.
     * @return The vertex corresponding to the key, or null if not found.
     */
    public GraphNode findNodeByKey(String nodeKey) {
        for (GraphNode node : vertexSet())
            if (node.equalsKey(nodeKey))
                return node;
        return null;
    }

    public GraphNode findNodeByEdgeSourceKey(DefaultEdge edge) {
        return findNodeByKey(getEdgeSource(edge).getKey());
    }

    public GraphNode findNodeByEdgeTargetKey(DefaultEdge edge) {
        return findNodeByKey(getEdgeTarget(edge).getKey());
    }

    /**
     * Retrieves the root nodes of the given graph.
     * A root node is defined as a node with no incoming edges.
     *
     * @return A list of entries representing the root nodes of the graph.
     */
    public List<GraphNode> getRootsNodes() {
        return graph.vertexSet().stream().filter(v ->
                        graph.incomingEdgesOf(v).isEmpty()) // No incoming edges = start node
                .collect(Collectors.toList());
    }

    /**
     * Retrieves the leaf nodes of the given graph.
     * A leaf node is defined as a node with no outgoing edges.
     *
     * @return A list of entries representing the leaf nodes of the graph.
     */
    public List<GraphNode> getLeafNodes() {
        return graph.vertexSet().stream().filter(v ->
                        graph.outgoingEdgesOf(v).isEmpty()) // No outgoing edges = end node
                .collect(Collectors.toList());
    }

    public Set<GraphNode> getSuccessorNodes(GraphNode node) {
        Set<GraphNode> successors = new HashSet<>();
        for (DefaultEdge edge : graph.outgoingEdgesOf(node))
            successors.add(getEdgeTarget(edge));

        return successors;
    }

    public Set<DefaultEdge> getSuccessorEdges(GraphNode node) {
        return graph.outgoingEdgesOf(node);
    }

    public Set<GraphNode> getPredecessorNodes(GraphNode node) {
        Set<GraphNode> successors = new HashSet<>();
        for (DefaultEdge edge : graph.incomingEdgesOf(node))
            successors.add(getEdgeSource(edge));

        return successors;
    }

    public Set<DefaultEdge> getPredecessorEdges(GraphNode node) {
        return graph.incomingEdgesOf(node);
    }


    @Override
    public String toString() {
        StringBuilder dotGraph = new StringBuilder();
        dotGraph.append("digraph ControlFlowGraph {\n");

        // Add nodes and their labels.
        for (GraphNode v : vertexSet())
            dotGraph.append(String.format("%s [label=\"%s\"];\n", v.getKey(), v.getValue().replace("\"", "\\\"")));

        // Add edges between nodes.
        for (DefaultEdge e : edgeSet())
            dotGraph.append(String.format("%s -> %s;\n", getEdgeSource(e).getKey(), getEdgeTarget(e).getKey()));

        dotGraph.append("}\n");

        return dotGraph.toString();
    }

    /**
     * Remove vertex and reconnects their predecessors to successors.
     */
    public void removeVertex(GraphNode node) {
        // Find predecessors and successors
        Set<GraphNode> predecessors = new HashSet<>();

        // change: goto removedNodeValue -> goto newNextNodeValue
        for (GraphNode pred : getPredecessorNodes(node)) {
            if (pred.getValue().contains(node.getValue())) {
                for (GraphNode succ : getSuccessorNodes(node))
                    pred = replaceVertex(pred.getKey(), pred.getValue().replace(node.getValue(), succ.getValue()));
            }
            predecessors.add(pred);
        }

        Set<GraphNode> successors = getSuccessorNodes(node);

        // Connect each predecessor to each successor, avoiding loops.
        for (GraphNode predecessor : predecessors)
            for (GraphNode successor : successors) {
                if (predecessor.getValue().startsWith("if") && predecessor.getValue().contains(node.getValue())) {
                    replaceVertex(predecessor.getKey(),
                            predecessor.getValue().replace(node.getValue(), successor.getValue())
                    );
                }
                if (!predecessor.equals(successor))  // Avoid adding loops
                    addEdge(predecessor, successor);
            }

        // Remove the current node.
        graph.removeVertex(node);
    }

    public GraphNode replaceVertex(String nodeKey, String newNodeValue) {
        GraphNode node = findNodeByKey(nodeKey);
        String oldValue = node.getValue();
        node.setNodeValue(newNodeValue);

        for (GraphNode pred : getPredecessorNodes(node))
            if (pred.getValue().contains(oldValue))
                replaceVertex(pred.getKey(), pred.getValue().replace(oldValue, newNodeValue));

        return node;
    }

    public static class GraphNode {
        private final String NodeKey;
        private String NodeValue;

        public GraphNode(String nodeKey, String nodeValue) {
            this.NodeKey = nodeKey;
            this.NodeValue = nodeValue;
        }

        public String getKey() {
            return NodeKey;
        }

        public String getValue() {
            return NodeValue;
        }

        public void setNodeValue(String nodeValue) {
            NodeValue = nodeValue;
        }

        public boolean equals(GraphNode node) {
            return this.NodeKey.equals(node.NodeKey) && this.NodeValue.equals(node.NodeValue);
        }

        public boolean equalsKey(GraphNode node) {
            return this.NodeKey.equals(node.NodeKey);
        }

        public boolean equalsKey(String nodeKey) {
            return this.NodeKey.equals(nodeKey);
        }

        @Override
        public String toString() {
            return String.format("{'%s' -> '%s'}", NodeKey, NodeValue);
        }
    }

}
