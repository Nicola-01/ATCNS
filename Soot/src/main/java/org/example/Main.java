package org.example;

import soot.*;
import soot.options.Options;
import soot.toolkits.graph.*;
import soot.util.*;

import java.util.*;

public class Main {

    public static void main(String[] args) {
        // Ensure that an APK file is provided as an argument
        if (args.length < 2) {
            System.out.println("Usage: java -jar ApkExecutionTree.jar <path-to-apk> <path-to-android-jar>");
            return;
        }

        String apkPath = args[0];
        String androidJar = args[1];

        // Set up Soot options for analyzing APKs
        setupSoot(apkPath, androidJar);

        // Load all classes and print them
//        printAllClasses();

        // Generate intent analysis
//        generateIntentAnalysis();
        test();
    }

    private static void setupSoot(String apkPath, String androidJar) {
        Options.v().set_src_prec(Options.src_prec_apk);
        Options.v().set_android_jars(androidJar);
        Options.v().set_soot_classpath(androidJar);
        Options.v().set_process_dir(Collections.singletonList(apkPath));
        Options.v().set_allow_phantom_refs(true);
        Options.v().set_whole_program(true);
        Options.v().set_output_format(Options.output_format_none);

        // Set up Dexpler to analyze DEX
        Options.v().set_src_prec(Options.src_prec_apk);
        Options.v().set_process_multiple_dex(true); // Handle multi-DEX APKs

    }

    private static void printAllClasses() {
        Scene.v().loadNecessaryClasses();

        System.out.println("Loaded classes:");
        for (SootClass sootClass : Scene.v().getClasses()) {
            System.out.println(sootClass.getName());
        }
    }

    private static void generateIntentAnalysis() {
        // Load the necessary classes
        Scene.v().loadNecessaryClasses();

        // Analyze all the loaded classes
        for (SootClass sootClass : Scene.v().getClasses()) {
            // Analyze only the classes that contain activities (Activity)
            System.out.println(sootClass.getName());
            if (sootClass.getName().startsWith("com.example.primarychecker.")) {
                analyzeActivityForIntents(sootClass);
            }
        }
    }

    private static void test() {
        Scene.v().loadNecessaryClasses();

        // Retrieve the specified class
        SootClass sootClass = Scene.v().getSootClass("com.example.primarychecker.PrimaryChecker");

        // Print the class name
        System.out.println("Class: " + sootClass.getName());

        // Print details of the methods in the class
        System.out.println("Methods:");
        for (SootMethod method : sootClass.getMethods()) {
            System.out.println(" - " + method.getName());
            if (method.isConcrete()) {
                // If the method is concrete, print its body
                System.out.println("   Body: " + method.retrieveActiveBody());
            } else {
                // If the method is not concrete, indicate it
                System.out.println("   Method is not concrete.");
            }
        }
    }

    private static void analyzeActivityForIntents(SootClass sootClass) {
        // Analyze the methods of the class to find those that invoke getIntent() and getIntExtra()
        for (SootMethod method : sootClass.getMethods()) {
            // Ignore the constructor (<init>) and methods without an active body
            if (method.getName().equals("<init>") || !method.hasActiveBody()) {
                continue;
            }

            // Analyze the method body
            for (Unit unit : method.getActiveBody().getUnits()) {
                // Look for calls to the getIntent() method
                if (unit.toString().contains("getIntent")) {
                    System.out.println("Found getIntent() in: " + sootClass.getName() + " in method " + method.getName());
                    analyzeIntentExtras(unit);
                }
            }
        }
    }

    private static void analyzeIntentExtras(Unit unit) {
        // Look for calls to getExtra() on Intent objects
        if (unit.toString().contains("getIntExtra")) {
            System.out.println("Found getIntExtra usage in: " + unit.toString());
        }
        if (unit.toString().contains("getExtras")) {
            System.out.println("Found getExtras() usage in: " + unit.toString());
        }
    }
}
