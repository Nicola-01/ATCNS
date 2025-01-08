package org.example;

import soot.*;
import soot.jimple.*;
import soot.options.Options;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.scalar.ArraySparseSet;
import soot.toolkits.scalar.FlowSet;
import soot.toolkits.scalar.ForwardFlowAnalysis;

import java.util.List;
import java.util.Map;

public class IntentAnalysis {
    public static void main(String[] args) {

        String apkPath = args[0];
        String androidJarPath = args[1];

        // Initialize Soot
        Options.v().set_src_prec(Options.src_prec_apk);
        Options.v().set_android_jars(androidJarPath);
        Options.v().set_force_android_jar(androidJarPath + "/android.jar");
        Options.v().set_process_dir(List.of(apkPath));
        Options.v().set_whole_program(true);
        Options.v().set_allow_phantom_refs(true);

        // Load classes and start Soot
        Scene.v().loadNecessaryClasses();
        PackManager.v().getPack("jtp").add(new Transform("jtp.intentAnalysis", new BodyTransformer() {
            @Override
            protected void internalTransform(Body body, String phase, Map<String, String> options) {
                // Perform the analysis on each method body
                SootMethod method = body.getMethod();
                String className = method.getDeclaringClass().getName(); // Get the class name
                new IntentFlowAnalysis(new ExceptionalUnitGraph(body), className);
            }
        }));
        PackManager.v().runPacks();
    }

    // Custom ForwardFlowAnalysis
    static class IntentFlowAnalysis extends ForwardFlowAnalysis<Unit, FlowSet<String>> {
        private final String className; // Class name where the method belongs

        public IntentFlowAnalysis(ExceptionalUnitGraph graph, String className) {
            super(graph);
            this.className = className;
            doAnalysis();
        }

        @Override
        protected FlowSet<String> newInitialFlow() {
            return new ArraySparseSet<>();
        }

        @Override
        protected FlowSet<String> entryInitialFlow() {
            return new ArraySparseSet<>();
        }

        @Override
        protected void flowThrough(FlowSet<String> in, Unit unit, FlowSet<String> out) {
            // Copy the current flow set
            in.copy(out);

            // Check for Intent-related calls
            if (unit instanceof Stmt) {
                Stmt stmt = (Stmt) unit;

                if (stmt.containsInvokeExpr()) {
                    InvokeExpr invokeExpr = stmt.getInvokeExpr();
                    String methodName = invokeExpr.getMethod().getName();

                    // Check for methods related to Intents
                    if (methodName.equals("startActivity") ||
                        methodName.equals("sendBroadcast") ||
                        methodName.equals("startService") ||
                        invokeExpr.getMethod().getDeclaringClass().getName().equals("android.content.Intent")) {
                        System.out.println("Found Intent-related call in class " + className + " at: " + stmt);
                    }
                }
            }
        }

        @Override
        protected void merge(FlowSet<String> in1, FlowSet<String> in2, FlowSet<String> out) {
            in1.union(in2, out);
        }

        @Override
        protected void copy(FlowSet<String> source, FlowSet<String> dest) {
            source.copy(dest);
        }
    }
}
