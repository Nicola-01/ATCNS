package org.IntentSymbolicExecution;

import android.app.Activity;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
     * A map of all activities, activity name, action, adn exported; defined in the AndroidManifest.xml.
     */
    private static List<Activity> Activities;

    /**
     * A list of exported activities (android:exported="true") in the AndroidManifest.xml.
     */
    private static final List<Activity> ExportedActivities = new ArrayList<>();

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
    public List<Activity> getActivities() {
        return Activities;
    }

    /**
     * Gets the list of exported activities (android:exported="true") in the AndroidManifest.xml.
     *
     * @return a list of exported activity names.
     */
    public List<Activity> getExportedActivities() {
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
        queryManifest(manifest, appName);

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
    public static void queryManifest(Document manifest, String appName) {
        if (manifest == null) {
            System.out.println("Manifest is null.");
            return;
        }

        // Get the root element of the manifest (the <manifest> tag)
        Element root = manifest.getDocumentElement();

        // Extract specific attributes from the <manifest> element
        PackageName = root.getAttribute("package");

        SDK_Version = getTargetSDKVersion(appName);
        Activities = getActivities(manifest);

        for (Activity activity : Activities)
            if (activity.isExported())
                ExportedActivities.add(activity);

        // Print the extracted values
        System.out.println();
        System.out.println("Package: " + PackageName);
        System.out.println("Compile SDK Version: " + SDK_Version);
        System.out.println("Activities: " + Activities);
        System.out.println("Exported Activities: " + ExportedActivities);
        System.out.println();
    }

    private static int getTargetSDKVersion(String appName) {
        try {
            String command = String.format("cat manifest/%s/apktool.yml | grep targetSdkVersion", appName);
            ProcessBuilder processBuilder = new ProcessBuilder(command.split(" "));
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            String regex = "targetSdkVersion:\\s*'(\\d+)'";
            Pattern pattern = Pattern.compile(regex);
            while ((line = reader.readLine()) != null) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    return Integer.parseInt(matcher.group(1));
                }
            }
        } catch (IOException ignored) {
        }
        return 34;
    }

    private static List<Activity> getActivities(Document manifest) {
        List<Activity> activities = new ArrayList<>();

        // Get all <activity> elements within the <application> tag
        NodeList activityNodes = manifest.getElementsByTagName("activity");

        for (int i = 0; i < activityNodes.getLength(); i++) {
            Node node = activityNodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element activityElement = (Element) node;
                String activityName = activityElement.getAttribute("android:name");
                if (activityName.startsWith("."))
                    activityName = String.format("%s%s", PackageName, activityName);

                // Ensure activity belongs to the correct package
//                if (!activityName.contains(packageName)) continue;

                // Extract the exported attribute
                String exported = activityElement.getAttribute("android:exported");

                boolean isExported;
                boolean hasIntentFilter = activityElement.getElementsByTagName("intent-filter").getLength() > 0;  // True if there is an intent-filter, false otherwise

                // If SDK >= 31, the "exported" attribute must be explicitly set in the manifest
                if (SDK_Version >= 31) {
                    isExported = Boolean.parseBoolean(exported);
                } else {
                    // For SDK < 31, check if the activity has an intent-filter
                    if (exported.isEmpty()) // exported non specified, if there is a intentFilter -> activity exported
                        isExported = hasIntentFilter;
                    else // if exported specified, get that value
                        isExported = Boolean.parseBoolean(exported);
                }

                // Get the intent-filters and extract actions
                List<String> actions = getIntentActions(activityElement);

//                // If onlyExportedActivities is true, filter out non-exported activities
//                if (onlyExportedActivities && !"true".equals(exported))
//                    continue;

                if (actions.isEmpty())
                    activities.add(new Activity(activityName, "", isExported));

                // Store each activity with its actions
                for (String action : actions)
                    activities.add(new Activity(activityName, action, isExported));
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

    public static class Activity {
        private final String name;
        private final String action;
        private final Boolean exported;

        public Activity(String name, String action, Boolean exported) {
            this.name = name;
            this.action = action;
            this.exported = exported;
        }

        public String getName() {
            return name;
        }

        public String getAction() {
            return action;
        }

        public Boolean isExported() {
            return exported;
        }

        @Override
        public String toString() {
            return "Activity{" +
                    "name='" + name + '\'' +
                    ", action='" + action + '\'' +
                    ", exported=" + exported +
                    '}';
        }
    }
}
