package org.example;

import soot.Local;
import soot.SootMethod;
import soot.Unit;
import soot.ValueBox;
import soot.toolkits.graph.DominatorsFinder;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.graph.MHGDominatorsFinder;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.scalar.ArraySparseSet;
import soot.toolkits.scalar.FlowSet;
import soot.toolkits.scalar.ForwardFlowAnalysis;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map; /**
 * A custom forward flow analysis class to analyze Intent-related operations in a method.
 */
public class IntentFlowAnalysis extends ForwardFlowAnalysis {
    private final String className;
    private final SootMethod method;
    private FlowSet emptySet = new ArraySparseSet();
    private Map<Unit, FlowSet> unitToGenerateSet;

    public IntentFlowAnalysis(ExceptionalUnitGraph graph, String className, SootMethod method) {
        super(graph);
        this.className = className;
        this.method = method;

        // Dominator analysis setup
        DominatorsFinder df = new MHGDominatorsFinder(graph);
        unitToGenerateSet = new HashMap<Unit, FlowSet>(graph.size() * 2 + 1, 0.7f);

        // Precompute generate sets
        for (Iterator unitIt = graph.iterator(); unitIt.hasNext(); ) {
            Unit s = (Unit) unitIt.next();
            FlowSet genSet = emptySet.clone();

            for (Iterator domsIt = df.getDominators(s).iterator(); domsIt.hasNext(); ) {
                Unit dom = (Unit) domsIt.next();
                for (Iterator boxIt = dom.getDefBoxes().iterator(); boxIt.hasNext(); ) {
                    ValueBox box = (ValueBox) boxIt.next();
                    if (box.getValue() instanceof Local)
                        genSet.add(box.getValue(), genSet);
                }
            }

            unitToGenerateSet.put(s, genSet);
        }

        doAnalysis();
        generateGraph(graph);
    }

    protected Object newInitialFlow() {
        return emptySet.clone();
    }

    protected Object entryInitialFlow() {
        return emptySet.clone();
    }

    protected void flowThrough(Object inValue, Object unit, Object outValue) {
        FlowSet in = (FlowSet) inValue;
        FlowSet out = (FlowSet) outValue;

        // Perform flow generation (kill set is empty)
        in.union(unitToGenerateSet.get(unit), out);
    }

    protected void merge(Object in1, Object in2, Object out) {
        FlowSet inSet1 = (FlowSet) in1;
        FlowSet inSet2 = (FlowSet) in2;
        FlowSet outSet = (FlowSet) out;

        inSet1.intersection(inSet2, outSet);
    }

    protected void copy(Object source, Object dest) {
        FlowSet sourceSet = (FlowSet) source;
        FlowSet destSet = (FlowSet) dest;
        sourceSet.copy(destSet);
    }

    /**
     * Method to generate the control flow graph in DOT format.
     */
    private void generateGraph(UnitGraph graph) {
        StringBuilder dotGraph = new StringBuilder();
        dotGraph.append("digraph " + className + "." + method.getName() + " {\n");

        // Add nodes (statements)
        for (Unit unit : graph) {
            String nodeName = "node" + unit.hashCode();
            dotGraph.append(nodeName + " [label=\"" + unit.toString() + "\"];\n");

            // Add edges (control flow)
            for (Unit succ : graph.getSuccsOf(unit)) {
                String succNodeName = "node" + succ.hashCode();
                dotGraph.append(nodeName + " -> " + succNodeName + ";\n");
            }
        }

        dotGraph.append("}\n");

        // Output the DOT representation
        System.out.println(dotGraph.toString());
    }
}
