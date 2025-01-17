package org.IntentSymbolicExecution;

import java.io.*;
import java.net.*;
import java.nio.file.*;

/**
 * A utility class to download the Android SDK JAR file for a specified SDK version.
 * <p>
 * The JAR files are retrieved from the Sable Android Platforms repository on GitHub.
 * If the requested JAR file already exists locally, it will not be downloaded again.
 * This class ensures the JAR files are organized and stored in a predefined directory.
 */
public class AndroidJarDownloader {

    /**
     * The base URL of the repository where the Android SDK JAR files are hosted.
     */
    private static final String GITHUB_ANDROID_PLATFORMS = "https://github.com/Sable/android-platforms/raw/master";

    /**
     * Path to the downloaded Android SDK JAR file.
     */
    private static String androidJarsPath;

    /**
     * Constructs an AndroidJarDownloader instance.
     *
     * @param SDK_Version The Android SDK version for which the JAR file is required.
     */
    public AndroidJarDownloader(int SDK_Version) {
        String destinationDir = "android_jars";
        String jarFileName = "android-" + SDK_Version + ".jar";
        Path destinationPath = Paths.get(destinationDir, jarFileName);

        androidJarsPath = destinationPath.toString();

        // Check if the file already exists
        if (Files.exists(destinationPath)) {
            System.out.println("File already downloaded: " + destinationPath);
        } else {
            // If not, download the JAR
            System.out.println("Downloading Android SDK JAR for SDK Version " + SDK_Version);
            downloadJar(SDK_Version, destinationPath);
        }
    }

    /**
     * Gets the path to the downloaded Android SDK JAR file.
     *
     * @return The local path to the JAR file.
     */
    public String getAndroidJarsPath() {
        return androidJarsPath;
    }

    /**
     * Downloads the JAR file for the specified SDK version from the repository.
     *
     * @param sdkVersion      The Android SDK version for which the JAR file is required.
     * @param destinationPath The local file path where the downloaded JAR file will be stored.
     */
    public static void downloadJar(int sdkVersion, Path destinationPath) {
        try {
            // Construct the URL for the JAR file
            String urlString = GITHUB_ANDROID_PLATFORMS + "/android-" + sdkVersion + "/android.jar";
            URL url = new URL(urlString);

            // Open a connection and set up input/output streams
            InputStream inputStream = url.openStream();
            Files.createDirectories(destinationPath.getParent());
            OutputStream outputStream = new FileOutputStream(destinationPath.toFile());

            // Read and write data in chunks
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            // Close the streams
            inputStream.close();
            outputStream.close();

            System.out.println("Download completed: " + destinationPath);

        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Failed to download JAR file.");
        }
    }
}
