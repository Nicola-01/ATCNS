package org.example;

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

    public IntentFlowAnalysis(ExceptionalUnitGraph graph, String className, SootMethod method) {
        super(graph);
        this.className = className;
        this.method = method;

        // Dominator analysis setup
        DominatorsFinder<Unit> dominatorsFinder = new MHGDominatorsFinder<>(graph);

        // Precompute generate sets
        for (Unit unit : graph) {
            FlowSet<Local> genSet = emptySet.clone();

            for (Unit dominator : dominatorsFinder.getDominators(unit)) {
                for (ValueBox valueBox : dominator.getDefBoxes()) {
                    if (valueBox.getValue() instanceof Local) {
                        genSet.add((Local) valueBox.getValue());
                    }
                }
            }
            unitToGenerateSet.put(unit, genSet);
        }

        // Perform the analysis
        doAnalysis();

        // Generate the control flow graph in DOT format
//        printGraph(graph);
        getIntentGraph(graph);
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


    private final String regexIntentExtra = "get\\w*Extra\\(java\\.lang\\.String";
    private final Pattern patternIntentExtra = Pattern.compile(regexIntentExtra);

    private final String regexBundleExtra = "android.os.Bundle: ([\\w.]+) get\\w*\\(java\\.lang\\.String\\)";
    private final Pattern patternBundleExtra = Pattern.compile(regexBundleExtra);

    private void getIntentGraph(ExceptionalUnitGraph graph) {

        Graph<Map.Entry<String, String>, DefaultEdge> myExecutionGraph = new SimpleGraph<>(DefaultEdge.class);
        Map<String, String> intentParameters = new HashMap<>();

        for (Unit unit : graph) {
            String nodeName = "node" + unit.hashCode();
            String line = unit.toString();

            if (line.contains("android.os.Bundle getExtras()"))
                myExecutionGraph.addVertex(Map.entry(nodeName, "getIntent().getExtras()")); // root

            if (line.contains("android.content.Intent") && patternIntentExtra.matcher(line).find()) {// TODO remove .contains, use the regex
//                intent.add(extractExtras(line, false)); // TODO
            } else if (patternBundleExtra.matcher(line).find()) { // extract a parameter from a bundle
                Map.Entry<String, String> stringStringPair = extractExtras(line, true);
                System.out.println("Key: " + stringStringPair.getKey() + "; Type: " + stringStringPair.getValue());

                addToGraph(graph, myExecutionGraph, unit, Map.entry(nodeName, "get" + stringStringPair.getValue() + "()"));

                String parameterName = unit.toString().split(" ")[0];
                intentParameters.put(parameterName, stringStringPair.getKey());
            }

            boolean containsKey = intentParameters.keySet().stream()
                    .anyMatch(line::contains);

            if (containsKey)
                addToGraph(graph, myExecutionGraph, unit, Map.entry(nodeName, unit.toString()));

            if (!myExecutionGraph.vertexSet().isEmpty() && unit.toString().equals("return"))
                addToGraph(graph, myExecutionGraph, unit, Map.entry(nodeName, unit.toString()));
        }

//        System.out.println(myExecutionGraph);

        printGraph(myExecutionGraph);
    }

    private void printGraph(Graph<Map.Entry<String, String>, DefaultEdge> graph) {

        StringBuilder dotGraph = new StringBuilder();
        dotGraph.append(String.format("digraph %s_%s {\n", className.replace(".", "_"), method.getName()));

        // Add nodes and edges
        for (Map.Entry<String, String> v : graph.vertexSet()) {
            String nodeName = v.getKey();
            dotGraph.append(String.format("%s [label=\"%s\"];\n", nodeName, v.getValue().replace("\"", "\\\"")));
        }

        for (DefaultEdge e : graph.edgeSet()) {
            String[] edge = e.toString().split(" : ");
            String target = edge[0].substring(1, edge[0].indexOf('='));
            String source = edge[1].substring(0, edge[1].indexOf('='));
            dotGraph.append(String.format("%s -> %s ;\n", source, target));
        }


        dotGraph.append("}\n");
        System.out.println(dotGraph);
    }

    /**
     * Method to generate the control flow graph in DOT format.
     */
    private void generateGraph(ExceptionalUnitGraph graph) {

        StringBuilder dotGraph = new StringBuilder();
        dotGraph.append(String.format("digraph %s_%s {\n", className.replace(".","_"), method.getName()));

        // Add nodes and edges
        for (Unit unit : graph) {
            String nodeName = "node" + unit.hashCode();
            dotGraph.append(String.format("%s [label=\"%s\"];\n", nodeName.replace("\"","\\\""), unit));

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

    private void addToGraph(ExceptionalUnitGraph graph, Graph<Map.Entry<String, String>, DefaultEdge> myExecutionGraph, Unit unit, Map.Entry<String, String> entry) {
        myExecutionGraph.addVertex(entry);

        Map<String, Map.Entry<String, String>> elements = new HashMap<>();

        for (Map.Entry<String, String> e : myExecutionGraph.vertexSet()) {
            elements.put(e.getKey(), e);
        }

        while (!graph.getPredsOf(unit).isEmpty()) {
            Unit pred = graph.getPredsOf(unit).get(0);
            if (elements.containsKey("node" + pred.hashCode())) {
                myExecutionGraph.addEdge(entry, elements.get("node" + pred.hashCode()));
                break;
            }
            unit = pred;
        }
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
