package org.example;

import soot.*;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.options.Options;
import soot.toolkits.graph.*;
import soot.util.*;

import java.util.*;

public class Main {

    public static void main(String[] args) {
        // Ensure that a JAR file is provided as an argument
        if (args.length != 1) {
            System.out.println("Usage: java SootAnalyzer <path-to-jar>");
            return;
        }

        // The path to the JAR file to analyze
        String jarPath = args[0];

        // Set up Soot options
        setupSoot(jarPath);

        // Run Soot analysis to generate the call graph
        generateCallGraph();
    }

    private static void setupSoot(String jarPath) {
        // Set up Soot for analyzing a JAR file
        Options.v().set_soot_classpath(jarPath);
        Options.v().set_allow_phantom_refs(true);
        Options.v().set_process_dir(Collections.singletonList(jarPath));
        Options.v().set_output_format(Options.output_format_none);
        Options.v().set_output_dir("soot_output");

        // Set Soot to analyze the classes and generate the call graph
        Options.v().set_src_prec(Options.src_prec_class); // Analyzing class files
        //Options.v().set_phantom_refs(true); // Enable phantom references for classes
        Options.v().set_whole_program(true); // Perform whole program analysis

        // Enable the Call Graph generation
        Options.v().set_include_all(true); // This includes all methods in the call graph
    }

    private static void generateCallGraph() {
        // Start the analysis
        SootClass mainClass = Scene.v().loadClassAndSupport("MainActivity"); // Replace with actual entry point if needed
        mainClass.setApplicationClass();
        Scene.v().loadNecessaryClasses();

        // Generate the Call Graph
        CallGraph cg = Scene.v().getCallGraph();

        // Display the call graph or execution tree
        System.out.println("Call Graph:");

        // Iterate through the call graph and print the nodes
        for (Iterator<Edge> it = cg.iterator(); it.hasNext(); ) {
            Edge edge = it.next();
            SootMethod src = edge.getSrc().method();
            SootMethod dest = edge.getTgt().method();
            System.out.println("Call from " + src.getSignature() + " to " + dest.getSignature());
        }
    }
}
