package org.example;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {

    private static final String APPS_DIR = "Applications_to_analise";
    static List<String> appsList = new ArrayList<>();

    public static void main(String[] args) {

        if (args.length == 1) {
            new IntentAnalysis(args[0]); // apk dir from args
            return;
        }

        createFolderIfNotExists();
        printAppsList();
        userInput();

    }

    private static void userInput() {
        // Wait for user input
        System.out.print("Choose an option: ");
        Scanner scanner = new Scanner(System.in);
        String choice = scanner.nextLine().toLowerCase();

        // Handle user input
        switch (choice) {
            case "r":
                printAppsList();
                userInput();
                break;

            case "e":
                System.out.println("Exiting...");
                scanner.close();
                System.exit(0);
                break;

            default:
                // Check if the choice is a valid number
                try {
                    int selectedIndex = Integer.parseInt(choice) - 1;

                    if (selectedIndex >= 0 && selectedIndex < appsList.size()) {
                        String selectedApk = appsList.get(selectedIndex);
                        System.out.println("You selected: " + selectedApk);
                        new IntentAnalysis(APPS_DIR + "/" + selectedApk);
                    } else {
                        System.out.println("Invalid number. Please select a valid APK.");
                    }

                } catch (NumberFormatException e) {
                    System.out.println("Invalid input.");
                }
                break;
        }
    }

    private static void printAppsList() {
        Path appsDirPath = Paths.get(APPS_DIR);


        System.out.println("[r]\tfor Reload");
        System.out.println("[e]\tfor Exit");
        System.out.println("\t- - - - -");


        AtomicInteger index = new AtomicInteger(1); // Start from 1 for the first file
        try {
            // Walk through the directory and filter for .apk files
            Files.walk(appsDirPath)
                    .filter(path -> path.toString().endsWith(".apk"))
                    .forEach(path -> {
                        // Get the file name (appName.apk)
                        String appName = path.getFileName().toString();
                        // Print in the specified format [index] \tab appName.apk
                        System.out.println("[" + index + "]\t" + appName);
                        index.getAndIncrement(); // Increment the index for the next file
                        appsList.add(appName);
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public static void createFolderIfNotExists() {
        Path destinationPath = Paths.get(APPS_DIR);
        // Check if the file already exists
        if (!Files.exists(destinationPath)) {
            try {
                // Create the directory if it doesn't exist
                Files.createDirectories(destinationPath);
                System.out.println("Directory created: " + destinationPath);
            } catch (IOException e) {
                System.err.println("Failed to create directory: " + e.getMessage());
            }
        }
    }
}
