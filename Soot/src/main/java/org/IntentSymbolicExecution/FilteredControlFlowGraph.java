package org.IntentSymbolicExecution;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;
import soot.Unit;
import soot.toolkits.graph.ExceptionalUnitGraph;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
     * The name of the method analyzed.
     */
    private final String method;

    /**
     * Constructor to initialize the filtered control flow graph.
     *
     * @param fullGraph The complete control flow graph for the method.
     * @param className The name of the class containing the method.
     * @param method    The name method being analyzed.
     */
    public FilteredControlFlowGraph(ExceptionalUnitGraph fullGraph, String className, String method) {
        this.fullGraph = fullGraph;
        this.className = className;
        this.method = method;
        this.filteredCFG = new SimpleGraph<>(DefaultEdge.class);

        startFiltering();
    }

    /**
     * Filter control flow graph that only contains edges related to Intent operations (e.g., getExtra calls).
     */
    private void startFiltering() {

        // Initialize a map to keep track of parameters that we need to monitor during the analysis.
        // The map's key represents the parameter's name, and the value represents its associated type.
        Map<String, String> parametersToTrack = new HashMap<>();

        // Store the count of parameters to track at the beginning of the loop.
        // This helps in detecting when we've finished processing all relevant parameters.
        int startParametersCount;

        // A flag to determine when we should start adding vertices to the filtered control flow graph.
        // Initially, we don't add any vertices until we've encountered the first relevant Intent or Bundle operation.
        boolean startAdding; // Start adding vertices after the root node

        do {
            startParametersCount = parametersToTrack.size();
            resetGraphContent();
            startAdding = false;

            // Iterate through the units in the graph
            for (Unit unit : fullGraph) {
                String line = unit.toString();

                // Match lines containing getExtra methods in Intent or Bundle objects
                if (RegexUtils.patternIntentExtra.matcher(line).find() || RegexUtils.patternBundleExtra.matcher(line).find()) {
                    startAdding = true;
                    boolean isBundle = RegexUtils.patternBundleExtra.matcher(line).find();

                    // Extract the extra and add the corresponding node to the graph
                    Map.Entry<String, String> stringStringPair = extractExtras(line, isBundle);
                    addToGraph(unit);

                    String parameterName = unit.toString().split(" ")[0];
                    parametersToTrack.put(parameterName, stringStringPair.getKey());

                }

                // Continue adding nodes and edges after the first relevant extra is found
                if (!startAdding) continue;

                // Check if any saved parameters are used in the current unit
                if (parametersToTrack.keySet().stream().anyMatch(line::contains)) {
                    addToGraph(unit);

                    // start tracking the new parameter (that depends on a saved parameter)
                    String newParameterName = unit.toString().split(" = ")[0];
                    // Case: $r2 = staticinvoke <java.lang.String: java.lang.String valueOf(int)>(i0)
                    // It stores $r2 in parametersToTrack
                    if (newParameterName.split(" ").length == 1)
                        parametersToTrack.put(newParameterName, newParameterName);

                    String[] newParametersName = unit.toString().split(".<")[0].split(" ");
                    // Case: specialinvoke $r9.<java.math.BigInteger: void <init>(java.lang.String)>($r2)
                    // It stores $r9 in parametersToTrack
                    if (newParametersName.length == 2)
                        parametersToTrack.put(newParametersName[1], newParametersName[1]);

                    // If the line is a lookup switch, track the parameter used
                    if (line.startsWith("lookupswitch")) {
                        Pattern pattern = Pattern.compile("\\(([^)]+)\\)");
                        Matcher matcher = pattern.matcher(line);

                        if (matcher.find())
                            parametersToTrack.put(matcher.group(1), matcher.group(1));
                    }
                }
            }
        }
        // Continue the loop until no new parameters are tracked, meaning we have processed all relevant parameters.
        while (startParametersCount < parametersToTrack.size());
    }


    /**
     * Extracts the key and type of extra parameter from a line of code.
     *
     * @param line The line of code to analyze.
     * @param bundle Whether the line refers to a Bundle object.
     * @return A Map.Entry containing the key and type of the extra.
     */
    public static Map.Entry<String, String> extractExtras(String line, Boolean bundle) {
        String regex = (bundle) ? "<[^:]+:\\s*[^ ]+\\s*get(\\w+)\\s*\\([^)]*\\)>.*\\(\"([^\"]+)\"" : "<[^:]+:\\s*[^ ]+\\s*get(\\w+)Extra\\s*\\([^)]*\\)>.*\\(\"([^\"]+)\"";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(line);

        if (matcher.find()) {
            String type = matcher.group(1);
            String key = matcher.group(2);
            return Map.entry(key, type);
        }
        return null;
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
     */
    public void addToGraph(Unit unit) {
        Map.Entry<String, String> entry = Map.entry("node" + unit.hashCode(), unit.toString());

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
        dotGraph.append(String.format("digraph %s_%s {\n", className.replace(".", "_"), method));

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

    /**
     * Checks if the filtered control flow graph is empty.
     *
     * @return true if the filtered control flow graph contains no vertices,
     *         otherwise false.
     */
    public boolean isEmpty() {
        return filteredCFG.vertexSet().isEmpty();
    }
}
