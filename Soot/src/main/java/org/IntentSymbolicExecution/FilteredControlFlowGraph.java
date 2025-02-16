package org.IntentSymbolicExecution;

import org.jgrapht.Graph;
//import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleDirectedGraph;
//import org.jgrapht.graph.SimpleGraph;
import soot.Unit;
import soot.toolkits.graph.ExceptionalUnitGraph;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.IntentSymbolicExecution.RegexUtils.*;

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

    private Graph<Map.Entry<String, String>, DefaultEdge> fullGraph;

    /**
     * The complete control flow graph for the method, provided by Soot.
     */
//    private final ExceptionalUnitGraph fullGraph;

    /**
     * The name of the class and the method analyzed.
     */
    private final String completeMethod;

    /**
     * TODO
     */
    private final Map<String, ExceptionalUnitGraph> otherMethods;

    /**
     * Constructor to initialize the filtered control flow graph.
     *
     * @param fullGraph      The complete control flow graph for the method.
     * @param completeMethod The name of the class and the method analyzed.
     * @param otherMethods   A map of other methods and their corresponding control flow graphs,
     *                       used for expanding method calls during filtering.
     */
    public FilteredControlFlowGraph(ExceptionalUnitGraph fullGraph, String completeMethod, Map<String, ExceptionalUnitGraph> otherMethods) {
        this.completeMethod = completeMethod;
        this.otherMethods = otherMethods;
        this.filteredCFG = new SimpleDirectedGraph<>(DefaultEdge.class);
        this.fullGraph = fullGraphConvert(fullGraph);


        startFiltering();
    }

    private Graph<Map.Entry<String, String>, DefaultEdge> fullGraphConvert(ExceptionalUnitGraph fullGraph) {
        Graph<Map.Entry<String, String>, DefaultEdge> graph = new SimpleDirectedGraph<>(DefaultEdge.class);
        for (Unit unit : fullGraph) {
            Map.Entry<String, String> vertex = Map.entry("node" + unit.hashCode(), unit.toString());
            graph.addVertex(vertex);
        }

        for (Unit unit : fullGraph) {
            Map.Entry<String, String> vertex = Map.entry("node" + unit.hashCode(), unit.toString());
            for (Unit pred : fullGraph.getPredsOf(unit)) {
                Map.Entry<String, String> predVertex = Map.entry("node" + pred.hashCode(), pred.toString());
                graph.addEdge(predVertex, vertex);
            }
        }
        return graph;
    }

    public FilteredControlFlowGraph(FilteredControlFlowGraph filteredControlFlowGraph, Graph<Map.Entry<String, String>, DefaultEdge> fullGraph) {
        this.fullGraph = fullGraph;
        this.completeMethod = filteredControlFlowGraph.completeMethod;
        this.otherMethods = filteredControlFlowGraph.otherMethods;
        this.filteredCFG = new SimpleDirectedGraph<>(DefaultEdge.class);
    }

    public Graph<Map.Entry<String, String>, DefaultEdge> getFilteredCFG() {
        return this.filteredCFG;
    }

    /**
     * Filter control flow graph that only contains edges related to Intent operations (e.g., getExtra calls).
     */
    private void startFiltering() {

        List<Graph<Map.Entry<String, String>, DefaultEdge>> methodGraphs = new ArrayList<>();
        List<Map.Entry<String, String>> methodNodes = new ArrayList<>();

        for (Map.Entry<String, String> node : fullGraph.vertexSet()) {  // todo
            Graph<Map.Entry<String, String>, DefaultEdge> methodGraph = expandMethodCall(node);

            if (methodGraph == null) continue;

            methodGraphs.add(methodGraph);
            methodNodes.add(node);
        }

        for (int i = 0; i < methodGraphs.size(); i++) {
            Graph<Map.Entry<String, String>, DefaultEdge> methodGraph = methodGraphs.get(i);

            for (Map.Entry<String, String> node : methodGraph.vertexSet())
                fullGraph.addVertex(node);
            for (DefaultEdge edge : methodGraph.edgeSet())
                fullGraph.addEdge(methodGraph.getEdgeSource(edge), methodGraph.getEdgeTarget(edge));

            List<Map.Entry<String, String>> roots = getRootsNodes(methodGraph);
            List<Map.Entry<String, String>> leafs = getLeafNodes(methodGraph);

            Map.Entry<String, String> methodNode = methodNodes.get(i);

            List<DefaultEdge> toRemove = new ArrayList<>();

            for (DefaultEdge defaultEdge : fullGraph.outgoingEdgesOf(methodNode)){
                toRemove.add(defaultEdge);
                Map.Entry<String, String> target = fullGraph.getEdgeTarget(defaultEdge);
                for (Map.Entry<String, String> leaf : leafs) // todo: doesn't works
                    fullGraph.addEdge(leaf, target);
            }

            for (DefaultEdge defaultEdge : toRemove)
                fullGraph.removeEdge(defaultEdge);

            for (Map.Entry<String, String> node : roots)
                fullGraph.addEdge(methodNode, node);

        }

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
            for (Map.Entry<String, String> node : fullGraph.vertexSet()) {
                String line = node.getValue();

                // Match lines containing getExtra methods in Intent or Bundle objects
                if (patternIntentExtra.matcher(line).find() || patternBundleExtra.matcher(line).find()) {
                    startAdding = true;
                    boolean isBundle = patternBundleExtra.matcher(line).find();

                    // Extract the extra and add the corresponding node to the graph
                    Map.Entry<String, String> stringStringPair = extractExtras(line, isBundle);
                    addToGraph(node);

                    String parameterName = line.split(" ")[0];
                    parametersToTrack.put(parameterName, stringStringPair.getKey());

                }

                // Continue adding nodes and edges after the first relevant extra is found
                if (!startAdding) continue;

                // Check if any saved parameters are used in the current unit
                if (parametersToTrack.keySet().stream().anyMatch(line::contains)) {
                    addToGraph(node);

                    // start tracking the new parameter (that depends on a saved parameter)
                    String newParameterName = line.split(" = ")[0];
                    // Case: $r2 = staticinvoke <java.lang.String: java.lang.String valueOf(int)>(i0)
                    // It stores $r2 in parametersToTrack
                    if (newParameterName.split(" ").length == 1) {
                        if (!parametersToTrack.containsKey(newParameterName))
                            parametersToTrack.put(newParameterName, newParameterName);
                        continue;
                    }

                    String[] newParametersName = line.split("\\.<")[0].split(" ");
                    // Case: specialinvoke $r9.<java.math.BigInteger: void <init>(java.lang.String)>($r2)
                    // It stores $r9 in parametersToTrack
                    if (newParametersName.length == 2) {
                        newParameterName = newParametersName[1];
                        if (!parametersToTrack.containsKey(newParameterName))
                            parametersToTrack.put(newParameterName, newParameterName);
                        continue;
                    }

                    // If the line is a lookup switch, track the parameter used
                    if (line.startsWith("lookupswitch")) {
                        Pattern pattern = Pattern.compile("\\(([^)]+)\\)");
                        Matcher matcher = pattern.matcher(line);

                        if (matcher.find())
                            parametersToTrack.put(matcher.group(1), matcher.group(1));
                        continue;
                    }


                }
            }
        }
        // Continue the loop until no new parameters are tracked, meaning we have processed all relevant parameters.
        while (startParametersCount < parametersToTrack.size());
    }

    private List<Map.Entry<String, String>> getRootsNodes(Graph<Map.Entry<String, String>, DefaultEdge> graph) {
        return graph.vertexSet().stream().filter(v ->
                        graph.incomingEdgesOf(v).isEmpty()) // No incoming edges = start node
                .collect(Collectors.toList());
    }

    private List<Map.Entry<String, String>> getLeafNodes(Graph<Map.Entry<String, String>, DefaultEdge> graph) {
        return graph.vertexSet().stream().filter(v ->
                        graph.outgoingEdgesOf(v).isEmpty()) // No outgoing edges = end node
                .collect(Collectors.toList());
    }

    /**
     * Expands a method call by identifying its class, method name, and parameters,
     * and then integrates the corresponding control flow graph into the filtered graph.
     *
     * @param unit The unit representing the method call in the control flow graph.
     */
    private Graph<Map.Entry<String, String>, DefaultEdge> expandMethodCall(Map.Entry<String, String> node) {
        Matcher matcher = patternCallClass.matcher(node.getValue());

        if (!matcher.find() || node.getValue().startsWith("lookupswitch")) return null;

        String className = matcher.group("class");
        String methodName = matcher.group("method");
        String parameters = matcher.group("parameters");
        String arguments = matcher.group("arguments");

        List<String> parameterList = Arrays.asList(parameters.split(",\\s*"));
        List<String> argumentList = Arrays.asList(arguments.split(",\\s*"));

//        System.out.println("class = \"" + className + "\"");
//        System.out.println("method = \"" + methodName + "\"");
//        System.out.println("parameters = " + parameterList);
//        System.out.println("arguments = " + argumentList);
//        System.out.println("");

        if (otherMethods.containsKey(className + "." + methodName)) {
//            System.out.println(className + "." + methodName);
            return addMethodGraphToGraph(otherMethods.get(className + "." + methodName)); // TODO
        }
        return null;
    }

    /**
     * Incorporates the control flow graph of a method into the filtered control flow graph.
     * It connects the entry point of the method to the node unit in the caller's control flow graph.
     *
     * @param graph The control flow graph of the method to be incorporated.
     * @param node  The node unit in the caller's graph where the method's graph will be attached.
     */

    private Graph<Map.Entry<String, String>, DefaultEdge> addMethodGraphToGraph(ExceptionalUnitGraph graph) {
        Graph<Map.Entry<String, String>, DefaultEdge> methodGraph = new SimpleDirectedGraph<>(DefaultEdge.class);

        for (Unit unit : graph) {
            Map.Entry<String, String> vertex = Map.entry("node" + unit.hashCode(), unit.toString());
            methodGraph.addVertex(vertex);
            for (Unit preds : graph.getPredsOf(unit))
                methodGraph.addEdge(Map.entry("node" + preds.hashCode(), preds.toString()), vertex);

            // TODO recursive method usage: to test; add recursive module() function in complexCalculator
//            expandMethodCall(node);
        }
        return methodGraph;
    }

    /**
     * Extracts the key and type of extra parameter from a line of code.
     *
     * @param line   The line of code to analyze.
     * @param bundle Whether the line refers to a Bundle object.
     * @return A Map.Entry containing the key and type of the extra.
     */
    private static Map.Entry<String, String> extractExtras(String line, Boolean bundle) {
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
        filteredCFG = new SimpleDirectedGraph<>(DefaultEdge.class);
    }


    /**
     * Adds a unit and its corresponding entry to the filtered control flow graph.
     *
     * @param node The control flow unit from the original graph.
     * @param pred The pred unit to which this unit should be connected. If {@code null},
     *             predecessors are automatically searched in the full control flow graph
     *             to establish connections.
     */
    private void addToGraph(Map.Entry<String, String> node) {
        filteredCFG.addVertex(node);
        resolveEdges(node, node);
    }

    /**
     * Adds a unit and its corresponding entry to the filtered control flow graph.
     *
     * @param node The control flow unit from the original graph.
     * @param pred The pred unit to which this unit should be connected. If {@code null},
     *             predecessors are automatically searched in the full control flow graph
     *             to establish connections.
     */
    private void addToGraph(Map.Entry<String, String> node, Map.Entry<String, String> pred) {
        filteredCFG.addVertex(node);
        filteredCFG.addEdge(pred, node);
    }

    public List<Map.Entry<String, String>> getPredecessors(String destKey) {
        return filteredCFG.vertexSet().stream()
                .filter(entry -> entry.getKey().equals(destKey)) // Find the destination node
                .flatMap(dest -> filteredCFG.incomingEdgesOf(dest).stream() // Get incoming edges
                        .map(filteredCFG::getEdgeSource)) // Get source vertices (predecessors)
                .collect(Collectors.toList()); // Collect the results in a list
    }

    /**
     * Resolves edges for a given unit by connecting it to its predecessors in the filtered graph.
     *
     * @param node TODO
     */
    private void resolveEdges(Map.Entry<String, String> starterNode, Map.Entry<String, String> node) {
        for (DefaultEdge defaultEdge : fullGraph.incomingEdgesOf(node)) {
            Map.Entry<String, String> edgeSource = filteredCFG.getEdgeSource(defaultEdge);
            Map.Entry<String, String> sourceNode = getNodeWithKey(edgeSource.getKey()); // For resolve a switch node rename issues
            if (sourceNode != null) {
                filteredCFG.addEdge(sourceNode, starterNode);
                continue;
            }

            // Recursively resolve edges for predecessors.
            if (!fullGraph.incomingEdgesOf(edgeSource).isEmpty()) {
                resolveEdges(starterNode, edgeSource);
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
        dotGraph.append(String.format("digraph %s {\n", completeMethod.replace(".", "_")));

        // Add nodes and their labels.
        for (Map.Entry<String, String> v : filteredCFG.vertexSet()) {
            String nodeName = v.getKey();
            dotGraph.append(String.format("%s [label=\"%s\"];\n", nodeName, v.getValue().replace("\"", "\\\"")));
        }

        // Add edges between nodes.
        for (DefaultEdge e : filteredCFG.edgeSet()) {
            String[] edge = e.toString().split(" : ");
            String source = edge[0].substring(1, edge[0].indexOf('='));
            String target = edge[1].substring(0, edge[1].indexOf('='));
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
     * TODO
     *
     * @param key
     * @return
     */
    public Map.Entry<String, String> getNodeWithKey(String key) {
        for (Map.Entry<String, String> entry : filteredCFG.vertexSet())
            if (entry.getKey().equals(key)) return entry;
        return null;
    }

    /**
     * Checks if the filtered control flow graph is empty.
     *
     * @return true if the filtered control flow graph contains no vertices,
     * otherwise false.
     */
    public boolean isEmpty() {
        return filteredCFG.vertexSet().isEmpty();
    }

//    private List<DefaultEdge> getEdgeWithTarget(Map.Entry<String, String> node) {
//        List<DefaultEdge> edges = new ArrayList<>();
//        for (DefaultEdge edge : filteredCFG.edgeSet()) {
//            if (filteredCFG.getEdgeSource(edge).equals(node)) {
//                edges.add(edge);
//            }
//        }
//        return edges; // Return null if no such edge is found
//    }

    /*
    private List<DefaultEdge> getEdgeWithSource(Map.Entry<String, String> node) {
        List<DefaultEdge> edges = new ArrayList<>();
        for (DefaultEdge edge : filteredCFG.edgeSet()) {
            if (filteredCFG.getEdgeTarget(edge).equals(node)) {
                edges.add(edge);
            }
        }
        return edges; // Return null if no such edge is found
    }
    */


    public FilteredControlFlowGraph switchResolver() {

        FilteredControlFlowGraph switchCFG = new FilteredControlFlowGraph(this, filteredCFG);
        List<String> nodesToRemove = new ArrayList<>();

        Map.Entry<String, String> firstSwitchNode = null;
        Map.Entry<String, String> lastSwitchNode = null;

        for (Map.Entry<String, String> node : filteredCFG.vertexSet()) {
            String line = node.getValue();

            if (line.startsWith("lookupswitch(")) {
                String variableName = line.substring(line.indexOf("(") + 1, line.indexOf(")"));

                Pattern pattern = Pattern.compile("case (.*?): (.*?);");
                Matcher matcher = pattern.matcher(line);

                List<Map.Entry<String, String>> extractedCases = new ArrayList<>();
                while (matcher.find())
                    extractedCases.add(Map.entry(matcher.group(1), matcher.group(2).trim()));

                Set<DefaultEdge> succsList = fullGraph.outgoingEdgesOf(node);
                List<Map.Entry<String, String>> succEntryList = new ArrayList<>();
                Map.Entry<String, String> defaultNode = null;

                for (Map.Entry<String, String> caseEntry : extractedCases) {
                    Map.Entry<String, String> caseNode = null;

                    for (DefaultEdge defaultEdge : succsList) {
                        Map.Entry<String, String> succ = filteredCFG.getEdgeTarget(defaultEdge);
                        String caseText = caseEntry.getValue().replace("goto ", "");
                        if (succ.getValue().contains(caseText)) {
                            caseNode = succ;
                            break;
                        }
                    }

                    if (caseNode == null) continue;

                    if (caseNode.getValue().startsWith("goto")) {
                        defaultNode = caseNode;
                        nodesToRemove.add(caseNode.getKey());
                        continue;
                    }

                    String nodeText = "if " + variableName + "==" + caseEntry.getKey() + " " + caseEntry.getValue();
                    if (succEntryList.isEmpty()) { // first switch element
                        Map.Entry<String, String> vertex = Map.entry(node.getKey(), nodeText); // use the node name of the switch
                        succEntryList.add(vertex);
                        for (DefaultEdge defaultEdge : filteredCFG.incomingEdgesOf(node)) {
                            Map.Entry<String, String> predNode = filteredCFG.getEdgeSource(defaultEdge);
                            switchCFG.addToGraph(vertex, predNode);
                        }
                        firstSwitchNode = vertex;
                        lastSwitchNode = vertex;
                    } else {
                        Map.Entry<String, String> vertex = Map.entry("node" + Math.abs(nodeText.hashCode()), nodeText);
                        succEntryList.add(vertex);
                        switchCFG.addToGraph(vertex, succEntryList.get(succEntryList.size() - 2));
                        lastSwitchNode = vertex;
                    }
                    switchCFG.addToGraph(caseNode, succEntryList.get(succEntryList.size() - 1));
                    nodesToRemove.add(caseNode.getKey());
                }

                if (defaultNode == null) continue;

                switchCFG.addToGraph(defaultNode, succEntryList.get(succEntryList.size() - 1));

            } else if (!nodesToRemove.contains(node.getKey())) {
                switchCFG.addToGraph(node);

                if (firstSwitchNode == null) continue;

                List<DefaultEdge> toRemove = new ArrayList<>();
                List<DefaultEdge> toAdd = new ArrayList<>();

                for (DefaultEdge defaultEdge : switchCFG.filteredCFG.incomingEdgesOf(node)) {
                    Map.Entry<String, String> predNode = switchCFG.filteredCFG.getEdgeSource(defaultEdge);
                    if (predNode.getKey().equals(firstSwitchNode.getKey())) {
                        // Is not possible to modify the graph inside the foreach
                        toAdd.add(defaultEdge);
                        toRemove.add(defaultEdge);
                    }
                }

                for (DefaultEdge defaultEdge : toAdd)
                    switchCFG.filteredCFG.addEdge(lastSwitchNode, node);

                for (DefaultEdge defaultEdge : toRemove)
                    switchCFG.filteredCFG.removeEdge(defaultEdge);

            }

        }
        if (!switchCFG.isEmpty()) {
            System.out.println(switchCFG);
            return switchCFG;
        }
        return null;
    }
}
