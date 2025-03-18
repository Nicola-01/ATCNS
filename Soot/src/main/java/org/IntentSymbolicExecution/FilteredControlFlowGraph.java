package org.IntentSymbolicExecution;

import org.IntentSymbolicExecution.ControlFlowGraph.GraphNode;

import org.IntentSymbolicExecution.IntentAnalysis.GlobalVariablesInfo;
import org.jgrapht.graph.DefaultEdge;
import soot.toolkits.graph.ExceptionalUnitGraph;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.IntentSymbolicExecution.RegexUtils.*;

/**
 * This class constructs a graph based on the control flow graph provided by the ExceptionalUnitGraph.
 * It filters the nodes from the original graph to focus on Intent-related operations and generates
 * a simplified graph for further analysis.
 */
public class FilteredControlFlowGraph {

    private static final int METHODS_CALL_DEPTH = 1;

    /**
     * The filtered control flow graph represented as a simple graph.
     * Each vertex is a map entry where the key is the node identifier, and the value is the corresponding code snippet.
     */
    private Map<String, String> filteredCFG;


    private ControlFlowGraph fullGraph;

    /**
     * The name of the class and the method analyzed.
     */
    private final String completeMethod;

    private final Map<String, ControlFlowGraph> convertedMethodGraph = new HashMap<>();

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
    public FilteredControlFlowGraph(ExceptionalUnitGraph fullGraph, String completeMethod, Map<String, ExceptionalUnitGraph> otherMethods, Map<String, GlobalVariablesInfo> globalVariables) {
        this.completeMethod = completeMethod;
        this.otherMethods = otherMethods;
        this.filteredCFG = new HashMap<>();
        this.fullGraph = new ControlFlowGraph(fullGraph);

        methodCallResolver();

        removeGoToVertex();
        gotoResolver();

        replaceGlobalVariables(globalVariables);

        graphSimplifier();

        filteredCFG = extractIntentRelatedNodes();

        stringSwitchSimplifier();
        switchResolver();
    }

    public FilteredControlFlowGraph(FilteredControlFlowGraph filteredControlFlowGraph, ControlFlowGraph fullGraph, Map<String, String> filteredCFG) {
        this.fullGraph = new ControlFlowGraph(fullGraph);
        this.completeMethod = filteredControlFlowGraph.completeMethod;
        this.otherMethods = filteredControlFlowGraph.otherMethods;
        this.filteredCFG = new HashMap<>(filteredCFG);
    }

    public ControlFlowGraph getFullCFG() {
        return this.fullGraph;
    }

    public Map<String, String> getFilteredCFG() {
        return filteredCFG;
    }

    public String getCompleteMethod() {
        return this.completeMethod;
    }

    public boolean haveExtras() {
        return !this.filteredCFG.isEmpty();
    }

    private void replaceGlobalVariables(Map<String, GlobalVariablesInfo> globalVariables) {

        for (GraphNode vertex : fullGraph.vertexSet()) {
            Matcher matcher = RegexUtils.globalVariablesPattern.matcher(vertex.getValue());
            if (matcher.matches()) {
                String variable = matcher.group("variable");
                String varName = matcher.group("varname");

                if (globalVariables.containsKey(varName)) {
                    GlobalVariablesInfo globalVariableInfo = globalVariables.get(varName);
                    String variableType = globalVariableInfo.getType();
                    String newNodeValue;
                    if (variableType.toLowerCase().contains("java.lang.string"))
                        newNodeValue = String.format("%s = \"%s\"", variable, globalVariableInfo.getValue());
                    else
                        newNodeValue = String.format("%s = %s", variable, globalVariableInfo.getValue());

                    fullGraph.replaceVertex(vertex.getKey(), newNodeValue);
                }
            }
        }
    }

    private void methodCallResolver() {

        List<String> nodesToExpand = getCallNode(fullGraph, 0);

        int parameterUse = 0;

        for (String nodeKey : nodesToExpand) {
            GraphNode node = fullGraph.findNodeByKey(nodeKey);
//            if (node == null) continue;

            Matcher matcher = patternMethodCall.matcher(node.getValue());
            if (!matcher.find()) continue;

            String className = matcher.group("objectType");
            String methodName = matcher.group("method");
            String argumentsType = matcher.group("argumentType").replace(",", ", ");
            String arguments = matcher.group("argument");
            List<String> argumentList = Arrays.asList(arguments.split(",\\s*"));

            String assignation = matcher.group("assignation");

            String getGraph = className + "." + methodName + "-(" + argumentsType + ")";
            fullGraph = addMethodGraphToGraph(fullGraph, convertedMethodGraph.get(getGraph), node, argumentList, assignation, parameterUse);
            parameterUse += argumentList.size();
        }
    }

    private List<String> getCallNode(ControlFlowGraph graph, int depth) {

        List<String> nodes = new ArrayList<>();

        if (depth == METHODS_CALL_DEPTH)
            return nodes;

        for (GraphNode node : graph.vertexSet()) {
            Matcher matcher = patternMethodCall.matcher(node.getValue()); // itkach.aard2.Util.isBlank-(java.lang.String)
            if (!matcher.find() || node.getValue().startsWith("lookupswitch")) continue;

            String className = matcher.group("objectType");
            String methodName = matcher.group("method");
            String argumentsType = matcher.group("argumentType").replace(",", ", ");
            // String arguments = matcher.group("argument");
            // String assignation = matcher.group("assignation");

            String getGraph = className + "." + methodName + "-(" + argumentsType + ")";
            if (otherMethods.containsKey(getGraph)) {
                nodes.add(node.getKey());
                ControlFlowGraph methodGraph = new ControlFlowGraph(otherMethods.get(getGraph));
                convertedMethodGraph.put(getGraph, methodGraph);
                nodes.addAll(getCallNode(methodGraph, depth + 1));
            }
        }

        return nodes;
    }


    private ControlFlowGraph addMethodGraphToGraph(ControlFlowGraph graph, ControlFlowGraph methodGraph, GraphNode node,
                                                   List<String> argumentList, String assignation, int parameterUse) {

        ControlFlowGraph newGraph = new ControlFlowGraph();

        List<Map.Entry<String, String>> methodParameter = new ArrayList<>();

        for (GraphNode vertex : graph.vertexSet()) {
            if (node.equalsKey(vertex)) {
                int parametersCount = 0;
                newGraph.addNode(vertex);
                for (GraphNode methodNode : methodGraph.vertexSet()) {
                    String line = methodNode.getValue();

                    if (line.contains(" := @parameter")) {
                        String parameterName = line.split(" := @parameter")[0];
                        line = String.format("$m%d = %s", parameterUse, argumentList.get(parametersCount));
                        parametersCount++;

                        methodParameter.add(Map.entry(parameterName, "$m" + parameterUse));
                        parameterUse++;
                    } else
                        for (Map.Entry<String, String> param : methodParameter) {
                            if (line.endsWith(param.getKey()))
                                line = line.replace(param.getKey(), param.getValue());
                            else
                                line = line.replace(param.getKey() + " ", param.getValue() + " ");
                        }

                    if (assignation != null && (line.startsWith("return ") || (line.startsWith("if") && line.contains("goto return"))))
                        line = line.replace("return ", assignation + " = ");

                    newGraph.addNode(new GraphNode(methodNode.getKey(), line));
                }
            } else
                newGraph.addNode(vertex);
        }

        for (DefaultEdge methodEdge : methodGraph.edgeSet())
            newGraph.addEdge(
                    newGraph.findNodeByEdgeSourceKey(methodEdge),
                    newGraph.findNodeByEdgeTargetKey(methodEdge)
            );

        List<GraphNode> roots = methodGraph.getRootsNodes();
        List<GraphNode> leafs = methodGraph.getLeafNodes();

        List<GraphNode> nodesSucc = new ArrayList<>();

        for (DefaultEdge edge : graph.edgeSet()) {
            GraphNode source = graph.getEdgeSource(edge);
            GraphNode target = graph.getEdgeTarget(edge);

            if (source.equalsKey(node)) { //  || target.getKey().equals(node.getKey())
                nodesSucc.add(target);
                continue;
            }
            newGraph.addEdge(edge);
        }

        for (GraphNode root : roots)
            newGraph.addEdge(node, newGraph.findNodeByKey(root.getKey()));

        for (GraphNode nodeSucc : nodesSucc)
            for (GraphNode leaf : leafs)
                newGraph.addEdge(newGraph.findNodeByKey(leaf.getKey()), nodeSucc);

        return newGraph;
    }

    /**
     * Starts the filtering process to extract nodes related to Intent operations.
     * This method iterates through the full control flow graph and identifies nodes
     * that involve `getExtra` calls or other relevant operations.
     *
     * @return
     */
    private Map<String, String> extractIntentRelatedNodes() {
        // Initialize a map to keep track of parameters that we need to monitor during the analysis.
        // The map's key represents the parameter's name, and the value represents its associated type.
//        Map<String, String> parametersToTrack = new HashMap<>();
        HashSet<String> parametersToTrack = new HashSet<>();

        // Map with the filtered Nodes
        Map<String, String> filteredNodes = new HashMap<>();

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
            for (GraphNode node : fullGraph.vertexSet()) {
                String nodeName = node.getKey();
                String line = node.getValue();

                // Match lines containing getExtra methods in Intent or Bundle objects
                Matcher extraMatcher = patternExtra.matcher(line);
                if (extraMatcher.find()) {
                    startAdding = true;
                    filteredNodes.put(nodeName, line);
                    parametersToTrack.add(extraMatcher.group("assignation"));
                }

                Matcher actionMatcher = patterGetAction.matcher(line);
                if (actionMatcher.find())
                    parametersToTrack.add(actionMatcher.group(1));

                // Continue adding nodes and edges after the first relevant extra is found
                if (!startAdding) continue;

                // Check if any saved parameters are used in the current unit
                if (parametersToTrack.stream().anyMatch(line::contains)) {
                    filteredNodes.put(nodeName, line);

                    // Start tracking the new parameter (that depends on a saved parameter)
                    String newParameterName = line.split(" = ")[0];
                    // Case: $r2 = staticinvoke <java.lang.String: java.lang.String valueOf(int)>(i0)
                    // It stores $r2 in parametersToTrack
                    if (newParameterName.split(" ").length == 1) {
                        parametersToTrack.add(newParameterName);
                        continue;
                    }

                    String[] newParametersName = line.split("\\.<")[0].split(" ");
                    // Case: specialinvoke $r9.<java.math.BigInteger: void <init>(java.lang.String)>($r2)
                    // It stores $r9 in parametersToTrack
                    if (newParametersName.length == 2) {
                        newParameterName = newParametersName[1];
                        parametersToTrack.add(newParameterName);
                        continue;
                    }

                    // If the line is a lookup switch, track the parameter used
                    if (line.startsWith("lookupswitch")) {
                        Pattern pattern = Pattern.compile("\\(([^)]+)\\)");
                        Matcher matcher = pattern.matcher(line);

                        if (matcher.find())
                            parametersToTrack.add(matcher.group(1));

                        // add all the targets of the switch
                        for (GraphNode succ : fullGraph.getSuccessorNodes(node)) {
                            if (!succ.getValue().startsWith("lookupswitch"))
                                filteredNodes.put(succ.getKey(), succ.getValue());
                        }
                        continue;
                    }
                }
            }
        }
        // Continue the loop until no new parameters are tracked, meaning we have processed all relevant parameters.
        while (startParametersCount < parametersToTrack.size());

        return filteredNodes;
    }

    private void gotoResolver() {
        for (GraphNode node : fullGraph.vertexSet()) {

            List<GraphNode> gotoNodes = new ArrayList<>();
            for (GraphNode pred : fullGraph.getPredecessorNodes(node)) {
                if (pred.getValue().contains("goto (branch)"))
                    gotoNodes.add(pred);
            }

            if (gotoNodes.size() > 1) {
                for (GraphNode gotoNode : gotoNodes) {
                    String replace = "";
                    for (GraphNode succ : fullGraph.getSuccessorNodes(gotoNode))
                        if (succ.getKey().equals(node.getKey()))
                            replace = succ.getValue();
                    fullGraph.replaceVertex(gotoNode.getKey(), gotoNode.getValue().replace("(branch)", replace));
                }
            }

        }
    }

    /**
     * Resets the content of the filtered control flow graph, clearing all nodes and edges.
     */
    public void resetGraphContent() {
        filteredCFG = new HashMap<>();
    }

    /**
     * Checks if the filtered control flow graph is empty.
     *
     * @return true if the filtered control flow graph contains no vertices,
     * otherwise false.
     */
    public boolean isEmpty() {
        return filteredCFG.isEmpty();
    }

    /**
     * Removes vertices representing "goto" statements and reconnects their predecessors to successors.
     */
    private void removeGoToVertex() {
        Set<GraphNode> nodesToRemove = new HashSet<>();

        // Identify vertices to remove.
        for (GraphNode vertex : fullGraph.vertexSet())
            if (vertex.getValue().startsWith("goto"))
                nodesToRemove.add(vertex);

        // Reconnect predecessors and successors for each node to be removed.
        for (GraphNode node : nodesToRemove)
            fullGraph.removeVertex(node);
    }

    private void stringSwitchSimplifier() {
        List<GraphNode> nodesToRemove = new ArrayList<>();

        for (GraphNode node : fullGraph.vertexSet()) {
            String line = node.getValue();
            if (line.startsWith("lookupswitch(") && fullGraph.getPredecessorNodes(node).size() == 1) {
                GraphNode firstPred = fullGraph.getPredecessorNodes(node).stream().findFirst().orElse(null);
                if (firstPred == null) continue;

                String hashCall = firstPred.getValue();
                if (hashCall.endsWith(".hashCode()")) {
                    String strParameter = hashCall.substring(hashCall.lastIndexOf(" ") + 1, hashCall.indexOf(".hashCode()"));
                    String intParameter = hashCall.substring(0, hashCall.indexOf(" "));
                    nodesToRemove.add(firstPred);

                    String caseString = line.substring(line.indexOf("{") + 1, line.indexOf("default:"));
                    String defaultString = line.substring(line.indexOf("default:") + ("default:").length(), line.lastIndexOf("; }"));

                    Matcher matcher = casePattern.matcher(caseString);

                    StringBuilder newLine = new StringBuilder(String.format("lookupswitch(%s) {", strParameter));
                    while (matcher.find()) {
                        if (matcher.group("equals") == null) continue;
                        newLine.append(String.format(" case %s: %s; ", matcher.group("equals"), matcher.group("goto")));
                    }
                    newLine.append(String.format("default: %s; ", defaultString.trim()));

                    newLine.append("}");
                    fullGraph.replaceVertex(node.getKey(), newLine.toString());
                }
            }
        }

        for (GraphNode node : nodesToRemove)
            fullGraph.removeVertex(node);
    }

    /**
     * Resolves switch statements in the control flow graph by converting them into
     * a series of conditional branches. This method simplifies the graph by replacing
     * switch nodes with a sequence of if-else conditions.
     */
    public void switchResolver() {
        FilteredControlFlowGraph switchCFG = new FilteredControlFlowGraph(this, fullGraph, filteredCFG); // todo, if we select the filtered node from using the paths,

        for (GraphNode node : fullGraph.vertexSet()) {
            String line = node.getValue();

            if (line.startsWith("lookupswitch(")) {

                boolean filtered = filteredCFG.containsKey(node.getKey());

                String variableName = line.substring(line.indexOf("(") + 1, line.indexOf(")"));

                String caseString = line.substring(line.indexOf("{") + 1, line.indexOf("default:"));

                String defaultString = null;
                if (line.contains("default:") && line.contains("; }") && (line.indexOf("default:") < line.lastIndexOf("; }")))
                    defaultString = line.substring(line.indexOf("default: ") + ("default: ").length(), line.lastIndexOf("; }"));

                Pattern pattern = Pattern.compile("case (.*?): (.*?);");
                Matcher matcher = pattern.matcher(caseString);

                List<Map.Entry<String, String>> extractedCases = new ArrayList<>();
                while (matcher.find())
                    extractedCases.add(Map.entry(matcher.group(1), matcher.group(2).trim()));

                if (defaultString != null)
                    extractedCases.add(Map.entry(defaultString, defaultString));

                Set<GraphNode> succsList = fullGraph.getSuccessorNodes(node);
                List<GraphNode> succEntryList = new ArrayList<>();
                GraphNode defaultNode = null;

                for (Map.Entry<String, String> caseEntry : extractedCases) {
                    GraphNode caseNode = null;
                    String caseText = caseEntry.getValue().replaceFirst("goto ", "").trim();

                    for (GraphNode succ : succsList) {
                        if (succ.getValue().contains(caseText)) {
                            caseNode = succ;
                            break;
                        }
                    }

                    if (caseNode == null) continue;

                    if (defaultString != null && caseNode.getValue().equals(defaultString.replaceFirst("goto ", "").trim())) {
                        defaultNode = caseNode;
                        continue;
                    }

                    String nodeText = "if " + variableName + "==" + caseEntry.getKey() + " " + caseEntry.getValue();
                    if (succEntryList.isEmpty()) { // first switch element
                        GraphNode newNode = switchCFG.fullGraph.replaceVertex(node.getKey(), nodeText); // use the node name of the switch
                        succEntryList.add(newNode);

                        // remove edges
                        Set<DefaultEdge> edgesToRemove = new HashSet<>(switchCFG.fullGraph.getSuccessorEdges(switchCFG.fullGraph.findNodeByKey(node.getKey())));
                        for (DefaultEdge defaultEdge : edgesToRemove)
                            if (!switchCFG.fullGraph.getEdgeTarget(defaultEdge).getValue().equals(caseText))
                                switchCFG.fullGraph.removeEdge(defaultEdge);

                    } else {
                        GraphNode vertex = new GraphNode("node" + Math.abs(nodeText.hashCode()), nodeText);
                        succEntryList.add(vertex);

                        switchCFG.fullGraph.addNode(vertex);
                        switchCFG.fullGraph.addEdge(succEntryList.get(succEntryList.size() - 2), vertex);

                        if (filtered)
                            switchCFG.filteredCFG.put(vertex.getKey(), vertex.getValue());
                    }
                    switchCFG.fullGraph.addNode(caseNode);
                    switchCFG.fullGraph.addEdge(succEntryList.get(succEntryList.size() - 1), caseNode); //  no such vertex in graph: {'node1244389716' -> 'if $r2=="*" goto $z0 = $r2 == "*"'}

                    if (filtered)
                        switchCFG.filteredCFG.put(caseNode.getKey(), caseNode.getValue());
                }

                if (defaultNode == null) continue;

                switchCFG.fullGraph.addNode(defaultNode);
                switchCFG.fullGraph.addEdge(succEntryList.get(succEntryList.size() - 1), defaultNode);
                if (filtered)
                    switchCFG.filteredCFG.put(defaultNode.getKey(), defaultNode.getValue());
            }
        }

        this.fullGraph = switchCFG.fullGraph;
        this.filteredCFG = switchCFG.filteredCFG;
    }

    /**
     * Simplifies the control flow graph by relabeling nodes and removing unnecessary edges.
     * This method is used to make the graph more readable and easier to analyze.
     *
     * @return A simplified version of the control flow graph.
     */
    private void graphSimplifier() {
        List<Map.Entry<String, String>> nodesRelabeled = new ArrayList<>();

        for (GraphNode node : fullGraph.vertexSet()) {
            String nodeLabel = node.getValue();
            String newNodeLabel;

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

        // fix also for goto
        for (GraphNode node : fullGraph.vertexSet()) {
            String nodeLabel = node.getValue();

            for (Map.Entry<String, String> nodeRelabeled : nodesRelabeled)
                nodeLabel = nodeLabel.replace(nodeRelabeled.getKey(), nodeRelabeled.getValue());

            fullGraph.replaceVertex(node.getKey(), nodeLabel);
        }
    }

    /**
     * Generates a DOT representation of the filtered control flow graph.
     *
     * @return A string representing the graph in DOT format.
     */
    public String toString() {
        // Remove "goto" vertices before generating the DOT representation.
        // removeGoToVertex();

        StringBuilder dotGraph = new StringBuilder();
        dotGraph.append(String.format("digraph %s {\n", completeMethod.replace(".", "_")));

        // Add nodes and their labels.
        for (GraphNode v : fullGraph.vertexSet()) {
            String nodeName = v.getKey();
            String color = "";
            if (filteredCFG.containsKey(v.getKey()))
                color = ", color=blue";
            dotGraph.append(String.format("%s [label=\"%s\"%s];\n", nodeName, v.getValue().replace("\"", "\\\""), color));
        }

        // Add edges between nodes.
        for (DefaultEdge e : fullGraph.edgeSet()) {
            String source = fullGraph.getEdgeSource(e).getKey();
            String target = fullGraph.getEdgeTarget(e).getKey();
            dotGraph.append(String.format("%s -> %s;\n", source, target));
        }

        dotGraph.append("}\n");

        return dotGraph.toString();
    }
}