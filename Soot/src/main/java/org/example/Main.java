package org.example;

import jas.Pair;
import soot.*;
import soot.options.Options;
import soot.util.*;

import java.util.Set;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {

    static Set<String> androidPackages = new HashSet<>();
    static Set<String> appPackages = new HashSet<>();
    static List<String> appClasses = new ArrayList<>();

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

        getAppClasses();

        for (String appClass : appClasses)
            printIntentExtras(appClass);

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

    private static void getAppClasses() {
        Scene.v().loadNecessaryClasses();
        // Ottieni tutte le classi caricate
        Chain<SootClass> classes = Scene.v().getApplicationClasses();

        // Stampa il nome del package di ciascuna classe
        for (SootClass sc : classes) {
            String packageName = sc.getPackageName();
//            System.out.println("Classe: " + sc.getName() + " - Package: " + packageName);
            androidPackages.add(packageName);
        }

        System.out.println("Packages:");
        for (String pkg : androidPackages) {
            if (!(pkg.contains("android") || pkg.contains("java") || pkg.contains("kotlin") || pkg.contains("google")
                    || pkg.contains("jetbrains") || pkg.contains("intellij")
                    || pkg.contains("_COROUTINE") || pkg.contains(".ui.theme"))) {
                appPackages.add(pkg);
                System.out.println(pkg);
            }
        }

        System.out.println("\nClasses:");
        for (SootClass sc : classes) {
            String packageName = sc.getPackageName();
            String[] parts = sc.getName().split("\\.");
            String className = parts[parts.length - 1];
            if (appPackages.contains(packageName)) {
                if (!(className.startsWith("R$") || className.equals("R"))) {
                    appClasses.add(packageName + "." + className);
                    System.out.println(packageName + "." + className);
                }

            }
        }
    }

    private static void printIntentExtras(String className) {
        String regex = "get\\w*Extra\\(java\\.lang\\.String";
        Pattern pattern = Pattern.compile(regex);
        List<Pair<String, String>> intent = new ArrayList<>();

        Scene.v().loadNecessaryClasses();

        // Retrieve the specified class
        SootClass sootClass = Scene.v().getSootClass(className);

        // Print the class name
        System.out.println("\nClass: " + sootClass.getName());

        // Print details of the methods in the class
        System.out.println("Methods:");
        for (SootMethod method : sootClass.getMethods()) {
            System.out.println(" - " + method.getName());
            if (method.isConcrete()) {
                String[] lines = method.retrieveActiveBody().toString().split("\n");
                // If the method is concrete, print its body
//                System.out.println("   Body: "  );
                for (String line : lines) {
                    if (line.contains("android.content.Intent") && pattern.matcher(line).find()) {
//                        System.out.println(line);
                        intent.add(extractIntentExtras(line));
                    }
                }
            } else {
                // If the method is not concrete, indicate it
                System.out.println("   Method is not concrete.");
            }
        }

        if (!intent.isEmpty()) {
            System.out.println("Extras in the intent:");
            for (Pair<String, String> pair : intent) {
                System.out.println("Key: " + pair.getO1() + "; Type: " + pair.getO2());
            }
        }
    }

    public static Pair<String, String> extractIntentExtras(String line) {
//        Matcher matcher = Pattern.compile("\\(\"([^\"]+)\".*?,\\s*(\\d+)\\)").matcher(line);

        String regex = "virtualinvoke .*<.*: (\\w+) .*get\\w*Extra\\(.*\\)>.*\\(\"([^\"]+)\"";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(line);

        if (matcher.find()) {
            String type = matcher.group(1);
            String key = matcher.group(2);
            return new Pair<>(key, type);
        }
        return null;
    }

}
