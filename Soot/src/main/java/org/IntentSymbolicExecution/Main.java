package org.IntentSymbolicExecution;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
//import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Main class for the Intent Symbolic Execution tool.
 * <p>
 * This class allows users to select an APK file from a specified directory for analysis.
 */
public class Main {

    /**
     * Directory containing the APK files to be analyzed.
     */
    private static final String APPS_DIR = "Applications_to_analise";

    /**
     * List to store the names of APK files found in the {@link #APPS_DIR}.
     */
    private static final List<String> appsList = new ArrayList<>();

    /**
     * The entry point of the application.
     * <p>
     * This method checks for command-line arguments to directly analyze an APK.
     * If no arguments are provided, it initializes the application by:
     * - Ensuring the applications directory exists.
     * - Printing the list of APKs available for analysis.
     * - Waiting for user input to select an APK, reload the list, or exit.
     *
     * @param args Command-line arguments, where the first argument can be the path to an APK for immediate analysis.
     */
    public static void main(String[] args) {
        // If an APK path is provided via command-line arguments, analyze it directly
        if (args.length == 0) {
            try {
                Files.createDirectories(Paths.get(APPS_DIR));
            } catch (IOException e) {
                System.err.println("Failed to create directory: " + e.getMessage());
            }

            printAppsList();
            userInput();
        }
        if (args.length == 1) {
            new IntentAnalysis(args[0], null); // apk path from args
        }
        else if (args.length == 2) {
            new IntentAnalysis(args[0], args[1]); // apk path from args
        }
        else {
            System.err.println("Invalid number of arguments: " + args.length);
        }
    }

    /**
     * Handles user input for selecting APK files, reloading the list, or exiting the application.
     */
    private static void userInput() {
        // Wait for user input
        System.out.print("Choose an option: ");
        Scanner scanner = new Scanner(System.in);
        String choice = scanner.nextLine().toLowerCase();

        // Handle user input
        switch (choice) {
            case "r": // Reload the APK list
                printAppsList();
                break;

            case "e": // Exit the application
                System.out.println("Exiting...");
                scanner.close();
                System.exit(0);
                break;

            default: // Handle APK selection by number
                try {
                    // Parse the user input as an integer
                    int selectedIndex = Integer.parseInt(choice) - 1;

                    // Ensure the selection is within the valid range
                    if (selectedIndex >= 0 && selectedIndex < appsList.size()) {
                        String selectedApk = appsList.get(selectedIndex);
                        System.out.println("You selected: " + selectedApk);
                        new IntentAnalysis(APPS_DIR + "/" + selectedApk, null);
                        scanner.close();
                        System.exit(0);
                    } else {
                        System.out.println("Invalid number. Please select a valid APK.");
                    }

                } catch (NumberFormatException e) {
                    System.out.println("Invalid input.");
                }
                break;
        }
        // Recursively prompt for input
        userInput();
    }

    /**
     * Prints the list of APK files in the specified directory.
     */
    private static void printAppsList() {
        File appsDir = new File(APPS_DIR);

        System.out.println("[r]\tfor Reload");
        System.out.println("[e]\tfor Exit");
        System.out.println("\t- - - - -");

        File[] apkFiles = appsDir.listFiles((dir, name) -> name.endsWith(".apk"));

        if (apkFiles != null) {
            int len = String.valueOf(apkFiles.length).length();

            for (int i = 0; i < apkFiles.length; i++) {
                String apkName = apkFiles[i].getName();
                appsList.add(apkName);
                // Format the index with leading zeros
                String format = "%0" + len + "d";
                String formattedIndex = String.format(format, i + 1);

                // Print with tab distance adjusted
                System.out.printf("[%s]  %s\n", formattedIndex, apkName);
            }
        }

    }
}
