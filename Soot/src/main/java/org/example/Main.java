package org.example;

import soot.*;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
//import soot.jimple.toolkits.callgraph.Sources;
import soot.options.Options;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java -jar ApkExecutionTree.jar <path-to-apk> <path-to-android-jar>");
            return;
        }

        String apkPath = args[0];
        String androidJar = args[1];

        // Soot setup
        Options.v().set_src_prec(Options.src_prec_apk);
        Options.v().set_android_jars(androidJar);
        Options.v().set_soot_classpath(androidJar);
        Options.v().set_process_dir(Collections.singletonList(apkPath));
        Options.v().set_allow_phantom_refs(true);
        Options.v().set_whole_program(true);
        Options.v().set_output_format(Options.output_format_none);

        // Load and process APK
        Scene.v().loadNecessaryClasses();

        // Generate Call Graph
        PackManager.v().runPacks();
        CallGraph callGraph = Scene.v().getCallGraph();

        // Save Execution Tree to a DOT file
        try (FileWriter writer = new FileWriter("execution_tree.dot")) {
            writer.write("digraph ExecutionTree {\n");
            Iterator<Edge> edges = callGraph.iterator();
            while (edges.hasNext()) {
                Edge edge = edges.next();
                String src = edge.getSrc().toString();
                String tgt = edge.getTgt().toString();
                writer.write(String.format("\"%s\" -> \"%s\";\n", src, tgt));
            }
            writer.write("}\n");
            System.out.println("Execution tree saved to execution_tree.dot");
        } catch (IOException e) {
            System.err.println("Error writing to file: " + e.getMessage());
        }
    }
}
