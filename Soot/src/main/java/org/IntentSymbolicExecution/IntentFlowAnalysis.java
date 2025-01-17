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
    private final String className;
    private final SootMethod method;
    private final FlowSet<Local> emptySet = new ArraySparseSet<>();
    private final Map<Unit, FlowSet<Local>> unitToGenerateSet = new HashMap<>();


    private final String regexIntentExtra = "android.content.Intent: ([\\w.]+) get\\w*Extra\\(java\\.lang\\.String";
    private final Pattern patternIntentExtra = Pattern.compile(regexIntentExtra);

    private final String regexBundleExtra = "android.os.Bundle: ([\\w.]+) get\\w*\\(java\\.lang\\.String\\)";
    private final Pattern patternBundleExtra = Pattern.compile(regexBundleExtra);

    private final String regexGetAction = "getAction\\(\\)";
    private final Pattern patterGetAction = Pattern.compile(regexGetAction);

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

        // Generate the control flow graph with filter for showing only intent related edges
        FilteredControlFlowGraph filteredControlFlowGraph = getIntentGraph(graph);

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

    private FilteredControlFlowGraph getIntentGraph(ExceptionalUnitGraph graph) {

        FilteredControlFlowGraph filteredControlFlowGraph = new FilteredControlFlowGraph(graph, className, method);

        Map<String, String> intentParameters = new HashMap<>();
        int startParametersCount;
        boolean startAdding = false; // start adding vertex only after the root

        do {
            startParametersCount = intentParameters.size();
            filteredControlFlowGraph.resetGraphContent();

            for (Unit unit : graph) {
                String nodeName = "node" + unit.hashCode();
                String line = unit.toString();

                if (patternIntentExtra.matcher(line).find() || patternBundleExtra.matcher(line).find()) {
                    startAdding = true;
                    boolean isBundle = patternBundleExtra.matcher(line).find();

                    Map.Entry<String, String> stringStringPair = extractExtras(line, isBundle);

//                    filteredControlFlowGraph.addToGraph(unit, Map.entry(nodeName, "get" + stringStringPair.getValue() + "()"));
                    filteredControlFlowGraph.addToGraph(unit, Map.entry(nodeName, line));

                    String parameterName = unit.toString().split(" ")[0];
                    intentParameters.put(parameterName, stringStringPair.getKey());

                }

                if (!startAdding) continue;

                // check if in the line there is the name of a saved parameter
                if (intentParameters.keySet().stream().anyMatch(line::contains)) {
                    filteredControlFlowGraph.addToGraph(unit, Map.entry(nodeName, line));

                    // start tracking the new parameter (that depends on a saved parameter)
                    String newParameterName = unit.toString().split(" = ")[0];
                    // Case: $r2 = staticinvoke <java.lang.String: java.lang.String valueOf(int)>(i0)
                    // It stores $r2 in intentParameters
                    if (newParameterName.split(" ").length == 1)
                        intentParameters.put(newParameterName, newParameterName);

                    String[] newParametersName = unit.toString().split(".<")[0].split(" ");
                    // Case: specialinvoke $r9.<java.math.BigInteger: void <init>(java.lang.String)>($r2)
                    // It stores $r9 in intentParameters
                    if (newParametersName.length == 2)
                        intentParameters.put(newParametersName[1], newParametersName[1]);

                    // if is a switch, save the parameter used
                    if (line.startsWith("lookupswitch")) {
                        Pattern pattern = Pattern.compile("\\(([^)]+)\\)");
                        Matcher matcher = pattern.matcher(line);

                        if (matcher.find())
                            intentParameters.put(matcher.group(1), matcher.group(1));
                    }
                }
            }
        }
        while (startParametersCount < intentParameters.size());

        return filteredControlFlowGraph;
    }

    /**
     * Method to generate the control flow graph in DOT format.
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
