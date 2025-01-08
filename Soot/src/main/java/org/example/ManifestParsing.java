package org.example;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class ManifestParsing {

    private static int SDK_Version;
    private static String PackageName;
    private static List<String> Activities;
    private static List<String> ExportedActivities;

    public static int getSDK_Version() {
        return SDK_Version;
    }

    public static String getPackageName() {
        return PackageName;
    }

    public static List<String> getActivities() {
        return Activities;
    }

    public static List<String> getExportedActivities() {
        return ExportedActivities;
    }

    public ManifestParsing(String apkPath) {
        String appName = apkPath.substring(apkPath.lastIndexOf('/') + 1).split("\\.")[0];

        Document manifest = extractManifest(apkPath, appName);
        queryManifest(manifest);
    }

    public static Document extractManifest(String apkPath, String appName) {
        // Define the command to execute apktool and extract the APK
        String command = String.format("apktool d %s -o manifest/%s -f", apkPath, appName);

        // Use ProcessBuilder to execute the command
        ProcessBuilder processBuilder = new ProcessBuilder(command.split(" "));
        processBuilder.redirectErrorStream(true);

        try {
            // Start the process to decode the APK
            Process process = processBuilder.start();

            // Read the output of the command
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }

            // Wait for the process to finish
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                System.out.println("apktool command executed successfully.");

                // After successful decoding, read the AndroidManifest.xml
                File manifestFile = new File(String.format("manifest/%s/AndroidManifest.xml", appName));

                if (manifestFile.exists()) {
                    // Parse the XML file and return as a Document
                    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                    DocumentBuilder builder = factory.newDocumentBuilder();
                    Document manifest = builder.parse(new InputSource(new FileInputStream(manifestFile)));

                    System.out.println("AndroidManifest.xml successfully parsed.");

                    // Optionally, delete the extracted directory after returning the Document
                    deleteDir(String.format("manifest/%s", appName));

                    // Return the parsed Document object
                    return manifest;
                } else {
                    System.out.println("AndroidManifest.xml not found.");
                }

            } else {
                System.out.println("apktool command failed with exit code: " + exitCode);
            }

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        } catch (SAXException e) {
            throw new RuntimeException(e);
        }

        // Return null if there's an error or manifest is not found
        return null;
    }

    // Simple method to delete a directory and its contents
    public static void deleteDir(String path) {
        String command = String.format("rm -rf %s", path);

        // Use ProcessBuilder to execute the command
        ProcessBuilder processBuilder = new ProcessBuilder(command.split(" "));

        processBuilder.redirectErrorStream(true);

        try {
            // Start the process
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                System.out.println("deleteDir executed successfully.");
            } else {
                System.out.println("deleteDir failed with exit code: " + exitCode);
            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    // Method to extract specific values from the parsed manifest
    public static void queryManifest(Document manifest) {
        if (manifest == null) {
            System.out.println("Manifest is null.");
            return;
        }

        // Get the root element of the manifest (the <manifest> tag)
        Element root = manifest.getDocumentElement();

        // Extract specific attributes from the <manifest> element
        PackageName = root.getAttribute("package");
        SDK_Version = Integer.parseInt(root.getAttribute("android:compileSdkVersion"));
        Activities = getActivities(manifest, PackageName, false);
        ExportedActivities = getActivities(manifest, PackageName, true);


        // Print the extracted values
        System.out.println("Package: " + PackageName);
        System.out.println("Compile SDK Version: " + SDK_Version);
        System.out.println("Activities: " + Activities);
        System.out.println("Exported Activities: " + ExportedActivities);
    }

    // Method to get a list of all activities from the manifest
    public static List<String> getActivities(Document manifest, String packageName, Boolean onlyExportedActivities) {
        List<String> activities = new ArrayList<>();

        // Get all <activity> elements within the <application> tag
        NodeList activityNodes = manifest.getElementsByTagName("activity");

        for (int i = 0; i < activityNodes.getLength(); i++) {
            Node node = activityNodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element activityElement = (Element) node;
                String activityName = activityElement.getAttribute("android:name");

                // Skip activities that are not in the correct package
                if(!activityName.contains(packageName))
                    continue;

                String exported = activityElement.getAttribute("android:exported");

                // If android:exported="true", add it to the list
                if (onlyExportedActivities) {
                    if (exported.equals("true"))
                        activities.add(activityName);
                } else
                    activities.add(activityName);
            }
        }

        return activities;
    }
}
