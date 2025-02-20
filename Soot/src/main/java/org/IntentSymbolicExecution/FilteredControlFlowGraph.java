package org.IntentSymbolicExecution;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleDirectedGraph;
import soot.Unit;
//import soot.jimple.IfStmt;
import soot.toolkits.graph.ExceptionalUnitGraph;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.IntentSymbolicExecution.RegexUtils.*;

/**
 * This class constructs a graph based on the control flow graph provided by the ExceptionalUnitGraph.
 * It filters the nodes from the original graph to focus on Intent-related operations and generates
 * a simplified graph for further analysis.
 */
public class FilteredControlFlowGraph {

    /**
     * The filtered control flow graph represented as a simple graph.
     * Each vertex is a map entry where the key is the node identifier, and the value is the corresponding code snippet.
     */
    private Graph<Map.Entry<String, String>, DefaultEdge> filteredCFG;

    private Graph<Map.Entry<String, String>, DefaultEdge> fullGraph;

    /**
     * The name of the class and the method analyzed.
     */
    private final String completeMethod;

    /**
     * A map of other methods and their corresponding control flow graphs,
     * used for expanding method calls during filtering.
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
        this.fullGraph = graphConvert(fullGraph);

        startFiltering();
        filteredCFG = graphSimplifier();
    }

    /**
     * Constructor to initialize the filtered control flow graph.
     *
     * @param filteredControlFlowGraph todo
     * @param fullGraph
     */
    public FilteredControlFlowGraph(FilteredControlFlowGraph filteredControlFlowGraph, Graph<Map.Entry<String, String>, DefaultEdge> fullGraph) {
        this.fullGraph = fullGraph;
        this.completeMethod = filteredControlFlowGraph.completeMethod;
        this.otherMethods = filteredControlFlowGraph.otherMethods;
        this.filteredCFG = new SimpleDirectedGraph<>(DefaultEdge.class);
    }

    /**
     * todo
     *
     * @return
     */
    public Graph<Map.Entry<String, String>, DefaultEdge> getFilteredCFG() {
        return this.filteredCFG;
    }

    /**
     * Converts ExceptionalUnitGraph to Graph<Map.Entry<String, String>, DefaultEdge>.
     *
     * @param fullGraph The complete control flow graph for the method.
     * @return A simplified graph representation of the control flow graph.
     */
    private Graph<Map.Entry<String, String>, DefaultEdge> graphConvert(ExceptionalUnitGraph fullGraph) {
        Graph<Map.Entry<String, String>, DefaultEdge> graph = new SimpleDirectedGraph<>(DefaultEdge.class);

        List<Graph<Map.Entry<String, String>, DefaultEdge>> methodGraphs = new ArrayList<>();
        List<Map.Entry<String, String>> methodNodes = new ArrayList<>();

        for (Unit unit : fullGraph) {
            Map.Entry<String, String> vertex = Map.entry("node" + unit.hashCode(), unit.toString());
            graph.addVertex(vertex);

/*            if (unit.toString().contains(" goto (branch)")) // todo: check
                if (unit instanceof IfStmt) {
                    IfStmt ifStmt = (IfStmt) unit;
                    Unit trueBranch = ifStmt.getTarget(); // Where it jumps if condition is true
                    List<Unit> successors = fullGraph.getSuccsOf(unit);
                    Unit falseBranch = successors.contains(trueBranch) && successors.size() > 1 ? successors.get(1) : null;

                    System.out.println("True branch: " + trueBranch);
                    System.out.println("False branch: " + falseBranch);
                }*/

            Graph<Map.Entry<String, String>, DefaultEdge> methodGraph = expandMethodCall(vertex, 0, 0);
            if (methodGraph == null) continue;

            methodGraphs.add(methodGraph);
            methodNodes.add(vertex);

            for (Map.Entry<String, String> methodNode : methodGraph.vertexSet())
                graph.addVertex(methodNode);
            for (DefaultEdge methodEdge : methodGraph.edgeSet())
                graph.addEdge(methodGraph.getEdgeSource(methodEdge), methodGraph.getEdgeTarget(methodEdge));
        }

        for (Unit unit : fullGraph) {
            Map.Entry<String, String> vertex = Map.entry("node" + unit.hashCode(), unit.toString());
            for (Unit pred : fullGraph.getPredsOf(unit)) {
                Map.Entry<String, String> predVertex = Map.entry("node" + pred.hashCode(), pred.toString());
                graph.addEdge(predVertex, vertex);
            }
        }

        for (int i = 0; i < methodGraphs.size(); i++) {
            Graph<Map.Entry<String, String>, DefaultEdge> methodGraph = methodGraphs.get(i);
            Map.Entry<String, String> node = methodNodes.get(i);

            List<Map.Entry<String, String>> roots = getRootsNodes(methodGraph);
            List<Map.Entry<String, String>> leafs = getLeafNodes(methodGraph);

            List<Map.Entry<Map.Entry<String, String>, Map.Entry<String, String>>> edgeToRemove = new ArrayList<>();

            for (DefaultEdge defaultEdge : graph.outgoingEdgesOf(node)) {
                edgeToRemove.add(
                        Map.entry(graph.getEdgeSource(defaultEdge), graph.getEdgeTarget(defaultEdge))
                );
                Map.Entry<String, String> target = graph.getEdgeTarget(defaultEdge);
                for (Map.Entry<String, String> leaf : leafs)
                    graph.addEdge(leaf, target);
            }

            for (Map.Entry<Map.Entry<String, String>, Map.Entry<String, String>> edge : edgeToRemove) {
                graph.removeEdge(
                        edge.getKey(),
                        edge.getValue()
                );
            }

            for (Map.Entry<String, String> root : roots)
                graph.addEdge(node, root);
        }

        return graph;
    }

    /**
     * Starts the filtering process to extract nodes related to Intent operations.
     * This method iterates through the full control flow graph and identifies nodes
     * that involve `getExtra` calls or other relevant operations.
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
                if (!startAdding || line.contains(" goto (branch)")) continue; // todo line.contains(" goto (branch)")

                // Check if any saved parameters are used in the current unit
                if (parametersToTrack.keySet().stream().anyMatch(line::contains)) {
                    addToGraph(node);

                    // Start tracking the new parameter (that depends on a saved parameter)
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

    /**
     * Expands a method call by identifying its class, method name, and parameters,
     * and then integrates the corresponding control flow graph into the filtered graph.
     *
     * @param node            The node representing the method call in the control flow graph.
     * @param parametersCount The number of parameters.
     * @param depth           The recursion depth to prevent infinite loops.
     * @return The expanded graph representation of the method.
     */
    private Graph<Map.Entry<String, String>, DefaultEdge> expandMethodCall(Map.Entry<String, String> node, int parametersCount, int depth) {
        Matcher matcher = patternMethodCall.matcher(node.getValue());
        if (!matcher.find() || node.getValue().startsWith("lookupswitch") || depth == 5) return null;

        String className = matcher.group("objectType");
        String methodName = matcher.group("method");
        String arguments = matcher.group("argument");
        String assignation = matcher.group("assignation");

        List<String> argumentList = Arrays.asList(arguments.split(",\\s*"));
        if (otherMethods.containsKey(className + "." + methodName)) {
            ExceptionalUnitGraph graph = otherMethods.get(className + "." + methodName);
            return addMethodGraphToGraph(graph, assignation, argumentList, parametersCount, depth);
        }
        return null;
    }

    /**
     * Incorporates the control flow graph of a method into the filtered control flow graph.
     * It connects the entry point of the method to the node unit in the caller's control flow graph.
     *
     * @param graph          The control flow graph of the method to be incorporated.
     * @param assignation    The variable to which the method's return value is assigned.
     * @param argumentList   The list of arguments passed to the method.
     * @param parameterIndex The index of the current parameter being processed.
     * @param depth          The current depth of recursion to avoid infinite loops.
     * @return A graph representing the expanded method call.
     */
    private Graph<Map.Entry<String, String>, DefaultEdge> addMethodGraphToGraph(ExceptionalUnitGraph graph, String assignation, List<String> argumentList, int parameterIndex, int depth) {
        Graph<Map.Entry<String, String>, DefaultEdge> methodGraph = new SimpleDirectedGraph<>(DefaultEdge.class);

        List<Map.Entry<String, String>> methodParameter = new ArrayList<>();
        int parametersCount = 0;

        for (Unit unit : graph) {
            String line = unit.toString();

            if (line.contains(" := @parameter")) {
                String parameterName = line.split(" := @parameter")[0];
                line = "$m" + parameterIndex + " = " + argumentList.get(parametersCount);
                parametersCount++;

                methodParameter.add(Map.entry(parameterName, "$m" + parameterIndex));
                parameterIndex++;
            } else {
                for (Map.Entry<String, String> param : methodParameter)
                    line = line.replace(param.getKey(), param.getValue());
            }
            if (assignation != null && line.startsWith("return ")) {
                line = line.replace("return ", assignation + " = ");
            }

            Map.Entry<String, String> vertex = Map.entry("node" + unit.hashCode(), line);
            methodGraph.addVertex(vertex);

            // TODO recursive method usage: to test; add recursive module() function in complexCalculator
            expandMethodCall(vertex, parameterIndex, depth + 1);
        }

        for (Unit unit : graph) {
            String nodeKey = "node" + unit.hashCode();

            for (Unit preds : graph.getPredsOf(unit)) {
                String predKey = "node" + preds.hashCode();
                methodGraph.addEdge(findVertexByKey(methodGraph, predKey), findVertexByKey(methodGraph, nodeKey));
            }
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
     * Checks if the filtered control flow graph is empty.
     *
     * @return true if the filtered control flow graph contains no vertices,
     * otherwise false.
     */
    public boolean isEmpty() {
        return filteredCFG.vertexSet().isEmpty();
    }

    /**
     * Finds a vertex in the graph by its key.
     *
     * @param graph   The graph to search.
     * @param nodeKey The key of the node to find.
     * @return The vertex corresponding to the key, or null if not found.
     */
    private Map.Entry<String, String> findVertexByKey(Graph<Map.Entry<String, String>, DefaultEdge> graph, String nodeKey) {
        for (Map.Entry<String, String> node : graph.vertexSet())
            if (node.getKey().equals(nodeKey))
                return node;
        return null;
    }

    /**
     * Retrieves the root nodes of the given graph.
     * A root node is defined as a node with no incoming edges.
     *
     * @param graph The graph from which to extract root nodes.
     * @return A list of entries representing the root nodes of the graph.
     */
    public List<Map.Entry<String, String>> getRootsNodes(Graph<Map.Entry<String, String>, DefaultEdge> graph) {
        return graph.vertexSet().stream().filter(v ->
                        graph.incomingEdgesOf(v).isEmpty()) // No incoming edges = start node
                .collect(Collectors.toList());
    }

    /**
     * Retrieves the leaf nodes of the given graph.
     * A leaf node is defined as a node with no outgoing edges.
     *
     * @param graph The graph from which to extract leaf nodes.
     * @return A list of entries representing the leaf nodes of the graph.
     */
    public List<Map.Entry<String, String>> getLeafNodes(Graph<Map.Entry<String, String>, DefaultEdge> graph) {
        return graph.vertexSet().stream().filter(v ->
                        graph.outgoingEdgesOf(v).isEmpty()) // No outgoing edges = end node
                .collect(Collectors.toList());
    }


    /**
     * Adds a unit and its corresponding entry to the filtered control flow graph.
     *
     * @param node The control flow unit from the original graph.
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

    /**
     * Resolves edges for a given unit by connecting it to its predecessors in the filtered graph.
     *
     * @param starterNode The node from which the edge resolution starts.
     * @param node        The node for which edges are being resolved.
     */
    private void resolveEdges(Map.Entry<String, String> starterNode, Map.Entry<String, String> node) {
        for (DefaultEdge defaultEdge : fullGraph.incomingEdgesOf(node)) {
            Map.Entry<String, String> edgeSource = filteredCFG.getEdgeSource(defaultEdge);
            Map.Entry<String, String> sourceNode = findVertexByKey(filteredCFG, edgeSource.getKey()); // For resolve a switch node rename issues
            if (sourceNode != null) { // edgeSource node is not in the filteredCFG
/*                if (sourceNode.getKey().equals(starterNode.getKey())) {
                    Random rand = new Random();
                    int randomNum = rand.nextInt(999999999);
                    Map.Entry<String, String> tempNode = Map.entry("nodeTMP" + randomNum, "TMP_TMP_TMP_TMP");
                    filteredCFG.addVertex(tempNode);
                    filteredCFG.addEdge(sourceNode, tempNode);
                    filteredCFG.addEdge(tempNode, sourceNode);
                } else*/
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
     * Remove vertex and reconnects their predecessors to successors.
     */
    private void removeVertex(Map.Entry<String, String> node) {
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

    /**
     * @param oldNode
     * @param newNode
     */
    private void replaceVertex(Map.Entry<String, String> oldNode, Map.Entry<String, String> newNode) {

        // dont change the graph order
        Graph<Map.Entry<String, String>, DefaultEdge> simplifiedGraph = new SimpleDirectedGraph<>(DefaultEdge.class);

        for (Map.Entry<String, String> node : filteredCFG.vertexSet()) {
            if (node.getKey().equals(oldNode.getKey()))
                simplifiedGraph.addVertex(newNode);
            else
                simplifiedGraph.addVertex(node);
        }

        for (DefaultEdge edge : filteredCFG.edgeSet()) {
            if(filteredCFG.getEdgeSource(edge).equals(oldNode))
                simplifiedGraph.addEdge(newNode, filteredCFG.getEdgeTarget(edge));
            else if (filteredCFG.getEdgeTarget(edge).equals(oldNode))
                simplifiedGraph.addEdge(filteredCFG.getEdgeSource(edge), newNode);
            else
                simplifiedGraph.addEdge(filteredCFG.getEdgeSource(edge), filteredCFG.getEdgeTarget(edge));
        }

        filteredCFG = simplifiedGraph;
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
        for (Map.Entry<String, String> node : nodesToRemove.entrySet())
            removeVertex(node);
    }

    private void stringSwitchSimplifier() {
        List<Map.Entry<String, String>> nodesToRemove = new ArrayList<>();

        Map<Map.Entry<String, String>, Map.Entry<String, String>> nodesToReplace = new HashMap<>();

        for (Map.Entry<String, String> node : filteredCFG.vertexSet()) {
            String line = node.getValue();
            if (line.startsWith("lookupswitch(") && filteredCFG.incomingEdgesOf(node).size() == 1) {
                DefaultEdge firstEdge = filteredCFG.incomingEdgesOf(node).stream().findFirst().orElse(null);
                String hashCall = filteredCFG.getEdgeSource(firstEdge).getValue();
                if (hashCall.endsWith(".hashCode()")) {
                    String strParameter = hashCall.substring(hashCall.lastIndexOf(" ") + 1, hashCall.indexOf(".hashCode()"));
                    String intParameter = hashCall.substring(0, hashCall.indexOf(" "));
                    nodesToRemove.add(filteredCFG.getEdgeSource(firstEdge));

                    Matcher matcher = casePattern.matcher(line);

                    StringBuilder newLine = new StringBuilder(String.format("lookupswitch(%s) {", strParameter));
                    while (matcher.find()) {
                        if (matcher.group("case").equals("default"))
                            newLine.append(String.format(" %s; ", matcher.group("switchCase")));
                        else if (matcher.group("equals") == null) continue;
                        else
                            newLine.append(String.format(" case %s: %s; ", matcher.group("equals"), matcher.group("goto")));
                    }
//                        extractedCases.add(Map.entry(matcher.group(1), matcher.group(2).trim()));
                    newLine.append("}");
                    nodesToReplace.put(node, Map.entry(node.getKey(), newLine.toString()));
                }
            }
        }

        for (Map.Entry<String, String> node : nodesToRemove)
            removeVertex(node);

        for (Map.Entry<String, String> node : nodesToReplace.keySet())
            replaceVertex(node, nodesToReplace.get(node));
    }

    /**
     * Resolves switch statements in the control flow graph by converting them into
     * a series of conditional branches. This method simplifies the graph by replacing
     * switch nodes with a sequence of if-else conditions.
     *
     * @return A new instance of FilteredControlFlowGraph with resolved switch statements,
     * or null if no switch statements were found.
     */
    public FilteredControlFlowGraph switchResolver() {
        FilteredControlFlowGraph switchCFG = new FilteredControlFlowGraph(this, filteredCFG);
        List<String> nodesToRemove = new ArrayList<>();

        Map.Entry<String, String> firstSwitchNode = null;
        Map.Entry<String, String> lastSwitchNode = null;

        stringSwitchSimplifier();

        for (Map.Entry<String, String> node : filteredCFG.vertexSet()) {
            String line = node.getValue();

            if (line.startsWith("lookupswitch(")) {
                String variableName = line.substring(line.indexOf("(") + 1, line.indexOf(")"));

                Pattern pattern = Pattern.compile("case (.*?): (.*?);");
                Matcher matcher = pattern.matcher(line);

                List<Map.Entry<String, String>> extractedCases = new ArrayList<>();
                while (matcher.find())
                    extractedCases.add(Map.entry(matcher.group(1), matcher.group(2).trim()));

                Set<DefaultEdge> succsList = filteredCFG.outgoingEdgesOf(node);
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
                    switchCFG.filteredCFG.addEdge(lastSwitchNode, switchCFG.filteredCFG.getEdgeTarget(defaultEdge));

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

    /**
     * Simplifies the control flow graph by relabeling nodes and removing unnecessary edges.
     * This method is used to make the graph more readable and easier to analyze.
     *
     * @return A simplified version of the control flow graph.
     */
    private Graph<Map.Entry<String, String>, DefaultEdge> graphSimplifier() {
        Graph<Map.Entry<String, String>, DefaultEdge> simplifiedGraph = new SimpleDirectedGraph<>(DefaultEdge.class);

        List<Map.Entry<String, String>> nodesRelabeled = new ArrayList<>();

        for (Map.Entry<String, String> node : filteredCFG.vertexSet()) {
            String nodeLabel = node.getValue();
            String newNodeLabel = "";

            Matcher matcher = patternMethodCall.matcher(nodeLabel);
            if (matcher.find()) {
                String assignation = matcher.group("assignation");
                String invoke = matcher.group("invoke");
                String object = matcher.group("object");
                String objectType = matcher.group("objectType");
                String returnedType = matcher.group("returnedType");
                String method = matcher.group("method");
                //String argumentType = matcher.group("argumentType");
                String argument = matcher.group("argument");
                if (method.equals("equals"))
                    newNodeLabel = String.format("%s = %s == %s", assignation, object, argument);
                else if (invoke.equals("virtualinvoke") && object != null)
                    newNodeLabel = String.format("%s (%s) = (%s) %s.%s(%s)", assignation, returnedType, objectType, object, method, argument);
                else if (invoke != null && object == null && assignation != null)
                    newNodeLabel = String.format("%s (%s) = (%s).%s(%s)", assignation, returnedType, objectType, method, argument);
                else if (assignation == null)
                    newNodeLabel = String.format("(%s) (%s).%s(%s)", returnedType, objectType, method, argument);
                else
                    newNodeLabel = nodeLabel;
                nodesRelabeled.add(Map.entry(nodeLabel, newNodeLabel));
            }
        }

        for (Map.Entry<String, String> node : filteredCFG.vertexSet()) {
            String nodeLabel = node.getValue();

            for (Map.Entry<String, String> nodeRelabeled : nodesRelabeled)
                nodeLabel = nodeLabel.replace(nodeRelabeled.getKey(), nodeRelabeled.getValue());

            simplifiedGraph.addVertex(Map.entry(node.getKey(), nodeLabel));
        }

        for (DefaultEdge defaultEdge : filteredCFG.edgeSet()) {
            Map.Entry<String, String> source = filteredCFG.getEdgeSource(defaultEdge);
            Map.Entry<String, String> target = filteredCFG.getEdgeTarget(defaultEdge);
            String sourceLabel = source.getValue();
            String targetLabel = target.getValue();
            for (Map.Entry<String, String> nodeRelabeled : nodesRelabeled) {
                sourceLabel = sourceLabel.replace(nodeRelabeled.getKey(), nodeRelabeled.getValue());
                targetLabel = targetLabel.replace(nodeRelabeled.getKey(), nodeRelabeled.getValue());

                source = Map.entry(source.getKey(), sourceLabel);
                target = Map.entry(target.getKey(), targetLabel);
            }

            simplifiedGraph.addEdge(source, target);
        }

        return simplifiedGraph;
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
            dotGraph.append(String.format("%s -> %s;\n", source, target));
        }

        dotGraph.append("}\n");

        return dotGraph.toString();
    }
}