package org.IntentSymbolicExecution;

import soot.*;
import soot.jimple.AssignStmt;
import soot.jimple.DoubleConstant;
import soot.jimple.FloatConstant;
import soot.jimple.IntConstant;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.LongConstant;
import soot.jimple.StaticFieldRef;
import soot.jimple.StringConstant;
import soot.options.Options;
import soot.tagkit.ConstantValueTag;
import soot.tagkit.Tag;
import soot.toolkits.graph.ExceptionalUnitGraph;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

import org.IntentSymbolicExecution.ControlFlowGraph.GraphNode;

/**
 * This class performs static analysis on an APK file to
 * analyze Intent-related operations in their methods.
 * It uses Soot to extract the control flow graph (CFG) of methods,
 * filters the CFG to focus on Intent-related operations, and generates
 * a graph of execution paths for further symbolic execution using Z3.
 */
public class IntentAnalysis {

    public static class GlobalVariablesInfo {
        private String type;
        private String value;

        public GlobalVariablesInfo(String type, String value) {
            this.type = type;
            this.value = value;
        }

        public String getType() {
            return type;
        }

        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return String.format("(%s) = %s", type, value);
        }
    }

    /**
     * Constructor that initializes the analysis for a given APK file.
     *
     * @param apkPath        The path to the APK file to be analyzed.
     * @param androidJarPath The path to the Android SDK JAR file corresponding to the APK's SDK version.
     *                       If null, the appropriate SDK JAR will be downloaded automatically.
     */
    public IntentAnalysis(String apkPath, String androidJarPath) {
        // Parse the APK's AndroidManifest.xml to retrieve metadata
        ManifestParsing manifest = new ManifestParsing(apkPath);

        // Retrieve necessary data from the manifest
        int SDK_Version = manifest.getSDK_Version();
        List<ManifestParsing.Activity> exportedActivities = manifest.getExportedActivities();
        String packageName = manifest.getPackageName();

        // Download the corresponding Android SDK JAR and get its path
        if (androidJarPath == null)
            androidJarPath = (new AndroidJarDownloader(SDK_Version)).getAndroidJarsPath();

        // Set up Soot for analyzing the APK, and load classes
        setupSoot(apkPath, androidJarPath);
        Scene.v().loadNecessaryClasses();

        // Get global variables of the application
        Map<String, GlobalVariablesInfo> globalVariables = getGlobalVariables(packageName);
        /*System.out.println("\nGLOBAL VARIABLES:");
        for (Map.Entry<String, GlobalVariablesInfo> entry : globalVariables.entrySet())
            System.out.println("Variable: " + entry.getKey() + " " + entry.getValue().toString());

        System.out.println();*/

        // Compute the Control Flow Graph (CFG) for exported activities
        Map<String, ExceptionalUnitGraph> graphs = getCFGs(exportedActivities, packageName);

        System.out.println();

        // Analyze each CFG to extract Intent-related paths
        for (Map.Entry<String, ExceptionalUnitGraph> entry : graphs.entrySet()) {

            String methodName = entry.getKey().substring(0, entry.getKey().lastIndexOf("-"));

            String activityName = methodName.substring(0, methodName.lastIndexOf("."));
            String action = null;
            for (ManifestParsing.Activity exportedActivity : exportedActivities) {
                if (exportedActivity.getName().equals(activityName)) {
                    action = exportedActivity.getAction();
                    break;
                }
            }
            if (action == null) continue;

            FilteredControlFlowGraph filteredControlFlowGraph = new FilteredControlFlowGraph(entry.getValue(), methodName, graphs, globalVariables);
            if (filteredControlFlowGraph.haveExtras()) {
                System.out.println(methodName);
                System.out.println(filteredControlFlowGraph);

                String fileName = "paths/" + filteredControlFlowGraph.getCompleteMethod() + "_paths.dot";
                CFGPathFinder pathFinder = new CFGPathFinder(filteredControlFlowGraph);
                pathFinder.generateDotFile(filteredControlFlowGraph.getFilteredCFG(), fileName, packageName, activityName, action);
            }
        }
    }

    /**
     * Configures Soot options for analyzing the given APK file.
     *
     * @param apkPath        The path to the APK file to be analyzed.
     * @param androidJarPath The path to the Android SDK JAR file corresponding to the APK's SDK version.
     */
    private static void setupSoot(String apkPath, String androidJarPath) {
        // Initialize Soot
        Options.v().set_android_jars(androidJarPath);
        Options.v().set_process_dir(List.of(apkPath));
        Options.v().set_whole_program(true);

        Options.v().set_allow_phantom_refs(true);
        Options.v().set_process_multiple_dex(true);
        Options.v().set_force_android_jar(androidJarPath);

        // Enable Dexpler for analyzing DEX files
        Options.v().set_src_prec(Options.src_prec_apk);
        Options.v().set_output_format(Options.output_format_jimple);
    }

    /**
     * Generates a mapping of method names to filtered control flow graphs (CFGs) for exported activities.
     *
     * @param exportedActivities A list of exported activities from the APK.
     * @param packageName        The name of the apk package.
     * @return A map where the keys are method identifiers in the format "ClassName.MethodName",
     * and the values are {@link ExceptionalUnitGraph} objects representing the control flow of the corresponding method.
     */
    private static Map<String, ExceptionalUnitGraph> getCFGs(List<ManifestParsing.Activity> exportedActivities, String packageName) {
        Map<String, ExceptionalUnitGraph> graphs = new HashMap<>();

        // Add a custom transformation to analyze methods for Intent-related operations
        PackManager.v().getPack("jtp").add(new Transform("jtp.intentAnalysis", new BodyTransformer() {
            @Override
            protected void internalTransform(Body body, String phase, Map<String, String> options) {
                // Perform the analysis on each method body
                SootMethod method = body.getMethod();
                String className = method.getDeclaringClass().getName(); // Get the class name

                // Check if the class is an exported activity
//                if (exportedActivities.contains(className))
                if (className.startsWith(packageName)) {
                    String substring = "(";
                    StringBuilder parameterTypes = new StringBuilder("(");
                    for (int i = 0; i < method.getParameterCount(); i++)
                        parameterTypes.append(method.getParameterType(i)).append(", ");
                    if (parameterTypes.length() > 1)
                        substring = parameterTypes.substring(0, parameterTypes.toString().length() - 2);
                    substring += ")";

                    graphs.put(className + "." + method.getName() + "-" + substring, new ExceptionalUnitGraph(body));
                }
            }
        }));

        // Run all Soot transformations
        PackManager.v().runPacks();
        PackManager.v().writeOutput();

        return graphs;
    }

    /**
     * Retrieves the global variables (static final, static non-final fields) in the specified package from the APK.
     * The method filters classes by the given package name and then checks for static final, static non-final fields
     * in those classes. For each global variable, the value is resolved (if possible) and added to a map.
     *
     * @param packageName The name of the package to filter classes by.
     * @return A map where the key is the name of the global variable and the value is its resolved value as a string.
     */
    private static Map<String, GlobalVariablesInfo> getGlobalVariables(String packageName) {
        Map<String, GlobalVariablesInfo> globalVariables = new HashMap<>();

        for (SootClass sc : Scene.v().getClasses()) {
            if (sc.getName().startsWith(packageName)) { // Filter by package name
                for (SootField field : sc.getFields()) {
                    if (field.isStatic() || !field.isFinal()) { // Example heuristic for global variables
                        String fieldName = field.getName();
                        String fieldValue = resolveFieldValue(field); // Placeholder for the value
                        String fieldType = field.getType().toString();

                        GlobalVariablesInfo info = new IntentAnalysis.GlobalVariablesInfo(fieldType, fieldValue);
                        globalVariables.put(fieldName, info);
                    }
                }
            }
        }

        return globalVariables;
    }

    /**
     * Resolves the value of a given field, if possible. This method first checks if the field has
     * a constant value (for static final variables) using a `ConstantValueTag`.
     * If no constant is found, it attempts to analyze the `<clinit>` (class initializer) method of
     * the field's declaring class to find where the field is assigned a value (e.g. for static variables).
     * The resolved value can be a constant or the string representation of the assigned value.
     *
     * @param field The field whose value is to be resolved.
     * @return A string representing the resolved value of the field. If the value can't be determined,
     * returns the field's signature.
     */
    private static String resolveFieldValue(SootField field) {
        // Check if the field has a constant value
        for (Tag tag : field.getTags()) {
            if (tag instanceof ConstantValueTag) {
                ConstantValueTag constantTag = (ConstantValueTag) tag;
                return constantTag.getConstant().toString();
            }
        }

        // If no ConstantValueTag, attempt to analyze the `<clinit>` method
        SootClass declaringClass = field.getDeclaringClass();
        if (declaringClass.declaresMethodByName("<clinit>")) {
            SootMethod clinit = declaringClass.getMethodByName("<clinit>");
            Body body = clinit.retrieveActiveBody(); 

            // Look for statements assigning a value to this field
            for (Unit unit : body.getUnits()) {
                if (unit instanceof AssignStmt) {
                    AssignStmt assignStmt = (AssignStmt) unit;
                    // Check if this statement assigns a value to the field
                    if (assignStmt.getLeftOp() instanceof StaticFieldRef) {
                        StaticFieldRef fieldRef = (StaticFieldRef) assignStmt.getLeftOp();
                        if (fieldRef.getField().equals(field)) {
                            // Retrieve the right-hand side value
                            Value rhs = assignStmt.getRightOp();
                            // If the value is a constant, return it
                            if (rhs instanceof IntConstant) {
                                return String.valueOf(((IntConstant) rhs).value);
                            } else if (rhs instanceof StringConstant) {
                                return ((StringConstant) rhs).value;
                            } else if (rhs instanceof FloatConstant) {
                                return String.valueOf(((FloatConstant) rhs).value);
                            } else if (rhs instanceof DoubleConstant) {
                                return String.valueOf(((DoubleConstant) rhs).value);
                            } else if (rhs instanceof LongConstant) {
                                return String.valueOf(((LongConstant) rhs).value);
                            } else {
                                // For complex RHS, return its string representation
                                return rhs.toString();
                            }
                        }
                    }
                }
            }
        }

        // Fallback: return the field's signature
        return field.getSignature();
    }
}