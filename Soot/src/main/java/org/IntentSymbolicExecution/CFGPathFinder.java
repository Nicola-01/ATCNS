package org.IntentSymbolicExecution;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;

public class CFGPathFinder {

    private Graph<Map.Entry<String, String>, DefaultEdge> filteredCFG;
    
    public CFGPathFinder(FilteredControlFlowGraph graph) {
        this.filteredCFG = graph.getFilteredCFG();

    }

    /**
     * Retrieves all possible paths from start nodes (with no incoming edges) to end nodes (with no outgoing edges).
     *
     * @return A list of all paths, where each path is a list of nodes (Map.Entry) in the order they are traversed.
     */
    public List<List<Map.Entry<String, String>>> getAllPaths() {
        // Find start nodes (nodes with no predecessors)
        List<Map.Entry<String, String>> startNodes = filteredCFG.vertexSet().stream()
            .filter(v -> filteredCFG.incomingEdgesOf(v).isEmpty()) // No incoming edges = start node
            .collect(Collectors.toList());
    
        // Find end nodes (nodes with no successors)
        List<Map.Entry<String, String>> endNodes = filteredCFG.vertexSet().stream()
            .filter(v -> filteredCFG.outgoingEdgesOf(v).isEmpty()) // No outgoing edges = end node
            .collect(Collectors.toList());
    
        // Debug print edges for all nodes with node numbers
        System.out.println("\n=== DEBUG EDGE CONNECTIONS ===");
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
        }
    
        // Rest of the code remains the same...
        List<List<Map.Entry<String, String>>> allPaths = new ArrayList<>();
        for (Map.Entry<String, String> start : startNodes) {
            LinkedList<Map.Entry<String, String>> currentPath = new LinkedList<>();
            Set<Map.Entry<String, String>> visitedInPath = new HashSet<>();
            findAllPathsDFS(start, endNodes, currentPath, visitedInPath, allPaths);
        }
        return allPaths;
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


}
