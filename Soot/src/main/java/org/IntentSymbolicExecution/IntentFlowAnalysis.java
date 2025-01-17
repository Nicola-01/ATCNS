package org.IntentSymbolicExecution;

import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;
import soot.Local;
import soot.SootMethod;
import soot.Unit;
import soot.ValueBox;
import soot.toolkits.graph.DominatorsFinder;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.graph.MHGDominatorsFinder;
import soot.toolkits.scalar.ArraySparseSet;
import soot.toolkits.scalar.FlowSet;
import soot.toolkits.scalar.ForwardFlowAnalysis;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jgrapht.*;

/**
 * A custom forward flow analysis class to analyze Intent-related operations in a method.
 */
public class IntentFlowAnalysis extends ForwardFlowAnalysis<Unit, FlowSet<Local>> {

    /**
     * The name of the class being analyzed.
     */
    private final String className;

    /**
     * The method in which flow analysis is performed.
     */
    private final SootMethod method;

    /**
     * An empty FlowSet, used as a placeholder for initializing flow sets.
     */
    private final FlowSet<Local> emptySet = new ArraySparseSet<>();

    /**
     * A map to associate each Unit with its corresponding generated flow set.
     */
    private final Map<Unit, FlowSet<Local>> unitToGenerateSet = new HashMap<>();

    // Regex

    /**
     * A regular expression to identify calls to getExtra methods in Intent objects.
     */
    private final String regexIntentExtra = "android.content.Intent: ([\\w.]+) get\\w*Extra\\(java\\.lang\\.String";
    /**
     * Pattern matching the regexIntentExtra.
     */
    private final Pattern patternIntentExtra = Pattern.compile(regexIntentExtra);

    /**
     * A regular expression to identify calls to get methods in Bundle objects.
     */
    private final String regexBundleExtra = "android.os.Bundle: ([\\w.]+) get\\w*\\(java\\.lang\\.String\\)";
    /**
     * Pattern matching the regexBundleExtra.
     */
    private final Pattern patternBundleExtra = Pattern.compile(regexBundleExtra);

    /**
     * A regular expression to identify calls to the getAction method.
     */
    private final String regexGetAction = "getAction\\(\\)";
    /**
     * Pattern matching the regexGetAction.
     */
    private final Pattern patterGetAction = Pattern.compile(regexGetAction);

    /**
     * Constructor for the IntentFlowAnalysis. It sets up the flow analysis and precomputes the generate sets.
     *
     * @param graph  The control flow graph for the method.
     * @param className  The class name of the method.
     * @param method  The SootMethod for which analysis is performed.
     */
    public IntentFlowAnalysis(ExceptionalUnitGraph graph, String className, SootMethod method) {
        super(graph);
        this.className = className;
        this.method = method;

        // Dominator analysis setup
        DominatorsFinder<Unit> dominatorsFinder = new MHGDominatorsFinder<>(graph);

        // Precompute generate sets
        for (Unit unit : graph) {
            FlowSet<Local> genSet = emptySet.clone();

            for (Unit dominator : dominatorsFinder.getDominators(unit))
                for (ValueBox valueBox : dominator.getDefBoxes())
                    if (valueBox.getValue() instanceof Local)
                        genSet.add((Local) valueBox.getValue());

            unitToGenerateSet.put(unit, genSet);
        }

        // Perform the analysis --> create the control flow graph without any filter
        doAnalysis();

        // Generate the filtered control flow graph with only intent-related edges
        FilteredControlFlowGraph filteredControlFlowGraph = getIntentGraph(graph);

        if (!filteredControlFlowGraph.isEmpty())
            System.out.println(filteredControlFlowGraph);

        // For printing the no filtered control flow graph
        //generateGraph(graph);
    }

    @Override
    protected FlowSet<Local> newInitialFlow() {
        return emptySet.clone();
    }

    @Override
    protected FlowSet<Local> entryInitialFlow() {
        return emptySet.clone();
    }

    @Override
    protected void flowThrough(FlowSet<Local> in, Unit unit, FlowSet<Local> out) {
        // Perform flow generation (kill set is empty)
        in.union(unitToGenerateSet.getOrDefault(unit, emptySet), out);
    }

    @Override
    protected void merge(FlowSet<Local> in1, FlowSet<Local> in2, FlowSet<Local> out) {
        in1.intersection(in2, out);
    }

    @Override
    protected void copy(FlowSet<Local> source, FlowSet<Local> dest) {
        source.copy(dest);
    }

    /**
     * Generates a filtered control flow graph that only contains edges related to Intent operations (e.g., getExtra calls).
     *
     * @param graph The exceptional unit graph of the method.
     * @return A FilteredControlFlowGraph containing only Intent-related edges.
     */
    private FilteredControlFlowGraph getIntentGraph(ExceptionalUnitGraph graph) {

        FilteredControlFlowGraph filteredControlFlowGraph = new FilteredControlFlowGraph(graph, className, method);

        // Initialize a map to keep track of parameters that we need to monitor during the analysis.
        // The map's key represents the parameter's name, and the value represents its associated type.
        Map<String, String> parametersToTrack = new HashMap<>();

        // Store the count of parameters to track at the beginning of the loop.
        // This helps in detecting when we've finished processing all relevant parameters.
        int startParametersCount;

        // A flag to determine when we should start adding vertices to the filtered control flow graph.
        // Initially, we don't add any vertices until we've encountered the first relevant Intent or Bundle operation.
        boolean startAdding = false; // Start adding vertices after the root node


        do {
            startParametersCount = parametersToTrack.size();
            filteredControlFlowGraph.resetGraphContent();

            // Iterate through the units in the graph
            for (Unit unit : graph) {
                String nodeName = "node" + unit.hashCode();
                String line = unit.toString();

                // Match lines containing getExtra methods in Intent or Bundle objects
                if (patternIntentExtra.matcher(line).find() || patternBundleExtra.matcher(line).find()) {
                    startAdding = true;
                    boolean isBundle = patternBundleExtra.matcher(line).find();

                    // Extract the extra and add the corresponding node to the graph
                    Map.Entry<String, String> stringStringPair = extractExtras(line, isBundle);
                    filteredControlFlowGraph.addToGraph(unit);

                    String parameterName = unit.toString().split(" ")[0];
                    parametersToTrack.put(parameterName, stringStringPair.getKey());

                }

                // Continue adding nodes and edges after the first relevant extra is found
                if (!startAdding) continue;

                // Check if any saved parameters are used in the current unit
                if (parametersToTrack.keySet().stream().anyMatch(line::contains)) {
                    filteredControlFlowGraph.addToGraph(unit);

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

        return filteredControlFlowGraph;
    }

    /**
     * Method to generate the control flow graph in DOT format.
     *
     * @param graph The ExceptionalUnitGraph representing the control flow of the method.
     */
    private void generateGraph(ExceptionalUnitGraph graph) {

        StringBuilder dotGraph = new StringBuilder();
        dotGraph.append(String.format("digraph %s_%s {\n", className.replace(".", "_"), method.getName()));

        // Add nodes and edges
        for (Unit unit : graph) {
            String nodeName = "node" + unit.hashCode();
            dotGraph.append(String.format("%s [label=\"%s\"];\n", nodeName, unit.toString().replace("\"", "\\\"")));

            for (Unit successor : graph.getSuccsOf(unit)) {
                String succNodeName = "node" + successor.hashCode();
                dotGraph.append(String.format("%s -> %s;\n", nodeName, succNodeName));
            }
        }

        dotGraph.append("}\n");
        System.out.println(dotGraph);

//        System.out.println(dotGraph);

//        String dotContent = dotGraph.toString();
//
//        Pattern intentClassPattern = Pattern.compile(".*android\\.content\\.Intent.*");
//        Pattern getIntentPattern = Pattern.compile(".*getIntent\\(\\).*");
//
//        Matcher intentClassMatcher = intentClassPattern.matcher(dotContent);
//        Matcher getIntentMatcher = getIntentPattern.matcher(dotContent);
//
//        System.out.println("Intent class calls inside " + className + "_" + method.getName() + ":");
//        while (intentClassMatcher.find())
//            System.out.println(intentClassMatcher.group());
//
//        System.out.println("getIntent() calls inside " + className + "_" + method.getName() + ":");
//        while (getIntentMatcher.find())
//            System.out.println(getIntentMatcher.group());

    }

    /**
     * Extracts the key and type of extra parameter from a line of code.
     *
     * @param line The line of code to analyze.
     * @param bundle Whether the line refers to a Bundle object.
     * @return A Map.Entry containing the key and type of the extra.
     */
    public static Map.Entry<String, String> extractExtras(String line, Boolean bundle) {
//        Matcher matcher = Pattern.compile("\\(\"([^\"]+)\".*?,\\s*(\\d+)\\)").matcher(line);

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
}
