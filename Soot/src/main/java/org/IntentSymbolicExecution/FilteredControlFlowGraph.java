package org.IntentSymbolicExecution;

import org.IntentSymbolicExecution.ControlFlowGraph.GraphNode;
import org.IntentSymbolicExecution.IntentAnalysis.GlobalVariablesInfo;
import org.jgrapht.graph.DefaultEdge;
import soot.toolkits.graph.ExceptionalUnitGraph;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.IntentSymbolicExecution.RegexUtils.*;

/**
 * This class constructs a filtered control flow graph based on the full control flow graph
 * provided by an {@link ExceptionalUnitGraph}. It filters the nodes from the original graph
 * to focus on Intent-related operations and generates a simplified graph for further analysis.
 */
public class FilteredControlFlowGraph {

    /**
     * Maximum recursion depth when expanding method calls.
     */
    private static final int METHODS_CALL_DEPTH = 1;

    /**
     * The filtered control flow graph.
     * <p>
     * Each entry represents a node where the key is the node identifier and the value is the corresponding code snippet.
     */
    private Map<String, String> filteredCFG;

    /**
     * The complete (full) control flow graph.
     */
    private ControlFlowGraph fullGraph;

    /**
     * The name of the class and method being analyzed.
     */
    private final String completeMethod;

    private final String attributes;

    /**
     * A cache of converted method graphs.
     * <p>
     * The key is a string representation of the method signature, and the value is its corresponding control flow graph.
     */
    private final Map<String, ControlFlowGraph> convertedMethodGraph = new HashMap<>();

    /**
     * A map of other methods and their corresponding {@link ExceptionalUnitGraph}.
     * <p>
     * This is used to expand method calls during the filtering process.
     */
    private final Map<String, ExceptionalUnitGraph> otherMethods;

    /**
     * Constructs a filtered control flow graph by processing the given {@link ExceptionalUnitGraph}.
     *
     * @param fullGraph       The complete control flow graph for the method (Soot {@link ExceptionalUnitGraph}).
     * @param completeMethod  The name of the class and method being analyzed.
     * @param otherMethods    A map of other methods and their corresponding control flow graphs for expanding method calls.
     * @param globalVariables A map of global variables used to replace corresponding occurrences in the graph.
     */
    public FilteredControlFlowGraph(ExceptionalUnitGraph fullGraph, String completeMethod, String attributes,
                                    Map<String, ExceptionalUnitGraph> otherMethods, Map<String, GlobalVariablesInfo> globalVariables) {
        this.completeMethod = completeMethod;
        this.otherMethods = otherMethods;
        this.attributes = attributes;
        this.filteredCFG = new HashMap<>();
        // Build the full graph from the ExceptionalUnitGraph.
        this.fullGraph = ControlFlowGraph_GotoFixer(fullGraph);

        // Resolve method calls by expanding called methods into the graph.
        methodCallResolver();

        // Replace occurrences of global variables.
        replaceGlobalVariables(globalVariables);

        // Simplify the graph structure.
        lableRenameGraphNodes();

        // Extract nodes that are relevant to Intent-related operations.
        filteredCFG = extractIntentRelatedNodes();

        // Simplify string switch constructs and resolve switch statements.
        stringSwitchSimplifier();
        switchResolver();
    }

    /**
     * @return
     */
    private ControlFlowGraph ControlFlowGraph_GotoFixer(ExceptionalUnitGraph originalGraph) {

        ControlFlowGraph graph = new ControlFlowGraph(originalGraph);

        String className = originalGraph.getBody().getMethod().getDeclaringClass().getName();
        String methodSign = originalGraph.getBody().getMethod().getSubSignature();
        methodSign = methodSign.substring(methodSign.lastIndexOf(" ") + 1).replace(",", ", ");

        List<String> jimpleCode = getJimpleCode("sootOutput/" + className + ".jimple", methodSign);
        List<String> ifjimpleCode = jimpleCode.stream().filter(s -> s.startsWith("if")).collect(Collectors.toList());
        List<Map.Entry<String, String>> resolvedLabel = findAndResolveLabel(jimpleCode);

        // Remove and resolve goto vertices.
        gotoResolver(graph, ifjimpleCode, resolvedLabel);
        return graph;
    }

    /**
     * Copy constructor for creating a new filtered control flow graph based on an existing one.
     *
     * @param filteredControlFlowGraph The existing filtered control flow graph.
     * @param fullGraph                The full control flow graph.
     * @param filteredCFG              The map of filtered nodes.
     */
    public FilteredControlFlowGraph(FilteredControlFlowGraph filteredControlFlowGraph, ControlFlowGraph fullGraph, Map<String, String> filteredCFG) {
        this.fullGraph = new ControlFlowGraph(fullGraph);
        this.completeMethod = filteredControlFlowGraph.completeMethod;
        this.otherMethods = filteredControlFlowGraph.otherMethods;
        this.attributes = filteredControlFlowGraph.attributes;
        this.filteredCFG = new HashMap<>(filteredCFG);
    }

    public static List<String> getJimpleCode(String filePath, String methodName) {
        try {
            List<String> lines = Files.readAllLines(Paths.get(filePath));
            List<String> methodContent = new ArrayList<>();
            boolean insideMethod = false;
            int braceCount = 0;

            for (String line : lines) {
                line = line.trim();

                // Check for method signature
                if (line.endsWith(methodName) || line.contains(methodName + " throws ")) {
                    insideMethod = true;
                }

                if (insideMethod) {
                    if (line.isEmpty()) continue;

                    methodContent.add(line);

                    // Count braces to detect method boundaries
                    for (char c : line.toCharArray()) {
                        if (c == '{') braceCount++;
                        if (c == '}') braceCount--;
                    }

                    // If braces are balanced and we reach '}', exit
                    if (methodContent.size() > 1 && braceCount == 0) break;
                }
            }

            return methodContent;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * TODO itkach_aard2_ArticleCollectionActivity_createFromIntent [1]
     *
     * @param jimpleCode
     * @return
     */
    private List<Map.Entry<String, String>> findAndResolveLabel(List<String> jimpleCode) {
        List<Map.Entry<String, String>> label = new ArrayList<>();
        List<Map.Entry<String, String>> resolvedLabel = new ArrayList<>();

        Pattern labelPattern = Pattern.compile("^label(\\d+):$");

        for (int i = 1; i < jimpleCode.size(); i++) {
            String line = jimpleCode.get(i);
            if (labelPattern.matcher(line).matches())
                label.add(Map.entry(line, jimpleCode.get(i + 1)));
        }

        for (int i = label.size() - 1; i >= 0; i--) {

            Map.Entry<String, String> entry = label.get(i);
            String labelName = entry.getKey().substring(0, entry.getKey().length() - 1);
            String labelValue = entry.getValue().substring(0, entry.getValue().length() - 1);

            for (Map.Entry<String, String> labelEntry : resolvedLabel) {
                String prevLabelName = labelEntry.getKey();
                String prevLabelValue = labelEntry.getValue();
                labelValue = labelValue.replace(prevLabelName, prevLabelValue);
            }
            resolvedLabel.add(Map.entry(labelName, labelValue));
        }

        // Collections.reverse(resolvedLabel);

        return resolvedLabel;
    }


    /**
     * Returns the full control flow graph.
     *
     * @return The {@link ControlFlowGraph} representing the full graph.
     */
    public ControlFlowGraph getFullCFG() {
        return this.fullGraph;
    }

    /**
     * Returns the filtered control flow graph.
     *
     * @return A map with node identifiers as keys and code snippets as values.
     */
    public Map<String, String> getFilteredCFG() {
        return filteredCFG;
    }

    /**
     * Returns the complete method name (class and method) being analyzed.
     *
     * @return The complete method name.
     */
    public String getCompleteMethod() {
        return this.completeMethod;
    }

    /**
     * Checks if the filtered control flow graph contains any Intent-related nodes.
     *
     * @return true if there are any filtered nodes; false otherwise.
     */
    public boolean haveExtras() {
        return !this.filteredCFG.isEmpty();
    }

    /**
     * Replaces occurrences of global variables in the full control flow graph.
     * <p>
     * For each node, it uses a regular expression to detect global variable patterns and,
     * if found, replaces the node's value based on the corresponding global variable info.
     *
     * @param globalVariables A map of global variable names to their information.
     */
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

    /**
     * Resolves method calls in the full control flow graph by expanding them.
     * <p>
     * This method searches for nodes that match the method call pattern, extracts method details,
     * and then integrates the called method's graph into the full graph.
     */
    private void methodCallResolver() {
        List<String> nodesToExpand = getCallNode(fullGraph, 0);
        int parameterUse = 0;

        for (String nodeKey : nodesToExpand) {
            GraphNode node = fullGraph.findNodeByKey(nodeKey);
            // If the node does not match a method call pattern, skip it.
            Matcher matcher = patternMethodCall.matcher(node.getValue());
            if (!matcher.find()) continue;

            String className = matcher.group("objectType");
            String methodName = matcher.group("method");
            String argumentsType = matcher.group("argumentType").replace(",", ", ");
            String arguments = matcher.group("argument");
            List<String> argumentList = Arrays.asList(arguments.split(",\\s*"));
            String assignation = matcher.group("assignation");
            String returnedType = matcher.group("returnedType");

            String getGraph = className + "." + methodName + "-(" + argumentsType + ")";
            fullGraph = addMethodGraphToGraph(fullGraph, convertedMethodGraph.get(getGraph), node, argumentList, assignation, returnedType, parameterUse);
            parameterUse += argumentList.size();
        }
    }

    /**
     * Recursively retrieves node keys that contain method calls in the given control flow graph.
     *
     * @param graph The control flow graph to search.
     * @param depth The current depth of method call expansion.
     * @return A list of node keys where method calls occur.
     */
    private List<String> getCallNode(ControlFlowGraph graph, int depth) {
        List<String> nodes = new ArrayList<>();
        if (depth == METHODS_CALL_DEPTH)
            return nodes;

        for (GraphNode node : graph.vertexSet()) {
            Matcher matcher = patternMethodCall.matcher(node.getValue());
            // Skip nodes that do not match or start with a lookupswitch.
            if (!matcher.find() || node.getValue().startsWith("lookupswitch"))
                continue;

            String className = matcher.group("objectType");
            String methodName = matcher.group("method");
            String argumentsType = "";
            if (matcher.group("argumentType") != null)
                argumentsType = matcher.group("argumentType").replace(",", ", ");
            // String arguments = matcher.group("argument");
            // String assignation = matcher.group("assignation");

            String getGraph = className + "." + methodName + "-(" + argumentsType + ")";
            if (otherMethods.containsKey(getGraph)) {
                nodes.add(node.getKey());
                ControlFlowGraph methodGraph = ControlFlowGraph_GotoFixer(otherMethods.get(getGraph));
                convertedMethodGraph.put(getGraph, methodGraph);
                nodes.addAll(getCallNode(methodGraph, depth + 1));
            }
        }
        return nodes;
    }


    /**
     * Add a number in the nodeName to uniquifier in, in the method expansion, avoiding problems in graph overlapping
     */
    private int expandMethodUniquifier = 1;

    /**
     * Integrates a method's control flow graph into the full graph at the given call site.
     * <p>
     * This method replaces the node representing the method call with the corresponding method graph,
     * performing parameter substitution as needed.
     *
     * @param graph        The full control flow graph.
     * @param methodGraph  The control flow graph of the called method.
     * @param node         The node in the full graph representing the method call.
     * @param argumentList A list of arguments passed to the method.
     * @param assignation  The assignation string from the method call (if any).
     * @param returnedType TODO
     * @param parameterUse An index used for parameter naming.
     * @return A new full control flow graph with the method graph integrated.
     */
    private ControlFlowGraph addMethodGraphToGraph(ControlFlowGraph graph, ControlFlowGraph methodGraph, GraphNode node,
                                                   List<String> argumentList, String assignation, String returnedType, int parameterUse) {
        ControlFlowGraph newGraph = new ControlFlowGraph();

        List<Map.Entry<String, String>> methodParameter = new ArrayList<>();
        List<String> methodNodesKey = new ArrayList<>();

        // Iterate over all vertices in the full graph.
        for (GraphNode vertex : graph.vertexSet()) {
            newGraph.addNode(vertex);
            if (node.equalsKey(vertex)) {
                int parametersCount = 0;
                // Process each node in the called method's graph.
                for (GraphNode methodNode : methodGraph.vertexSet()) {
                    String line = methodNode.getValue();
                    if (line.contains(" := @parameter")) {
                        String parameterName = line.split(" := @parameter")[0];
                        // Substitute the parameter with the corresponding argument.
                        line = String.format("$m%d = %s", parameterUse, argumentList.get(parametersCount));
                        parametersCount++;

                        methodParameter.add(Map.entry(parameterName, "$m" + parameterUse));
                        parameterUse++;
                    } else {
                        // Replace parameter names in the line if they were tracked.
                        for (Map.Entry<String, String> param : methodParameter) {
                            String replaceRegex = String.format(variableRenamingRegex, param.getKey().replace("$", "\\$"));
                            String replacementName = param.getValue().replace("$", "\\$");
                            line = line.replaceAll(replaceRegex, replacementName);
                        }
                    }

                    // Replace return statements with assignation if applicable.
                    if (assignation != null && (line.startsWith("return ") || (line.startsWith("if") && line.contains("goto return"))))
                        line = line.replace("return ", String.format("%s (%s) = (return.%s) ", assignation, returnedType, assignation));
//                        line = line.replace("return ", assignation + " (return.) = ");

                    newGraph.addNode(new GraphNode(methodNode.getKey(), line));
                    methodNodesKey.add(methodNode.getKey());
                }
            }
        }

        // Add edges from the called method's graph into the new graph.
        for (DefaultEdge methodEdge : methodGraph.edgeSet())
            newGraph.addEdge(
                    newGraph.findNodeByEdgeSourceKey(methodEdge),
                    newGraph.findNodeByEdgeTargetKey(methodEdge)
            );

        List<GraphNode> roots = methodGraph.getRootsNodes();
        List<GraphNode> leafs = methodGraph.getLeafNodes();

        List<GraphNode> nodesSucc = new ArrayList<>();

        // Reconnect edges from the original graph that relate to the method call node.
        for (DefaultEdge edge : graph.edgeSet()) {
            GraphNode source = graph.getEdgeSource(edge);
            GraphNode target = graph.getEdgeTarget(edge);

            if (source.equalsKey(node)) {
                nodesSucc.add(target);
                continue;
            }
            newGraph.addEdge(edge);
        }

        // Connect the method graph's roots and leafs to the appropriate nodes.
        for (GraphNode root : roots)
            newGraph.addEdge(node, newGraph.findNodeByKey(root.getKey()));

        for (GraphNode nodeSucc : nodesSucc)
            for (GraphNode leaf : leafs)
                newGraph.addEdge(newGraph.findNodeByKey(leaf.getKey()), nodeSucc);

        for (GraphNode vertex : newGraph.vertexSet()) {
            if (methodNodesKey.contains(vertex.getKey()))
                vertex.setKey(vertex.getKey() + expandMethodUniquifier);
        }

        expandMethodUniquifier++;
        return newGraph;
    }

    /**
     * Extracts nodes related to Intent operations from the full control flow graph.
     * <p>
     * The method tracks parameters and adds nodes to the filtered CFG if they match Intent extra or action patterns.
     *
     * @return A map of filtered nodes where the key is the node identifier and the value is the code snippet.
     */
    private Map<String, String> extractIntentRelatedNodes() {
        // Use a HashSet to track parameter names referenced in the graph.
        HashSet<String> parametersToTrack = new HashSet<>();
        // Map to store nodes that pass the filtering criteria.
        Map<String, String> filteredNodes = new HashMap<>();
        // Keep track of the number of tracked parameters to determine when filtering is complete.
        int startParametersCount;
        // Flag to indicate when to start adding nodes based on encountering the first relevant Intent/Bundle extra.
        boolean startAdding;

        do {
            startParametersCount = parametersToTrack.size();
            resetGraphContent();
            startAdding = false;

            // Iterate over each node in the full control flow graph.
            for (GraphNode node : fullGraph.vertexSet()) {
                String nodeName = node.getKey();
                String line = node.getValue();

                // Check for Intent/Bundle getExtra operations.
                Matcher extraMatcher = patternExtra.matcher(line);
                if (extraMatcher.find()) {
                    startAdding = true;
                    filteredNodes.put(nodeName, line);
                    parametersToTrack.add(extraMatcher.group("assignation"));
                }

                // Check for getAction operations.
                Matcher actionMatcher = patterGetAction.matcher(line);
                if (actionMatcher.find()) {
                    startAdding = true;
                    filteredNodes.put(nodeName, line);
                    parametersToTrack.add(actionMatcher.group(1));
                }

                // If we have not started adding nodes, continue to next node.
                if (!startAdding) continue;

                // If the current line contains any tracked parameters, add it to the filtered nodes.
                if (parametersToTrack.stream().anyMatch(line::contains)) {
                    filteredNodes.put(nodeName, line);

                    // Track a new parameter that depends on a saved parameter.
                    String newParameterName = line.split(" = ")[0];
                    // Case: $r2 = staticinvoke ... (stores $r2)
                    if (newParameterName.split(" ").length == 1) {
                        parametersToTrack.add(newParameterName);
                        continue;
                    }

                    String[] newParametersName = line.split("\\.<")[0].split(" ");
                    // Case: specialinvoke ... (stores $r9)
                    if (newParametersName.length == 2) {
                        newParameterName = newParametersName[1];
                        parametersToTrack.add(newParameterName);
                        continue;
                    }

                    // For lookup switches, extract the parameter and add all targets.
                    if (line.startsWith("lookupswitch")) {
                        Pattern pattern = Pattern.compile("\\(([^)]+)\\)");
                        Matcher matcher = pattern.matcher(line);
                        if (matcher.find())
                            parametersToTrack.add(matcher.group(1));

                        // Add all target nodes of the switch.
                        for (GraphNode succ : fullGraph.getSuccessorNodes(node)) {
                            if (!succ.getValue().startsWith("lookupswitch"))
                                filteredNodes.put(succ.getKey(), succ.getValue());
                        }
                        continue;
                    }
                }
            }
        } while (startParametersCount < parametersToTrack.size());

        return filteredNodes;
    }

    /**
     * Resolves "goto" statements in the graph by updating the corresponding node values.
     * <p>
     * This method searches for nodes with "goto (branch)" in their values, finds the target of the branch,
     * and replaces the branch placeholder with the actual target value.
     */
    private void gotoResolver(ControlFlowGraph graph, List<String> ifjimpleCode, List<Map.Entry<String, String>> resolvedLabel) {

        Set<GraphNode> nodesToRemove = new HashSet<>();
        // Identify nodes that start with "goto".
        for (GraphNode vertex : graph.vertexSet())
            if (vertex.getValue().startsWith("goto"))
                nodesToRemove.add(vertex);

        // Remove each identified node from the graph.
        for (GraphNode node : nodesToRemove)
            graph.removeVertex(node);

        int ifCounter = 0;
        for (GraphNode node : graph.vertexSet()) {
            List<GraphNode> gotoNodes = new ArrayList<>();
            // Identify predecessor nodes containing "goto (branch)".
            for (GraphNode pred : graph.getPredecessorNodes(node)) {
                if (pred.getValue().contains("goto (branch)"))
                    gotoNodes.add(pred);
            }

            // If multiple such nodes exist, update each one.
            if (gotoNodes.size() > 1) {
                for (GraphNode gotoNode : gotoNodes) {
                    String replace = "";
                    // Find the corresponding successor that matches the current node.
                    for (GraphNode succ : graph.getSuccessorNodes(gotoNode))
                        if (succ.getKey().equals(node.getKey()))
                            replace = succ.getValue();
                    graph.replaceVertex(gotoNode.getKey(), gotoNode.getValue().replace("(branch)", replace));
                }
            }


            if (node.getValue().startsWith("if")) {
                if (gotoNodes.isEmpty()) {
                    String ifLine = ifjimpleCode.get(ifCounter);
                    for (Map.Entry<String, String> label : resolvedLabel)
                        ifLine = ifLine.replace(label.getKey(), label.getValue());
                    graph.replaceVertex(node.getKey(), ifLine.replaceAll(";+$", ""));
                }
                ifCounter++;
            }

        }
    }

    /**
     * Resets the filtered control flow graph content by clearing all filtered nodes.
     */
    public void resetGraphContent() {
        filteredCFG = new HashMap<>();
    }

    /**
     * Checks if the filtered control flow graph is empty.
     *
     * @return true if the filtered graph has no nodes; false otherwise.
     */
    public boolean isEmpty() {
        return filteredCFG.isEmpty();
    }

    /**
     * Simplifies string switch constructs by modifying their representation.
     * <p>
     * This method processes nodes starting with "lookupswitch(" and, if a preceding hashCode() call is detected,
     * constructs a new string representation for the switch statement. It also removes associated nodes.
     */
    private void stringSwitchSimplifier() {
        List<GraphNode> nodesToRemove = new ArrayList<>();

        for (GraphNode node : fullGraph.vertexSet()) {
            String line = node.getValue();
            if (line.startsWith("lookupswitch(") && fullGraph.getPredecessorNodes(node).size() == 1) {
                GraphNode firstPred = fullGraph.getPredecessorNodes(node).stream().findFirst().orElse(null);
                if (firstPred == null) continue;

                String hashCall = firstPred.getValue();
                if (hashCall.endsWith(".hashCode()")) {
                    // Extract parameters from the hash call.
                    String strParameter = hashCall.substring(hashCall.lastIndexOf(" ") + 1, hashCall.indexOf(".hashCode()"));
                    String intParameter = hashCall.substring(0, hashCall.indexOf(" "));
                    nodesToRemove.add(firstPred);

                    // Parse the case string and default branch.
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

        // Remove the nodes that were marked for removal.
        for (GraphNode node : nodesToRemove)
            fullGraph.removeVertex(node);
    }

    /**
     * Resolves switch statements in the control flow graph by converting them into a series of conditional branches.
     * <p>
     * This method processes nodes starting with "lookupswitch(", extracts case values and their targets,
     * and rebuilds the graph using if-else style branches.
     */
    public void switchResolver() {
        // Create a temporary filtered graph for processing switch statements.
        FilteredControlFlowGraph switchCFG = new FilteredControlFlowGraph(this, fullGraph, filteredCFG);

        for (GraphNode node : fullGraph.vertexSet()) {
            String line = node.getValue();

            if (line.startsWith("lookupswitch(")) {
                boolean filtered = filteredCFG.containsKey(node.getKey());
                // Extract the variable used in the switch statement.
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

                    if (caseNode == null)
                        continue;

                    if (defaultString != null && caseNode.getValue().equals(defaultString.replaceFirst("goto ", "").trim())) {
                        defaultNode = caseNode;
                        continue;
                    }

                    // Build the if-condition string for the current case.
                    String nodeText = "if " + variableName + "==" + caseEntry.getKey() + " " + caseEntry.getValue();
                    if (succEntryList.isEmpty()) { // For the first switch element.
                        GraphNode newNode = switchCFG.fullGraph.replaceVertex(node.getKey(), nodeText);
                        succEntryList.add(newNode);

                        // Remove edges that do not correspond to the current case.
                        Set<DefaultEdge> edgesToRemove = new HashSet<>(switchCFG.fullGraph.getSuccessorEdges(switchCFG.fullGraph.findNodeByKey(node.getKey())));
                        for (DefaultEdge defaultEdge : edgesToRemove)
                            if (!switchCFG.fullGraph.getEdgeTarget(defaultEdge).getValue().equals(caseText))
                                switchCFG.fullGraph.removeEdge(defaultEdge);
                    } else {
                        // For subsequent switch elements, create a new node.
                        GraphNode vertex = new GraphNode("node" + Math.abs(nodeText.hashCode()), nodeText);
                        succEntryList.add(vertex);
                        switchCFG.fullGraph.addNode(vertex);
                        switchCFG.fullGraph.addEdge(succEntryList.get(succEntryList.size() - 2), vertex);
                        if (filtered)
                            switchCFG.filteredCFG.put(vertex.getKey(), vertex.getValue());
                    }
                    switchCFG.fullGraph.addNode(caseNode);
                    switchCFG.fullGraph.addEdge(succEntryList.get(succEntryList.size() - 1), caseNode);
                    if (filtered)
                        switchCFG.filteredCFG.put(caseNode.getKey(), caseNode.getValue());
                }

                if (defaultNode == null)
                    continue;

                switchCFG.fullGraph.addNode(defaultNode);
                switchCFG.fullGraph.addEdge(succEntryList.get(succEntryList.size() - 1), defaultNode);
                if (filtered)
                    switchCFG.filteredCFG.put(defaultNode.getKey(), defaultNode.getValue());
            }
        }
        // Update the full graph and filtered CFG based on the processed switch.
        this.fullGraph = switchCFG.fullGraph;
        this.filteredCFG = switchCFG.filteredCFG;
    }

    /**
     * Simplifies the control flow graph by relabeling nodes and removing unnecessary edges.
     * <p>
     * This method processes nodes to transform method call representations into a simpler, more readable form,
     * and then updates all node labels accordingly.
     */
    private void lableRenameGraphNodes() {
        List<Map.Entry<String, String>> nodesRelabeled = new ArrayList<>();

        // Iterate over each node to build a mapping of original labels to simplified labels.
        for (GraphNode node : fullGraph.vertexSet()) {
            String nodeLabel = node.getValue();
            String newNodeLabel;

            Matcher matcher = RegexUtils.patternMethodCall.matcher(nodeLabel);
            Matcher matcher2 = RegexUtils.thisCallPattern.matcher(nodeLabel);
            Matcher matcher3 = RegexUtils.variableCallPattern.matcher(nodeLabel);
            Matcher mathcer4 = RegexUtils.voidMethodCallPattern.matcher(nodeLabel);
            if (matcher2.find()) {
                String thisObject = matcher2.group(1);
                //String packageName = matcher2.group(2);
                String type = matcher2.group(3);
                String varName = matcher2.group(4);
                String newObject = matcher2.group(5);
                if (!thisObject.contains("if ")) {
                    newNodeLabel = String.format("%s_this_%s (%s) = %s", thisObject, varName, type, newObject);
                    nodesRelabeled.add(Map.entry(nodeLabel, newNodeLabel));
                }
            } else if (matcher3.find()) {
                String assignation = matcher3.group(1);
                String object = matcher3.group(2);
                String objectType = matcher3.group(3);
                String returnedType = matcher3.group(4);
                String varName = matcher3.group(5);
                if (object.equals("r0"))
                    newNodeLabel = String.format("%s (%s) = %s_this_%s", assignation, returnedType, object, varName);
                else
                    newNodeLabel = String.format("%s (%s) = (%s) %s.%s", assignation, returnedType, objectType, object, varName);
                nodesRelabeled.add(Map.entry(nodeLabel, newNodeLabel));
            } else if (mathcer4.find()) {
                String object = mathcer4.group(2);
                String objectType = mathcer4.group(3);
                String method = mathcer4.group(4);
                //String paramsType = mathcer4.group(5);
                String params = mathcer4.group(6);
                newNodeLabel = String.format("(%s) %s.%s(%s)", objectType, object, method, params);
                nodesRelabeled.add(Map.entry(nodeLabel, newNodeLabel));
            } else if (matcher.find()) {
                String assignation = matcher.group("assignation");
                String invoke = matcher.group("invoke");
                String object = matcher.group("object");
                String objectType = matcher.group("objectType");
                String returnedType = matcher.group("returnedType");
                String method = matcher.group("method");
                String argument = matcher.group("argument");
                if (method.equals("equals") || method.equals("areEqual")) {
                    newNodeLabel = String.format("%s = %s == %s", assignation, object, argument);
                    if (object == null) {
                        String arg1 = argument.split(", ")[0];
                        String arg2 = argument.split(", ")[1];
                        newNodeLabel = String.format("%s = %s == %s", assignation, arg1, arg2);
                    }
                } else if (invoke == null)
                    newNodeLabel = String.format("%s (%s) = %s.%s", assignation, returnedType, object, method);
                else if ((invoke.equals("virtualinvoke") || invoke.equals("specialinvoke") || invoke.equals("interfaceinvoke")) && object != null && assignation != null)
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

        // Update each node in the graph with the new simplified labels.
        for (GraphNode node : fullGraph.vertexSet()) {
            String nodeLabel = node.getValue();
            for (Map.Entry<String, String> nodeRelabeled : nodesRelabeled)
                nodeLabel = nodeLabel.replace(nodeRelabeled.getKey(), nodeRelabeled.getValue());
            fullGraph.replaceVertex(node.getKey(), nodeLabel);
        }
    }

    /**
     * Generates a DOT representation of the filtered control flow graph.
     * <p>
     * The DOT format is used by Graphviz for visualization.
     *
     * @return A string representing the graph in DOT format.
     */
    public String toString() {
        StringBuilder dotGraph = new StringBuilder();
        dotGraph.append(String.format("digraph %s {\n", completeMethod.replace(".", "_")));

        // Add nodes with their labels, highlighting filtered nodes in blue.
        for (GraphNode v : fullGraph.vertexSet()) {
            String nodeName = v.getKey();
            String color = "";
            if (filteredCFG.containsKey(v.getKey()))
                color = ", color=blue";
            String label = v.getValue().replace("\\", "\\\\").replace("\"", "\\\"");
            dotGraph.append(String.format("%s [label=\"%s\"%s];\n", nodeName, label, color));
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
