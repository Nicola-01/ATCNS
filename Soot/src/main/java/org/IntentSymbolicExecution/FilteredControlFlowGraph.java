package org.IntentSymbolicExecution;

import org.jgrapht.Graph;
import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;
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

    /**
     * The complete control flow graph for the method, provided by Soot.
     */
    private final ExceptionalUnitGraph fullGraph;

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
        this.fullGraph = fullGraph;
        this.completeMethod = completeMethod;
        this.otherMethods = otherMethods;
        this.filteredCFG = new SimpleGraph<>(DefaultEdge.class);

        startFiltering();
    }

    public FilteredControlFlowGraph(FilteredControlFlowGraph filteredControlFlowGraph) {
        this.fullGraph = filteredControlFlowGraph.fullGraph;
        this.completeMethod = filteredControlFlowGraph.completeMethod;
        this.otherMethods = filteredControlFlowGraph.otherMethods;
        this.filteredCFG = new SimpleGraph<>(DefaultEdge.class);
    }

    /**
     * Filter control flow graph that only contains edges related to Intent operations (e.g., getExtra calls).
     */
    private void startFiltering() {

        // Initialize a map to keep track of parameters that we need to monitor during the analysis.
        // The map's key represents the parameter's name, and the value represents its associated type.
        ParametersList parametersList = new ParametersList();

        // Store the count of parameters to track at the beginning of the loop.
        // This helps in detecting when we've finished processing all relevant parameters.
        int startParametersCount;

        // A flag to determine when we should start adding vertices to the filtered control flow graph.
        // Initially, we don't add any vertices until we've encountered the first relevant Intent or Bundle operation.
        boolean startAdding; // Start adding vertices after the root node

        do {
            startParametersCount = parametersList.size();
            parametersList.resetStartAndFinish();
            filteredCFG = new SimpleGraph<>(DefaultEdge.class); // reset graph content
            startAdding = false;

            // Iterate through the units in the graph
            for (Unit unit : fullGraph) {
                String line = unit.toString();

                // Match lines containing getExtra methods in Intent or Bundle objects
                if (patternIntentExtra.matcher(line).find() || patternBundleExtra.matcher(line).find()) {
                    startAdding = true;
                    boolean isBundle = patternBundleExtra.matcher(line).find();

                    // Extract the extra and add the corresponding node to the graph
                    Map.Entry<String, String> stringStringPair = extractExtras(line, isBundle);
                    addToGraph(unit);

                    String parameterName = unit.toString().split(" ")[0];

                    parametersList.addParameter(parameterName, stringStringPair.getKey(), unit);
                    continue;
                }

                // Continue adding nodes and edges after the first relevant extra is found
                if (!startAdding) continue;

                // Check if any saved parameters are used in the current unit
                if (parametersList.containInLine(unit)) {

                    // start tracking the new parameter (that depends on a saved parameter)
                    String newParameterName = unit.toString().split(" = ")[0];
                    // Case: $r2 = staticinvoke <java.lang.String: java.lang.String valueOf(int)>(i0)
                    // It stores $r2 in parametersToTrack
                    if (newParameterName.split(" ").length == 1) {
                                                
                        parametersList.addParameter(newParameterName, newParameterName, unit);
//                        expandMethodCall(unit, 0); TODO to re-enable
                    }

                    String[] newParametersName = unit.toString().split("\\.<")[0].split(" ");
                    // Case: specialinvoke $r9.<java.math.BigInteger: void <init>(java.lang.String)>($r2)
                    // It stores $r9 in parametersToTrack
                    if (newParametersName.length == 2) {
                        newParameterName = newParametersName[1];
                        parametersList.addParameter(newParameterName, newParameterName, unit);
                        //                        expandMethodCall(unit, 0); TODO to re-enable
                    }

                    // If the line is a lookup switch, track the parameter used
                    if (line.startsWith("lookupswitch")) {
                        Pattern pattern = Pattern.compile("\\(([^)]+)\\)");
                        Matcher matcher = pattern.matcher(line);

                        parametersList.addParameter(newParameterName, newParameterName, unit);
                    }

                    addToGraph(unit);


                }
            }
        }
        // Continue the loop until no new parameters are tracked, meaning we have processed all relevant parameters.
        while (startParametersCount < parametersList.size());
    }

    /**
     * Expands a method call by identifying its class, method name, and parameters,
     * and then integrates the corresponding control flow graph into the filtered graph.
     *
     * @param unit The unit representing the method call in the control flow graph.
     */
    private void expandMethodCall(Unit unit, int depth) {
        Matcher matcher = patternCallClass.matcher(unit.toString());

        if (depth == 5) return;
        if (!matcher.find()) return;

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
            addMethodGraphToGraph(otherMethods.get(className + "." + methodName), unit, depth);
        }
    }

    /**
     * Incorporates the control flow graph of a method into the filtered control flow graph.
     * It connects the entry point of the method to the target unit in the caller's control flow graph.
     *
     * @param graph  The control flow graph of the method to be incorporated.
     * @param target The target unit in the caller's graph where the method's graph will be attached.
     */
    private void addMethodGraphToGraph(ExceptionalUnitGraph graph, Unit target, int depth) {
        boolean first = true;
        for (Unit unit : graph) {
            if (first) {
                addToGraph(unit, target);
                first = false;
                continue;
            }
            for (Unit preds : graph.getPredsOf(unit))
                addToGraph(unit, preds);
            if (unit.toString().startsWith("return"))
                addToGraph(target, unit);

            // TODO recursive method usage: to test; add recursive module() function in complexCalculator
            expandMethodCall(unit, depth + 1);
        }
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
     * Adds a unit and its corresponding entry to the filtered control flow graph.
     *
     * @param source The control flow unit from the original graph.
     */
    private void addToGraph(Unit source) {
        addToGraph(source, null);
    }

    /**
     * Adds a unit and its corresponding entry to the filtered control flow graph.
     *
     * @param source The control flow unit from the original graph.
     * @param target The target unit to which this unit should be connected. If {@code null},
     *               predecessors are automatically searched in the full control flow graph
     *               to establish connections.
     */
    private void addToGraph(Unit source, Unit target) {
        Map.Entry<String, String> entry = Map.entry("node" + source.hashCode(), source.toString());

        filteredCFG.addVertex(entry);

        // Map of existing vertices for quick lookup, the key is the node name.
        Map<String, Map.Entry<String, String>> elements = new HashMap<>();
        for (Map.Entry<String, String> e : filteredCFG.vertexSet())
            elements.put(e.getKey(), e);

        if (target == null)
            resolveEdges(source, elements, entry);
        else
            filteredCFG.addEdge(entry, Map.entry("node" + target.hashCode(), target.toString()));
    }

    private void addToGraph(Map.Entry<String, String> vertex, Unit target) {
        filteredCFG.addVertex(vertex);
        filteredCFG.addEdge(vertex, Map.entry("node" + target.hashCode(), target.toString()));
    }

    private void addToGraph(Map.Entry<String, String> vertex, Map.Entry<String, String> target) {
        filteredCFG.addVertex(vertex);
        filteredCFG.addEdge(vertex, target);
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
        dotGraph.append(String.format("digraph %s {\n", completeMethod.replace(".", "_")));

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
     * TODO
     *
     * @param nodeHash
     * @return
     */
    public boolean containsNode(String nodeHash) {
        for (Map.Entry<String, String> entry : filteredCFG.vertexSet())
            if (entry.getKey().equals(nodeHash)) return true;
        return false;
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

    private List<DefaultEdge> getEdgeWithTarget(Map.Entry<String, String> node) {
        List<DefaultEdge> edges = new ArrayList<>();
        for (DefaultEdge edge : filteredCFG.edgeSet()) {
            if (filteredCFG.getEdgeSource(edge).equals(node)) {
                edges.add(edge);
            }
        }
        return edges; // Return null if no such edge is found
    }

    private List<DefaultEdge> getEdgeWithSource(Map.Entry<String, String> node) {
        List<DefaultEdge> edges = new ArrayList<>();
        for (DefaultEdge edge : filteredCFG.edgeSet()) {
            if (filteredCFG.getEdgeTarget(edge).equals(node)) {
                edges.add(edge);
            }
        }
        return edges; // Return null if no such edge is found
    }


    public void switchResolver() {

        FilteredControlFlowGraph switchCFG = new FilteredControlFlowGraph(this);
        List<String> nodesToRemove = new ArrayList<>();

        Map.Entry<String, String> firstSwitchNode = null;
        Map.Entry<String, String> lastSwitchNode = null;

        for (Unit unit : fullGraph) {
            String line = unit.toString();

            if (!containsNode("node" + unit.hashCode())) continue;

            if (line.startsWith("lookupswitch(")) {
                String variableName = line.substring(line.indexOf("(") + 1, line.indexOf(")"));

                Pattern pattern = Pattern.compile("case (\\d+): (.*?);");
                Matcher matcher = pattern.matcher(line);

                List<Map.Entry<Integer, String>> extractedCases = new ArrayList<>();
                while (matcher.find())
                    extractedCases.add(Map.entry(Integer.parseInt(matcher.group(1)), matcher.group(2).trim()));

                List<Unit> succsList = fullGraph.getSuccsOf(unit);
                List<Map.Entry<String, String>> succEntryList = new ArrayList<>();
                Unit defaultNode = null;

                for (Map.Entry<Integer, String> caseEntry : extractedCases) {
                    Unit caseNode = null;
                    for (Unit succ : succsList) {
                        String caseText = caseEntry.getValue().replace("goto ", "");
                        if (succ.toString().contains(caseText)) {
                            caseNode = succ;
                            break;
                        }
                    }

                    if (caseNode == null) continue;
                    if (caseNode.toString().startsWith("goto")) {
                        defaultNode = caseNode;
                        nodesToRemove.add("node" + caseNode.hashCode());
                        continue;
                    }

                    String nodeText = "if " + variableName + "==" + caseEntry.getKey() + " " + caseEntry.getValue();
                    if (succEntryList.isEmpty()) {
                        Map.Entry<String, String> vertex = Map.entry("node" + unit.hashCode(), nodeText);
                        succEntryList.add(vertex);
                        List<DefaultEdge> defaultEdges = getEdgeWithTarget(Map.entry("node" + unit.hashCode(), line));
                        for (DefaultEdge defaultEdge : defaultEdges) {
                            Map.Entry<String, String> predNode = filteredCFG.getEdgeTarget(defaultEdge);
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
                    switchCFG.addToGraph(Map.entry("node"+caseNode.hashCode(), caseNode.toString()), succEntryList.get(succEntryList.size() - 1));
                    nodesToRemove.add("node" + caseNode.hashCode());
                }

                if (defaultNode == null) continue;
                Map.Entry<String, String> vertex = Map.entry("node" + defaultNode.hashCode(), defaultNode.toString());
                switchCFG.addToGraph(vertex, succEntryList.get(succEntryList.size() - 1));

            } else if (!nodesToRemove.contains("node" + unit.hashCode())){
                switchCFG.addToGraph(unit);

                Map.Entry<String, String> vertex = Map.entry("node" + unit.hashCode(), unit.toString());

                List<DefaultEdge> defaultEdges = switchCFG.getEdgeWithTarget(vertex);
                for (DefaultEdge defaultEdge : defaultEdges) {
                    Map.Entry<String, String> predNode = switchCFG.filteredCFG.getEdgeTarget(defaultEdge);
                    if (firstSwitchNode != null && predNode.getKey().equals(firstSwitchNode.getKey())){
                        switchCFG.filteredCFG.removeEdge(defaultEdge);
                        switchCFG.filteredCFG.addEdge(vertex, lastSwitchNode);
                    }
                }
            }

        }
        if (!switchCFG.isEmpty())
            System.out.println(switchCFG);
    }
}
