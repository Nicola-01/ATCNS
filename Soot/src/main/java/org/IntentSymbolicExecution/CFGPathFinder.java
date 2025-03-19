package org.IntentSymbolicExecution;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;

import static org.IntentSymbolicExecution.RegexUtils.*;

import org.IntentSymbolicExecution.ControlFlowGraph.GraphNode;

public class CFGPathFinder {

    private final FilteredControlFlowGraph filteredControlFlowGraph;
    private final ControlFlowGraph filteredCFG;

    public CFGPathFinder(FilteredControlFlowGraph graph) {
        this.filteredControlFlowGraph = graph;
        this.filteredCFG = graph.getFullCFG();
    }


    /*public List<List<GraphNode>> getAllPathsIterative() {
        List<List<GraphNode>> allPaths = new ArrayList<>();

        ControlFlowGraph graph = filteredControlFlowGraph.getFullCFG();

        GraphNode firstNode = graph.getRootsNodes().get(0);
        allPaths.add(new ArrayList<>(List.of(firstNode)));

        Stack<GraphNode> stack = new Stack<>();
        stack.push(firstNode);

        while (!stack.isEmpty()) {
            GraphNode node = stack.pop();
            Set<GraphNode> preds = graph.getPredecessorNodes(node);
            List<GraphNode> succs = new ArrayList<>(graph.getSuccessorNodes(node));
            if (succs.isEmpty()) continue;

            int allPathsSize = allPaths.size();
            for (int i = 0; i < allPathsSize; i++) {

                List<GraphNode> path = allPaths.get(i);

                if (path.get(path.size() - 1).equals(node)) {
                    for (int succIndex = 0; succIndex < succs.size() - 1; succIndex++) {
                        List<GraphNode> newPath = new ArrayList<>(path);
                        newPath.add(succs.get(succIndex));
                        allPaths.add(newPath);

                        if(!stack.contains(succs.get(succIndex)))
                            stack.push(succs.get(succIndex));
                    }
                    path.add(succs.get(succs.size() - 1));
                    if(!stack.contains(succs.get(succs.size() - 1)))
                        stack.push(succs.get(succs.size() - 1));
                }
            }
        }

        return allPaths;
    }*/

    /**
     * Retrieves all possible paths from start nodes (with no incoming edges) to end nodes (with no outgoing edges).
     *
     * @return A list of all paths, where each path is a list of nodes (Map.Entry) in the order they are traversed.
     */
    public List<List<GraphNode>> getAllPaths() {
        // Find start nodes (nodes with no predecessors)
        List<GraphNode> startNodes = filteredCFG.getRootsNodes();

        // Find end nodes (nodes with no successors)
        List<GraphNode> endNodes = filteredCFG.getLeafNodes();

        // Debug print edges for all nodes with node numbers
        /*System.out.println("\n=== DEBUG EDGE CONNECTIONS ===");
        int nodeCounter = 1;
        for (Map.Entry<String, String> node : filteredCFG.vertexSet()) {
            System.out.printf("\nNode %d:%n", nodeCounter++);
            System.out.println("  Key:   " + node.getKey());
            System.out.println("  Value: " + node.getValue());

            // Print incoming edges (predecessors)
            Set<DefaultEdge> incomingEdges = filteredCFG.incomingEdgesOf(node);
            System.out.println("  Incoming edges: " + incomingEdges.size());
            if (!incomingEdges.isEmpty()) {
                System.out.println("    From:");
                for (DefaultEdge edge : incomingEdges) {
                    Map.Entry<String, String> source = filteredCFG.getEdgeSource(edge);
                    System.out.println("      - " + source.getKey() + ": " + source.getValue());
                }
            }

            // Print outgoing edges (successors)
            Set<DefaultEdge> outgoingEdges = filteredCFG.outgoingEdgesOf(node);
            System.out.println("  Outgoing edges: " + outgoingEdges.size());
            if (!outgoingEdges.isEmpty()) {
                System.out.println("    To:");
                for (DefaultEdge edge : outgoingEdges) {
                    Map.Entry<String, String> target = filteredCFG.getEdgeTarget(edge);
                    System.out.println("      - " + target.getKey() + ": " + target.getValue());
                }
            }

            System.out.println("----------------------");
        }*/

        // Rest of the code remains the same...
        List<List<GraphNode>> allPaths = new ArrayList<>();
        for (GraphNode start : startNodes) {
            LinkedList<GraphNode> currentPath = new LinkedList<>();
            Set<GraphNode> visitedInPath = new HashSet<>();
            findAllPathsDFS(start, endNodes, currentPath, visitedInPath, allPaths);
        }

        return allPaths;

    }

    /**
     * Renames variables in the given list of paths.
     *
     * <p>This method iterates over each path (a list of code entries), identifies assignment statements,
     * and renames the variables by appending an underscore and a usage count. The new name is applied to both
     * the left-hand side (assignment target) and the right-hand side (usages) within each code line.
     *
     * <p>For example, if a variable "i0" is assigned, it will be renamed to "i0_1" on its first occurrence,
     * and subsequent usages will be updated accordingly.
     *
     * @param allPaths the original list of paths, where each path is a list of Map.Entry objects (key: identifier, value: code line)
     * @return a new list of paths with the variables renamed sensibly
     */
    private List<List<GraphNode>> variableRenaming(List<List<GraphNode>> allPaths) {
        List<List<GraphNode>> updatedPaths = new ArrayList<>();

        // Iterate over each path (list of entries)
        for (List<GraphNode> path : allPaths) {
            // Map to store the usage count for each variable in the current path
            Map<String, Integer> variableUsageCount = new HashMap<>();
            List<GraphNode> updatedPath = new ArrayList<>();

            // Process each code entry in the path
            for (GraphNode entry : path) {
                String codeLine = entry.getValue();
                String assignedVariable = "";

                // Check if the line contains an assignment using the assignationPattern regex.
                Matcher matcher = assignationPattern.matcher(codeLine);
                if (matcher.find()) {
                    // Extract the variable name from the assignment.
                    assignedVariable = matcher.group("assignation");

                    if (!assignedVariable.equals("null")) { // assignedVariable is "null" e.g. null (void) = method..
                        // Update usage count for this variable.
                        variableUsageCount.put(assignedVariable, variableUsageCount.getOrDefault(assignedVariable, 0) + 1);
                        // Create a new variable name by appending the current count.
                        String newVariableName = assignedVariable + "_" + variableUsageCount.get(assignedVariable);

                        // Update the full code line with the modified left-hand side.

                        String replaceRegex = String.format(variableRenamingRegex, assignedVariable.replace("$", "\\$"));
                        newVariableName = newVariableName.replace("$", "\\$");
                        codeLine = codeLine.replaceFirst(replaceRegex, newVariableName);
                    }
                }

                // Process variable replacements in the rest of the line.
                for (String var : variableUsageCount.keySet()) {
                    String segmentToReplace = codeLine;
                    if (codeLine.contains(" goto "))
                        segmentToReplace = codeLine.substring(0, codeLine.indexOf(" goto "));

                    // By default, the replacement uses the current count.
                    String replacementName = String.format("%s_%d", var, variableUsageCount.get(var));
//                    String segmentToReplace = codeLine;

                    if (var.equals(assignedVariable)) {
                        // For the right-hand side, use the previous count (if the assignment was processed)
                        replacementName = String.format("%s_%d", var, variableUsageCount.get(var) - 1)
                                .replace('-', '_');
                        // Only consider the portion after the equal sign.
                    }

                    // Replace occurrences of the variable in the designated segment.
                    String replaceRegex = String.format(variableRenamingRegex, var.replace("$", "\\$"));
                    replacementName = replacementName.replace("$", "\\$");

                    String updatedSegment = segmentToReplace.replace(replaceRegex, replacementName);

                    // Update the full code line with the replaced segment.
                    codeLine = codeLine.replaceAll(segmentToReplace, updatedSegment);
                }
                // Add the updated entry with the modified code line to the updated path.
                updatedPath.add(new GraphNode(entry.getKey(), codeLine));
            }
            // Add the fully updated path to the collection of updated paths.
            updatedPaths.add(updatedPath);
        }

        return updatedPaths;
    }


    /**
     * Recursively performs DFS to find all paths from the current node to any end node.
     *
     * @param currentNode   The current node being visited.
     * @param endNodes      List of all end nodes in the graph.
     * @param currentPath   The current path being explored.
     * @param visitedInPath Set of nodes visited in the current path to prevent cycles.
     * @param allPaths      List to collect all valid paths found.
     */
    private void findAllPathsDFS(GraphNode currentNode,
                                 List<GraphNode> endNodes,
                                 LinkedList<GraphNode> currentPath,
                                 Set<GraphNode> visitedInPath,
                                 List<List<GraphNode>> allPaths) {
        // Add current node to the path and mark as visited
        currentPath.add(currentNode);
        visitedInPath.add(currentNode);

        // If current node is an end node, save the current path
        if (endNodes.contains(currentNode)) {
            allPaths.add(new ArrayList<>(currentPath));
        } else {
            // Recur for all adjacent nodes not yet visited in the current path
            for (GraphNode succ : filteredCFG.getSuccessorNodes(currentNode)) {
                if (!visitedInPath.contains(succ)) {
                    findAllPathsDFS(succ, endNodes, currentPath, visitedInPath, allPaths);
                }
            }
        }

        // Backtrack: remove current node from path and unmark
        currentPath.removeLast();
        visitedInPath.remove(currentNode);
    }

    public void generateDotFile(String fileName, String packageName, String activity, String action) {
        try (FileWriter writer = new FileWriter(fileName)) {


            List<List<GraphNode>> allPaths = getAllPaths();
            List<List<GraphNode>> renamedAllPaths = variableRenaming(allPaths);

            writer.write(String.format("# package: %s\n", packageName));
            writer.write(String.format("# activity: %s\n", activity));
            writer.write(String.format("# action: %s\n", action));

            int pathNumber = 1;
            writer.write(String.format("digraph paths {\n"));
            System.out.println("         " + renamedAllPaths.size());
            StringBuilder sb = new StringBuilder();

            int count = 0;

            for (int pathIndex = 0; pathIndex < renamedAllPaths.size(); pathIndex++) {

                List<GraphNode> path = renamedAllPaths.get(pathIndex);
                List<String> nodeToHighlight = startFiltering(path);
                if (nodeToHighlight.isEmpty()) continue;

                count++;
                sb.append(String.format("subgraph path_%d {\n", pathNumber));
                for (int nodeNumber = 0; nodeNumber < path.size(); nodeNumber++) {
                    GraphNode node = path.get(nodeNumber);
                    String nodeName = "node" + (nodeNumber + 1) + "_" + pathNumber;
                    String nodeLabel = node.getValue().replace("\"", "\\\"");

                    if (nodeToHighlight.contains(node.getKey()))
                        sb.append(String.format("    %s [label=\"%s\", color=blue];\n", nodeName, nodeLabel));
                    else
                        sb.append(String.format("    %s [label=\"%s\"];\n", nodeName, nodeLabel));

                    if (nodeNumber > 0) {

                        String originalPrevNodeLabel = allPaths.get(pathIndex).get(nodeNumber - 1).getValue();
                        String originalLabel = allPaths.get(pathIndex).get(nodeNumber).getValue();

                        String prevNodeName = "node" + (nodeNumber) + "_" + pathNumber;

                        String ifLabel = "";
                        if (originalPrevNodeLabel.startsWith("if") && originalPrevNodeLabel.contains(" goto ")) {
                            String ifTrueNode = originalPrevNodeLabel.split(" goto ")[1];
                            ifLabel = String.format(" [label=\"%s\"]", ifTrueNode.equals(originalLabel));
                        }

                        sb.append(String.format("    %s -> %s%s;\n", prevNodeName, nodeName, ifLabel));
                    }
                }

                sb.append("}\n\n"); // Close the current subgraph
                pathNumber++;
            }
            sb.append("}\n");

            writer.write(sb.toString());
            System.out.println("  End New allPaths generation, path number: " + count);
        } catch (IOException e) {
            System.err.println("Error writing DOT file: " + e.getMessage());
        }
    }

    private List<String> startFiltering(List<GraphNode> path) {
        // Initialize a map to keep track of parameters that we need to monitor during the analysis.
        // The map's key represents the parameter's name, and the value represents its associated type.
//        Map<String, String> parametersToTrack = new HashMap<>();
        HashSet<String> parametersToTrack = new HashSet<>();

        // Map with the filtered Nodes
        List<String> filteredNodes = new ArrayList<>();

        // Store the count of parameters to track at the beginning of the loop.
        // This helps in detecting when we've finished processing all relevant parameters.
        int startParametersCount;

        // A flag to determine when we should start adding vertices to the filtered control flow graph.
        // Initially, we don't add any vertices until we've encountered the first relevant Intent or Bundle operation.
        boolean startAdding; // Start adding vertices after the root node

        do {
            startParametersCount = parametersToTrack.size();

            startAdding = false;
            boolean addNextNode = false;

            // Iterate through the units in the graph
            for (int i = 0; i < path.size(); i++) {
                GraphNode node = path.get(i);
                String nodeName = node.getKey();
                String line = node.getValue();

                // Match lines containing getExtra methods in Intent or Bundle objects
                Matcher extraMatcher = patternExtra.matcher(line);
                if (extraMatcher.find()) {
                    startAdding = true;
                    filteredNodes.add(nodeName);
                    parametersToTrack.add(extraMatcher.group("assignation"));
                }

                Matcher actionMatcher = patterGetAction.matcher(line);
                if (actionMatcher.find())
                    parametersToTrack.add(actionMatcher.group(1));

                // Continue adding nodes and edges after the first relevant extra is found
                if (!startAdding) continue;

                // Check if any saved parameters are used in the current unit
                if (parametersToTrack.stream().anyMatch(line::contains) || addNextNode) {
                    filteredNodes.add(nodeName);

                    Matcher matcher = assignationPattern.matcher(line);
                    if (matcher.find()) {
                        String newParameterName = matcher.group("assignation");
                        parametersToTrack.add(newParameterName);
                    }

                    String[] newParametersName = line.split("\\.<")[0].split(" ");
                    // Case: specialinvoke $r9.<java.math.BigInteger: void <init>(java.lang.String)>($r2)
                    // It stores $r9 in parametersToTrack
                    if (newParametersName.length == 2) {
                        String newParameterName = newParametersName[1];
                        parametersToTrack.add(newParameterName);
                    }
                }

                addNextNode = line.startsWith("if") && filteredNodes.contains(nodeName);
            }
        }
        // Continue the loop until no new parameters are tracked, meaning we have processed all relevant parameters.
        while (startParametersCount < parametersToTrack.size());

        return filteredNodes;
    }

}
