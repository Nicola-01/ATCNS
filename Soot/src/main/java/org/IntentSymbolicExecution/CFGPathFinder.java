package org.IntentSymbolicExecution;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;

import static org.IntentSymbolicExecution.RegexUtils.assignationPattern;

public class CFGPathFinder {

    private FilteredControlFlowGraph filteredControlFlowGraph;
    private Graph<Map.Entry<String, String>, DefaultEdge> filteredCFG;

    public CFGPathFinder(FilteredControlFlowGraph graph) {
        this.filteredControlFlowGraph = graph;
        this.filteredCFG = graph.getFullCFG();

    }

    /**
     * Retrieves all possible paths from start nodes (with no incoming edges) to end nodes (with no outgoing edges).
     *
     * @return A list of all paths, where each path is a list of nodes (Map.Entry) in the order they are traversed.
     */
    public List<List<Map.Entry<String, String>>> getAllPaths() {
        // Find start nodes (nodes with no predecessors)
        List<Map.Entry<String, String>> startNodes = filteredControlFlowGraph.getRootsNodes(filteredCFG);

        // Find end nodes (nodes with no successors)
        List<Map.Entry<String, String>> endNodes = filteredControlFlowGraph.getLeafNodes(filteredCFG);

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
        List<List<Map.Entry<String, String>>> allPaths = new ArrayList<>();
        for (Map.Entry<String, String> start : startNodes) {
            LinkedList<Map.Entry<String, String>> currentPath = new LinkedList<>();
            Set<Map.Entry<String, String>> visitedInPath = new HashSet<>();
            findAllPathsDFS(start, endNodes, currentPath, visitedInPath, allPaths);
        }

        return variableRenaming(allPaths);

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
    private List<List<Map.Entry<String, String>>> variableRenaming(List<List<Map.Entry<String, String>>> allPaths) {
        List<List<Map.Entry<String, String>>> updatedPaths = new ArrayList<>();

        // Iterate over each path (list of entries)
        for (List<Map.Entry<String, String>> path : allPaths) {
            // Map to store the usage count for each variable in the current path
            Map<String, Integer> variableUsageCount = new HashMap<>();
            List<Map.Entry<String, String>> updatedPath = new ArrayList<>();

            // Process each code entry in the path
            for (Map.Entry<String, String> entry : path) {
                String codeLine = entry.getValue();
                String assignedVariable = "";

                // Check if the line contains an assignment using the assignationPattern regex.
                Matcher matcher = assignationPattern.matcher(codeLine);
                if (matcher.find()) {
                    // Extract the variable name from the assignment.
                    assignedVariable = matcher.group(1);

                    if (!assignedVariable.equals("null")) {
                        // Update usage count for this variable.
                        variableUsageCount.put(assignedVariable, variableUsageCount.getOrDefault(assignedVariable, 0) + 1);

                        // Process the left-hand side (portion before the "=")
                        String leftSide = codeLine.substring(0, codeLine.indexOf("="));
                        // Create a new variable name by appending the current count.
                        String newVariableName = assignedVariable + "_" + variableUsageCount.get(assignedVariable);
                        // Replace the old variable name with the new one in the left-hand side.
                        String updatedLeftSide = leftSide.replace(assignedVariable, newVariableName);

                        // Update the full code line with the modified left-hand side.
                        codeLine = codeLine.replace(leftSide, updatedLeftSide);
                    }
                }

                // Process variable replacements in the rest of the line.
                for (String var : variableUsageCount.keySet()) {
                    // By default, the replacement uses the current count.
                    String replacementName = String.format("%s_%d", var, variableUsageCount.get(var));
                    String segmentToReplace = codeLine;

                    if (var.equals(assignedVariable)) {
                        // For the right-hand side, use the previous count (if the assignment was processed)
                        replacementName = String.format("%s_%d", var, variableUsageCount.get(var) - 1)
                                .replace('-', '_');
                        // Only consider the portion after the equal sign.
                        segmentToReplace = codeLine.substring(codeLine.indexOf("="));
                    }
                    // Replace occurrences of the variable in the designated segment.
                    String updatedSegment = segmentToReplace.replace(var, replacementName);
                    // Update the full code line with the replaced segment.
                    codeLine = codeLine.replace(segmentToReplace, updatedSegment);
                }
                // Add the updated entry with the modified code line to the updated path.
                updatedPath.add(Map.entry(entry.getKey(), codeLine));
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
    private void findAllPathsDFS(Map.Entry<String, String> currentNode,
                                 List<Map.Entry<String, String>> endNodes,
                                 LinkedList<Map.Entry<String, String>> currentPath,
                                 Set<Map.Entry<String, String>> visitedInPath,
                                 List<List<Map.Entry<String, String>>> allPaths) {
        // Add current node to the path and mark as visited
        currentPath.add(currentNode);
        visitedInPath.add(currentNode);

        // If current node is an end node, save the current path
        if (endNodes.contains(currentNode)) {
            allPaths.add(new ArrayList<>(currentPath));
        } else {
            // Recur for all adjacent nodes not yet visited in the current path
            for (DefaultEdge edge : filteredCFG.outgoingEdgesOf(currentNode)) {
                Map.Entry<String, String> neighbor = filteredCFG.getEdgeTarget(edge);
                if (!visitedInPath.contains(neighbor)) {
                    findAllPathsDFS(neighbor, endNodes, currentPath, visitedInPath, allPaths);
                }
            }
        }

        // Backtrack: remove current node from path and unmark
        currentPath.removeLast();
        visitedInPath.remove(currentNode);
    }

    public void generateDotFile(List<List<Map.Entry<String, String>>> allPaths, Map<String, String> filteredNodes, String fileName, String packageName, String activity, String action) {
        try (FileWriter writer = new FileWriter(fileName)) {

            writer.write(String.format("# package: %s\n", packageName));
            writer.write(String.format("# activity: %s\n", activity));
            writer.write(String.format("# action: %s\n", action));

            int pathNumber = 1;
            writer.write(String.format("digraph paths {\n"));
            for (List<Map.Entry<String, String>> path : allPaths) {
                writer.write(String.format("subgraph path_%d {\n", pathNumber));

                int nodeNumber = 1;
                Map.Entry<String, String> prevNode = null;

                for (Map.Entry<String, String> node : path) {
                    String nodeName = "node" + nodeNumber + "_" + pathNumber;
                    String nodeLabel = node.getValue();
                    if (filteredNodes.containsKey(node.getKey()))
                        writer.write(String.format("    %s [label=\"%s\", color=blue];\n", nodeName, nodeLabel.replace("\"", "\\\"")));
                    else
                        writer.write(String.format("    %s [label=\"%s\"];\n", nodeName, nodeLabel.replace("\"", "\\\"")));

                    if (prevNode != null) {
                        String prevNodeName = "node" + (nodeNumber - 1) + "_" + pathNumber;

                        String ifLable = "";
                        if (prevNode.getValue().startsWith("if") && prevNode.getValue().contains(" goto ")) {
                            String ifTrueNode = prevNode.getValue().split(" goto ")[1];
                            ifLable = String.format(" [label=\"%s\"]", ifTrueNode.equals(nodeLabel));
                        }

                        writer.write(String.format("    %s -> %s%s;\n", prevNodeName, nodeName, ifLable));
                    }

                    prevNode = node;
                    nodeNumber++;
                }

                writer.write("}\n\n"); // Close the current subgraph
                pathNumber++;
            }
            writer.write("}\n");
        } catch (IOException e) {
            System.err.println("Error writing DOT file: " + e.getMessage());
        }
    }

}
