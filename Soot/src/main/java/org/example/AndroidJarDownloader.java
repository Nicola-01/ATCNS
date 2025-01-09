package org.example;

import java.io.*;
import java.net.*;
import java.nio.file.*;

public class AndroidJarDownloader {

    private static final String BASE_URL = "https://github.com/Sable/android-platforms/raw/master";

    private static String androidJarsPath;

    public AndroidJarDownloader(int SDK_Version) {
        // Path where you want to store the JAR file
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

    public String getAndroidJarsPath() {
        return androidJarsPath;
    }

    public static void downloadJar(int sdkVersion, Path destinationPath) {
        try {
            // Construct the URL for the JAR file based on the SDK version
            String urlString = BASE_URL + "/android-" + sdkVersion + "/android.jar";
            URL url = new URL(urlString);

            // Open a connection and set up input/output streams
            InputStream inputStream = url.openStream();
            Files.createDirectories(destinationPath.getParent());  // Ensure the destination directory exists
            OutputStream outputStream = new FileOutputStream(destinationPath.toFile());

            // Read and write data in chunks
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            // Close streams after downloading
            inputStream.close();
            outputStream.close();

            System.out.println("Download completed: " + destinationPath);

        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Failed to download JAR file.");
        }
    }
}
