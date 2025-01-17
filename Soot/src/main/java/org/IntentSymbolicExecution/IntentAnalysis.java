package org.IntentSymbolicExecution;

import soot.*;
//import soot.jimple.*;
import soot.options.Options;
//import soot.toolkits.graph.DominatorsFinder;
import soot.toolkits.graph.ExceptionalUnitGraph;
/**import soot.toolkits.graph.MHGDominatorsFinder;
 import soot.toolkits.graph.UnitGraph;
 import soot.toolkits.scalar.ArraySparseSet;
 import soot.toolkits.scalar.FlowSet;
 import soot.toolkits.scalar.ForwardFlowAnalysis;
 */
import java.util.*;

/**
 * This class performs static analysis on an APK file to
 * analyze Intent-related operations in their methods.
 */
public class IntentAnalysis {
    /**
     * Constructor that initializes the analysis for a given APK file.
     *
     * @param apkPath The path to the APK file to be analyzed.
     */
    public IntentAnalysis(String apkPath, String androidJarPath) {
        // Parse the APK's AndroidManifest.xml to retrieve metadata
        ManifestParsing manifest = new ManifestParsing(apkPath);

        // Retrieve necessary data from the manifest
        int SDK_Version = manifest.getSDK_Version();
        List<String> exportedActivities = manifest.getExportedActivities();
        //String packageName = manifest.getPackageName();

        // Download the corresponding Android SDK JAR and get its path
        if (androidJarPath == null)
            androidJarPath = (new AndroidJarDownloader(SDK_Version)).getAndroidJarsPath();

        // Set up Soot for analyzing the APK, and load classes
        setupSoot(apkPath, androidJarPath);
        Scene.v().loadNecessaryClasses();

        Map<String, FilteredControlFlowGraph> graphs = getCFGs(exportedActivities);

        System.out.println();

        for (FilteredControlFlowGraph graph : graphs.values())
            if (!graph.isEmpty())
                System.out.println(graph);
    }

    /**
     * Configures Soot options for analyzing the given APK file.
     *
     * @param apkPath        The path to the APK file to be analyzed.
     * @param androidJarPath The path to the Android SDK JAR file corresponding to the APK's SDK version.
     */
    private static void setupSoot(String apkPath, String androidJarPath) {
        // Initialize Soot
        Options.v().set_src_prec(Options.src_prec_apk);
        Options.v().set_android_jars(androidJarPath);
        Options.v().set_process_dir(List.of(apkPath));
        Options.v().set_whole_program(true);
        Options.v().set_allow_phantom_refs(true);
        Options.v().set_process_multiple_dex(true);
        Options.v().set_force_android_jar(androidJarPath);

        // Enable Dexpler for analyzing DEX files
        Options.v().set_src_prec(Options.src_prec_apk);
    }

    /**
     * Generates a mapping of method names to filtered control flow graphs (CFGs) for exported activities.
     *     *
     * @param exportedActivities A list exported activities from the APK.
     * @return A map where the keys are method identifiers in the format "ClassName.MethodName",
     *         and the values are {@link FilteredControlFlowGraph} objects representing the control flow of the corresponding method.
     */
    private static Map<String, FilteredControlFlowGraph> getCFGs(List<String> exportedActivities) {
        Map<String, FilteredControlFlowGraph> graphs = new HashMap<>();

        // Add a custom transformation to analyze methods for Intent-related operations
        PackManager.v().getPack("jtp").add(new Transform("jtp.intentAnalysis", new BodyTransformer() {
            @Override
            protected void internalTransform(Body body, String phase, Map<String, String> options) {
                // Perform the analysis on each method body
                SootMethod method = body.getMethod();
                String className = method.getDeclaringClass().getName(); // Get the class name

                // Check if the class is an exported activity
                if (exportedActivities.contains(className))
                    graphs.put(className + "." + method.getName(), new FilteredControlFlowGraph(new ExceptionalUnitGraph(body), className, method.getName()));
            }
        }));

        // Run all Soot transformations
        PackManager.v().runPacks();

        return graphs;
    }
}

