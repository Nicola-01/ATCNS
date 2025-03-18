package org.IntentSymbolicExecution;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleDirectedGraph;
import soot.Unit;
import soot.toolkits.graph.ExceptionalUnitGraph;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents a Control Flow Graph (CFG) where nodes represent program statements (or units)
 * and edges represent the control flow between these statements.
 * <p>
 * This class can build a CFG from an {@link ExceptionalUnitGraph} (from the Soot framework),
 * or it can be constructed manually. It also supports operations such as adding/removing nodes
 * and edges, retrieving root and leaf nodes, and converting the graph to DOT format for visualization.
 */
public class ControlFlowGraph {

    /**
     * Directed graph where vertices are of type {@link GraphNode} and edges are of type {@link DefaultEdge}.
     */
    private final Graph<GraphNode, DefaultEdge> graph;

    /**
     * Constructs an empty Control Flow Graph.
     */
    public ControlFlowGraph() {
        graph = new SimpleDirectedGraph<>(DefaultEdge.class);
    }

    /**
     * Constructs a Control Flow Graph by copying an existing one.
     *
     * @param controlFlowGraph The existing CFG to copy.
     */
    public ControlFlowGraph(ControlFlowGraph controlFlowGraph) {
        graph = new SimpleDirectedGraph<>(DefaultEdge.class);

        // Copy all vertices.
        for (GraphNode entry : controlFlowGraph.vertexSet())
            addNode(entry);

        // Copy all edges.
        for (DefaultEdge defaultEdge : controlFlowGraph.edgeSet())
            addEdge(defaultEdge);
    }

    /**
     * Constructs a Control Flow Graph from an {@link ExceptionalUnitGraph} (Soot framework).
     *
     * @param graph The ExceptionalUnitGraph representing the control flow of a method.
     */
    public ControlFlowGraph(ExceptionalUnitGraph graph) {
        this.graph = new SimpleDirectedGraph<>(DefaultEdge.class);

        // Map to store mapping from Soot Unit to our GraphNode.
        Map<Unit, GraphNode> nodeMap = new HashMap<>();

        // Create nodes for each Unit in the ExceptionalUnitGraph.
        for (Unit unit : graph) {
            GraphNode node = new GraphNode("node" + unit.hashCode(), unit.toString());
            // Skip exception handling nodes.
            if (node.getValue().endsWith(" := @caughtexception"))
                continue;
            addNode(node);
            nodeMap.put(unit, node);
        }

        // Create edges based on the predecessor relationship in the ExceptionalUnitGraph.
        for (Unit unit : graph) {
            GraphNode node = nodeMap.get(unit);
            if (node == null) continue;
            for (Unit pred : graph.getPredsOf(unit)) {
                GraphNode predNode = nodeMap.get(pred);
                if (predNode == null) continue;
                addEdge(predNode, node);
            }
        }
    }

    /**
     * @return The set of all vertices (nodes) in the graph.
     */
    public Set<GraphNode> vertexSet() {
        return graph.vertexSet();
    }

    /**
     * @return The set of all edges in the graph.
     */
    public Set<DefaultEdge> edgeSet() {
        return graph.edgeSet();
    }

    /**
     * Adds a new node to the graph.
     *
     * @param node The {@link GraphNode} to add.
     */
    public void addNode(GraphNode node) {
        graph.addVertex(node);
    }

    /**
     * Adds an edge between two nodes if they are not identical.
     *
     * @param from The source node.
     * @param to   The target node.
     */
    public void addEdge(GraphNode from, GraphNode to) {
        if (!from.equals(to))
            graph.addEdge(from, to);
    }

    /**
     * Adds an edge to the graph based on an existing {@link DefaultEdge}.
     *
     * @param defaultEdge The edge to add.
     */
    public void addEdge(DefaultEdge defaultEdge) {
        addEdge(getEdgeSource(defaultEdge), getEdgeTarget(defaultEdge));
    }

    /**
     * Removes an edge from the graph.
     *
     * @param edge The edge to remove.
     */
    public void removeEdge(DefaultEdge edge) {
        graph.removeEdge(edge);
    }

    /**
     * Returns the source node of a given edge.
     *
     * @param edge The edge from which to retrieve the source.
     * @return The source {@link GraphNode} of the edge.
     */
    public GraphNode getEdgeSource(DefaultEdge edge) {
        return graph.getEdgeSource(edge);
    }

    /**
     * Returns the target node of a given edge.
     *
     * @param edge The edge from which to retrieve the target.
     * @return The target {@link GraphNode} of the edge.
     */
    public GraphNode getEdgeTarget(DefaultEdge edge) {
        return graph.getEdgeTarget(edge);
    }

    /**
     * Finds a node in the graph by its key.
     *
     * @param nodeKey The key of the node to find.
     * @return The {@link GraphNode} with the specified key, or null if not found.
     */
    public GraphNode findNodeByKey(String nodeKey) {
        for (GraphNode node : vertexSet())
            if (node.equalsKey(nodeKey))
                return node;
        return null;
    }

    /**
     * Finds the source node of an edge by its key.
     *
     * @param edge The edge whose source key is used.
     * @return The {@link GraphNode} that is the source of the edge.
     */
    public GraphNode findNodeByEdgeSourceKey(DefaultEdge edge) {
        return findNodeByKey(getEdgeSource(edge).getKey());
    }

    /**
     * Finds the target node of an edge by its key.
     *
     * @param edge The edge whose target key is used.
     * @return The {@link GraphNode} that is the target of the edge.
     */
    public GraphNode findNodeByEdgeTargetKey(DefaultEdge edge) {
        return findNodeByKey(getEdgeTarget(edge).getKey());
    }

    /**
     * Retrieves the root nodes of the graph. A root node is defined as a node with no incoming edges.
     *
     * @return A list of {@link GraphNode} representing the root nodes.
     */
    public List<GraphNode> getRootsNodes() {
        return graph.vertexSet().stream().filter(v ->
                        graph.incomingEdgesOf(v).isEmpty()) // No incoming edges = start node
                .collect(Collectors.toList());
    }

    /**
     * Retrieves the leaf nodes of the graph. A leaf node is defined as a node with no outgoing edges.
     *
     * @return A list of {@link GraphNode} representing the leaf nodes.
     */
    public List<GraphNode> getLeafNodes() {
        return graph.vertexSet().stream().filter(v ->
                        graph.outgoingEdgesOf(v).isEmpty()) // No outgoing edges = end node
                .collect(Collectors.toList());
    }

    /**
     * Returns the successor nodes of a given node.
     *
     * @param node The node whose successors are to be retrieved.
     * @return A set of {@link GraphNode} that are successors of the given node.
     */
    public Set<GraphNode> getSuccessorNodes(GraphNode node) {
        Set<GraphNode> successors = new HashSet<>();
        for (DefaultEdge edge : graph.outgoingEdgesOf(node))
            successors.add(getEdgeTarget(edge));
        return successors;
    }

    /**
     * Returns the edges originating from a given node.
     *
     * @param node The node whose outgoing edges are to be retrieved.
     * @return A set of {@link DefaultEdge} representing the outgoing edges.
     */
    public Set<DefaultEdge> getSuccessorEdges(GraphNode node) {
        return graph.outgoingEdgesOf(node);
    }

    /**
     * Returns the predecessor nodes of a given node.
     *
     * @param node The node whose predecessors are to be retrieved.
     * @return A set of {@link GraphNode} that are predecessors of the given node.
     */
    public Set<GraphNode> getPredecessorNodes(GraphNode node) {
        Set<GraphNode> predecessors = new HashSet<>();
        for (DefaultEdge edge : graph.incomingEdgesOf(node))
            predecessors.add(getEdgeSource(edge));
        return predecessors;
    }

    /**
     * Returns the edges ending at a given node.
     *
     * @param node The node whose incoming edges are to be retrieved.
     * @return A set of {@link DefaultEdge} representing the incoming edges.
     */
    public Set<DefaultEdge> getPredecessorEdges(GraphNode node) {
        return graph.incomingEdgesOf(node);
    }

    /**
     * Converts the control flow graph to DOT format (Graphviz representation).
     *
     * @return A string representing the graph in DOT format.
     */
    @Override
    public String toString() {
        StringBuilder dotGraph = new StringBuilder();
        dotGraph.append("digraph ControlFlowGraph {\n");

        // Add nodes with their labels.
        for (GraphNode v : vertexSet())
            dotGraph.append(String.format("%s [label=\"%s\"];\n", v.getKey(), v.getValue().replace("\"", "\\\"")));

        // Add edges between nodes.
        for (DefaultEdge e : edgeSet())
            dotGraph.append(String.format("%s -> %s;\n", getEdgeSource(e).getKey(), getEdgeTarget(e).getKey()));

        dotGraph.append("}\n");

        return dotGraph.toString();
    }

    /**
     * Removes a node from the graph and reconnects its predecessors to its successors.
     * <p>
     * For each predecessor that contains the removed node's value, the method replaces that part of the value
     * with the successor's value. Then, it connects each predecessor to each successor (avoiding self-loops).
     *
     * @param node The {@link GraphNode} to remove.
     */
    public void removeVertex(GraphNode node) {
        // Find predecessors and successors.
        Set<GraphNode> predecessors = new HashSet<>();

        // For each predecessor that references the node's value, update it with the successor's value.
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
                if (!predecessor.equals(successor))  // Avoid adding loops.
                    addEdge(predecessor, successor);
            }

        // Remove the current node from the graph.
        graph.removeVertex(node);
    }

    /**
     * Replaces the value of a node identified by its key with a new value.
     * <p>
     * Additionally, if any predecessor nodes reference the old value in their own values, they are also updated.
     *
     * @param nodeKey      The key of the node to update.
     * @param newNodeValue The new value to assign to the node.
     * @return The updated {@link GraphNode}.
     */
    public GraphNode replaceVertex(String nodeKey, String newNodeValue) {
        GraphNode node = findNodeByKey(nodeKey);
        String oldValue = node.getValue();
        node.setNodeValue(newNodeValue);

        for (GraphNode pred : getPredecessorNodes(node))
            if (pred.getValue().contains(oldValue))
                replaceVertex(pred.getKey(), pred.getValue().replace(oldValue, newNodeValue));

        return node;
    }

    /**
     * Represents a node in the control flow graph.
     */
    public static class GraphNode {
        /**
         * Unique identifier for the node.
         */
        private final String NodeKey;
        /**
         * The value or label of the node (typically representing a program statement).
         */
        private String NodeValue;

        /**
         * Constructs a GraphNode with a key and value.
         *
         * @param nodeKey   The unique key for the node.
         * @param nodeValue The value or content of the node.
         */
        public GraphNode(String nodeKey, String nodeValue) {
            this.NodeKey = nodeKey;
            this.NodeValue = nodeValue;
        }

        /**
         * @return The unique key of the node.
         */
        public String getKey() {
            return NodeKey;
        }

        /**
         * @return The value or label of the node.
         */
        public String getValue() {
            return NodeValue;
        }

        /**
         * Sets a new value for the node.
         *
         * @param nodeValue The new value to set.
         */
        public void setNodeValue(String nodeValue) {
            NodeValue = nodeValue;
        }

        /**
         * Compares this node with another for equality based on both key and value.
         *
         * @param node The other node to compare.
         * @return true if both key and value match; false otherwise.
         */
        public boolean equals(GraphNode node) {
            return this.NodeKey.equals(node.NodeKey) && this.NodeValue.equals(node.NodeValue);
        }

        /**
         * Compares the key of this node with another node's key.
         *
         * @param node The node whose key is compared.
         * @return true if keys are equal; false otherwise.
         */
        public boolean equalsKey(GraphNode node) {
            return this.NodeKey.equals(node.NodeKey);
        }

        /**
         * Compares the key of this node with a given key.
         *
         * @param nodeKey The key to compare.
         * @return true if the keys are equal; false otherwise.
         */
        public boolean equalsKey(String nodeKey) {
            return this.NodeKey.equals(nodeKey);
        }

        /**
         * Returns a string representation of the node.
         *
         * @return A string in the format {'key' -> 'value'}.
         */
        @Override
        public String toString() {
            return String.format("{'%s' -> '%s'}", NodeKey, NodeValue);
        }
    }
}
