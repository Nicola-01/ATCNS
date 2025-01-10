package org.example;

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

import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
//import java.util.Iterator;
import java.util.Map;

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
        generateGraph(graph);
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
        //System.out.println(dotGraph);

        String simpleClassName = className.substring(className.lastIndexOf('.') + 1);
        String methodName = method.getName();
        String fileName = simpleClassName + "_" + methodName + ".dot";
        
        try (FileWriter writer = new FileWriter("dotFiles/" + fileName)) {

            writer.write(dotGraph.toString());
            System.out.println("Graph saved to /Soot/dotFiles/" + fileName);

        } catch (IOException e) {
            System.err.println("Error writing graph to file: " + e.getMessage());
        }
    }
}
