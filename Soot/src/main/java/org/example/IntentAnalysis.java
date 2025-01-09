package org.example;

import soot.*;
import soot.jimple.*;
import soot.options.Options;
import soot.toolkits.graph.DominatorsFinder;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.graph.MHGDominatorsFinder;
import soot.toolkits.scalar.ArraySparseSet;
import soot.toolkits.scalar.FlowSet;
import soot.toolkits.scalar.ForwardFlowAnalysis;

import java.util.*;

/**
 * This class performs static analysis on an APK file to
 * analyze Intent-related operations in their methods.
 */
public class IntentAnalysis {

    /**
     * A set to keep track of classes analyzed during the process.
     */
    private static final Set<String> customClasses = new HashSet<>();

    /**
     * Constructor that initializes the analysis for a given APK file.
     *
     * @param apkPath The path to the APK file to be analyzed.
     */
    public IntentAnalysis(String apkPath) {
        // Parse the APK's AndroidManifest.xml to retrieve metadata
        ManifestParsing manifest = new ManifestParsing(apkPath);

        // Retrieve necessary data from the manifest
        int SDK_Version = manifest.getSDK_Version();
        List<String> exportedActivities = manifest.getExportedActivities();
        String packageName = manifest.getPackageName();

        // Download the corresponding Android SDK JAR and get its path
        String androidJarPath = (new AndroidJarDownloader(SDK_Version)).getAndroidJarsPath();

        // Set up Soot for analyzing the APK, and load classes
        setupSoot(apkPath, androidJarPath);
        Scene.v().loadNecessaryClasses();

        // Add a custom transformation to analyze methods for Intent-related operations
        PackManager.v().getPack("jtp").add(new Transform("jtp.intentAnalysis", new BodyTransformer() {
            @Override
            protected void internalTransform(Body body, String phase, Map<String, String> options) {
                // Perform the analysis on each method body
                SootMethod method = body.getMethod();
                String className = method.getDeclaringClass().getName(); // Get the class name

                // Check if the class is an exported activity
                if (exportedActivities.contains(className)) {
                    System.out.println(className + "." + method.getName());
                    customClasses.add(className);
                    //customPackages.add(className.substring(0, className.lastIndexOf(".")));

                    // Perform Intent flow analysis on the method's body
                    new IntentFlowAnalysis(new ExceptionalUnitGraph(body), className, method);
                }

            }
        }));

        // Run all Soot transformations
        PackManager.v().runPacks();

        // Output packages and classes
        //System.out.println("Packages:");
        //customPackages.forEach(System.out::println);

        //System.out.println("\nClasses:");
        //customClasses.forEach(System.out::println);

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
        Options.v().set_force_android_jar(androidJarPath);
        Options.v().set_process_dir(List.of(apkPath));
        Options.v().set_whole_program(true);
        Options.v().set_allow_phantom_refs(true);

        // Set up Dexpler to analyze DEX --> otherwise custom classes are not analyzed
        Options.v().set_src_prec(Options.src_prec_apk);
        Options.v().set_process_multiple_dex(true);

    }
}

/**
 * A custom forward flow analysis class to analyze Intent-related operations in a method.
 */
class IntentFlowAnalysis extends ForwardFlowAnalysis {
    private final String className; // The class where the analyzed method belongs
    private final SootMethod method; // The method being analyzed

    FlowSet emptySet = new ArraySparseSet();
    Map<Unit, FlowSet> unitToGenerateSet;

    /**
     * Constructor for the IntentFlowAnalysis class.
     *
     * @param graph     The control flow graph of the method being analyzed.
     * @param className The name of the class containing the method.
     * @param method    The method being analyzed.
     */
    public IntentFlowAnalysis(ExceptionalUnitGraph graph, String className, SootMethod method) {
        super(graph);
        this.className = className;
        this.method = method;

        DominatorsFinder df = new MHGDominatorsFinder(graph);
        unitToGenerateSet = new HashMap<Unit, FlowSet>(graph.size() * 2 + 1, 0.7f);

        // pre-compute generate sets
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
    }

    /**
     * All INs are initialized to the empty set.
     **/
    protected Object newInitialFlow() {
        return emptySet.clone();
    }

    /**
     * IN(Start) is the empty set
     **/
    protected Object entryInitialFlow() {
        return emptySet.clone();
    }

    /**
     * OUT is the same as IN plus the genSet.
     **/
    protected void flowThrough(Object inValue, Object unit, Object outValue) {
        FlowSet
                in = (FlowSet) inValue,
                out = (FlowSet) outValue;

        // perform generation (kill set is empty)
        in.union(unitToGenerateSet.get(unit), out);
    }

    /**
     * All paths == Intersection.
     **/
    protected void merge(Object in1, Object in2, Object out) {
        FlowSet
                inSet1 = (FlowSet) in1,
                inSet2 = (FlowSet) in2,
                outSet = (FlowSet) out;

        inSet1.intersection(inSet2, outSet);
    }

    protected void copy(Object source, Object dest) {
        FlowSet
                sourceSet = (FlowSet) source,
                destSet = (FlowSet) dest;

        sourceSet.copy(destSet);
    }
}
