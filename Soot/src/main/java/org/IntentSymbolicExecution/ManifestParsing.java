package org.IntentSymbolicExecution;

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
import java.util.Map;

/**
 * A utility class for parsing the AndroidManifest.xml file of an APK.
 * <p>
 * This class:
 * - Extracts the AndroidManifest.xml from an APK using apktool.
 * - Parses the manifest to extract package details, SDK version, and activities.
 * - Provides methods to query the manifest for specific details, such as exported activities.
 */
public class ManifestParsing {

    /**
     * The compile SDK version specified in the AndroidManifest.xml.
     */
    private static int SDK_Version;

    /**
     * The package name of the application, as defined in the AndroidManifest.xml.
     */
    private static String PackageName;

    /**
     * A map of all activities, activity name, action; defined in the AndroidManifest.xml.
     */
    private static List<Map.Entry<String, String>> Activities;

    /**
     * A list of exported activities (android:exported="true") in the AndroidManifest.xml.
     */
    private static List<Map.Entry<String, String>> ExportedActivities;

    /**
     * Gets the compile SDK version of the application.
     *
     * @return the compile SDK version.
     */
    public int getSDK_Version() {
        return SDK_Version;
    }

    /**
     * Gets the package name of the application.
     *
     * @return the package name.
     */
    public String getPackageName() {
        return PackageName;
    }

    /**
     * Gets the list of all activities in the AndroidManifest.xml.
     *
     * @return a list of activity name and action.
     */
    public List<Map.Entry<String, String>> getActivities() {
        return Activities;
    }

    /**
     * Gets the list of exported activities (android:exported="true") in the AndroidManifest.xml.
     *
     * @return a list of exported activity names.
     */
    public List<Map.Entry<String, String>> getExportedActivities() {
        return ExportedActivities;
    }

    /**
     * Constructor for the ManifestParsing class.
     *
     * @param apkPath the path to the APK file.
     */
    public ManifestParsing(String apkPath) {
        String appName = apkPath.substring(apkPath.lastIndexOf('/') + 1).split("\\.")[0];

        Document manifest = extractManifest(apkPath, appName);
        queryManifest(manifest);

        // Delete the extracted directory after returning the Document
        deleteDir(String.format("manifest/%s", appName));
    }

    /**
     * Extracts the AndroidManifest.xml from the specified APK using apktool.
     *
     * @param apkPath the path to the APK file.
     * @param appName the name of the application, derived from the APK file name.
     * @return a Document object representing the parsed AndroidManifest.xml, or null if extraction fails.
     */
    public static Document extractManifest(String apkPath, String appName) {
        String command = String.format("apktool d %s -o manifest/%s -f --no-src", apkPath, appName);

        // Use ProcessBuilder to execute the command
        ProcessBuilder processBuilder = new ProcessBuilder(command.split(" "));
        processBuilder.redirectErrorStream(true);

        System.out.println("Starting execution of apktool...");
        try {
            Process process = processBuilder.start();
//            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
//            String line;
//            while ((line = reader.readLine()) != null) {
//                System.out.println(line);
//            }

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

                    // Return the parsed Document object
                    return manifest;
                } else {
                    System.out.println("AndroidManifest.xml not found.");
                }

            } else {
                System.out.println("apktool command failed with exit code: " + exitCode);
            }

        } catch (IOException | InterruptedException | ParserConfigurationException | SAXException e) {
            e.printStackTrace();
        }

        // Return null if there's an error or manifest is not found
        return null;
    }

    /**
     * Deletes a directory and its contents using a system command.
     *
     * @param path the path to the directory to be deleted.
     */
    public static void deleteDir(String path) {
        String command = String.format("rm -rf %s", path);
        ProcessBuilder processBuilder = new ProcessBuilder(command.split(" "));
        processBuilder.redirectErrorStream(true);

        try {
            Process process = processBuilder.start();
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Queries the provided AndroidManifest.xml document and extracts relevant details.
     *
     * @param manifest the Document object representing the AndroidManifest.xml.
     */
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
        System.out.println();
        System.out.println("Package: " + PackageName);
        System.out.println("Compile SDK Version: " + SDK_Version);
        System.out.println("Activities: " + Activities);
        System.out.println("Exported Activities: " + ExportedActivities);
        System.out.println();
    }

    public static List<Map.Entry<String, String>> getActivities(Document manifest, String packageName, Boolean onlyExportedActivities) {
        List<Map.Entry<String, String>> activities = new ArrayList<>();

        // Get all <activity> elements within the <application> tag
        NodeList activityNodes = manifest.getElementsByTagName("activity");

        for (int i = 0; i < activityNodes.getLength(); i++) {
            Node node = activityNodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element activityElement = (Element) node;
                String activityName = activityElement.getAttribute("android:name");

                // Ensure activity belongs to the correct package
                if (!activityName.contains(packageName)) {
                    continue;
                }

                // Extract the exported attribute
                String exported = activityElement.getAttribute("android:exported");

                // Get the intent-filters and extract actions
                List<String> actions = getIntentActions(activityElement);

                // If onlyExportedActivities is true, filter out non-exported activities
                if (onlyExportedActivities && !"true".equals(exported)) {
                    continue;
                }

                // Store each activity with its actions
                for (String action : actions) {
                    activities.add(Map.entry(activityName, action));
                }
            }
        }

        return activities;
    }

    /**
     * Extracts intent-filter actions from an activity element.
     *
     * @param activityElement the activity element.
     * @return a list of action names.
     */
    private static List<String> getIntentActions(Element activityElement) {
        List<String> actions = new ArrayList<>();

        // Get all <intent-filter> elements inside the activity
        NodeList intentFilters = activityElement.getElementsByTagName("intent-filter");

        for (int i = 0; i < intentFilters.getLength(); i++) {
            Node intentFilterNode = intentFilters.item(i);
            if (intentFilterNode.getNodeType() == Node.ELEMENT_NODE) {
                Element intentFilterElement = (Element) intentFilterNode;

                // Get all <action> elements inside the intent-filter
                NodeList actionNodes = intentFilterElement.getElementsByTagName("action");
                for (int j = 0; j < actionNodes.getLength(); j++) {
                    Element actionElement = (Element) actionNodes.item(j);
                    String actionName = actionElement.getAttribute("android:name");
                    actions.add(actionName);
                }
            }
        }

        return actions;
    }
}
