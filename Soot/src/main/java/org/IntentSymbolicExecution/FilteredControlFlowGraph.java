package org.IntentSymbolicExecution;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;
import soot.SootMethod;
import soot.Unit;
import soot.toolkits.graph.ExceptionalUnitGraph;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * This class constructs a graph based on the control flow graph provided by the ExceptionalUnitGraph.
 * And permits to filter the nodes from the original graph.
 */
public class FilteredControlFlowGraph {

    /**
     * The filtered control flow graph represented as a simple graph.
     * Each vertex is a map entry where the key is the node identifier, and the value is the corresponding code snippet.
     */
    private Graph<Map.Entry<String, String>, DefaultEdge> filteredCFG;

    /**
     * The complete control flow graph for the method, provided by Soot.
     */
    private final ExceptionalUnitGraph fullGraph;

    /**
     * The name of the class containing the method being analyzed.
     */
    private final String className;

    /**
     * The Soot method for which the control flow graph is constructed.
     */
    private final SootMethod method;

    /**
     * Constructor to initialize the filtered control flow graph.
     *
     * @param fullGraph The complete control flow graph for the method.
     * @param className The name of the class containing the method.
     * @param method    The Soot method being analyzed.
     */
    public FilteredControlFlowGraph(ExceptionalUnitGraph fullGraph, String className, SootMethod method) {
        this.fullGraph = fullGraph;
        this.className = className;
        this.method = method;
        this.filteredCFG = new SimpleGraph<>(DefaultEdge.class);
    }

    /**
     * Resets the content of the filtered control flow graph, clearing all nodes and edges.
     */
    public void resetGraphContent() {
        filteredCFG = new SimpleGraph<>(DefaultEdge.class);
    }

    /**
     * Adds a unit and its corresponding entry to the filtered control flow graph.
     *
     * @param unit  The control flow unit from the original graph.
     * @param entry The corresponding entry (node identifier and code snippet) to add to the graph.
     */
    public void addToGraph(Unit unit, Map.Entry<String, String> entry) {
        filteredCFG.addVertex(entry);

        // Map of existing vertices for quick lookup, the key is the node name.
        Map<String, Map.Entry<String, String>> elements = new HashMap<>();
        for (Map.Entry<String, String> e : filteredCFG.vertexSet())
            elements.put(e.getKey(), e);

        resolveEdges(unit, elements, entry);
    }

    /**
     * Resolves edges for a given unit by connecting it to its predecessors in the filtered graph.
     *
     * @param unit     The current control flow unit.
     * @param elements Map of existing vertices in the filtered graph.
     * @param entry    The current entry to connect.
     */
    private void resolveEdges(Unit unit, Map<String, Map.Entry<String, String>> elements, Map.Entry<String, String> entry) {
        for (Unit pred : fullGraph.getPredsOf(unit)) {
            if (elements.containsKey("node" + pred.hashCode())) {
                filteredCFG.addEdge(entry, elements.get("node" + pred.hashCode()));
                continue;
            }

            // Recursively resolve edges for predecessors.
            if (!fullGraph.getPredsOf(pred).isEmpty()) {
                resolveEdges(pred, elements, entry);
            }
        }
    }

    /**
     * Generates a DOT representation of the filtered control flow graph.
     *
     * @return A string representing the graph in DOT format.
     */
    public String toString() {
        // Remove "goto" vertices before generating the DOT representation.
        removeGoToVertex();

        StringBuilder dotGraph = new StringBuilder();
        dotGraph.append(String.format("digraph %s_%s {\n", className.replace(".", "_"), method.getName()));

        // Add nodes and their labels.
        for (Map.Entry<String, String> v : filteredCFG.vertexSet()) {
            String nodeName = v.getKey();
            dotGraph.append(String.format("%s [label=\"%s\"];\n", nodeName, v.getValue().replace("\"", "\\\"")));
        }

        // Add edges between nodes.
        for (DefaultEdge e : filteredCFG.edgeSet()) {
            String[] edge = e.toString().split(" : ");
            String target = edge[0].substring(1, edge[0].indexOf('='));
            String source = edge[1].substring(0, edge[1].indexOf('='));
            dotGraph.append(String.format("%s -> %s ;\n", source, target));
        }

        dotGraph.append("}\n");

        return dotGraph.toString();
    }

    /**
     * Removes vertices representing "goto" statements and reconnects their predecessors to successors.
     */
    private void removeGoToVertex() {
        Map<String, String> nodesToRemove = new HashMap<>();

        // Identify vertices to remove.
        for (Map.Entry<String, String> vertex : filteredCFG.vertexSet())
            if (vertex.getValue().startsWith("goto"))
                nodesToRemove.put(vertex.getKey(), vertex.getValue());

        // Reconnect predecessors and successors for each node to be removed.
        for (Map.Entry<String, String> node : nodesToRemove.entrySet()) {
            // Find predecessors and successors
            Set<Map.Entry<String, String>> predecessors = new HashSet<>();
            for (DefaultEdge incomingEdge : filteredCFG.incomingEdgesOf(node))
                predecessors.add(filteredCFG.getEdgeSource(incomingEdge));

            Set<Map.Entry<String, String>> successors = new HashSet<>();
            for (DefaultEdge outgoingEdge : filteredCFG.outgoingEdgesOf(node))
                successors.add(filteredCFG.getEdgeTarget(outgoingEdge));

            // Connect each predecessor to each successor, avoiding loops.
            for (Map.Entry<String, String> predecessor : predecessors)
                for (Map.Entry<String, String> successor : successors)
                    if (!predecessor.equals(successor))  // Avoid adding loops
                        filteredCFG.addEdge(predecessor, successor);


            // Remove the current node.
            filteredCFG.removeVertex(node);
        }
    }
}
