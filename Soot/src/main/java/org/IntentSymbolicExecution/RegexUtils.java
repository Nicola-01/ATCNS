package org.IntentSymbolicExecution;

import java.util.regex.Pattern;


/**
 * Utility class for regex patterns used in Intent and Bundle analysis.
 * */
public class RegexUtils {
    /** A regular expression to identify calls to getExtra methods in Intent objects. */
    private static final String regexIntentExtra = "android.content.Intent: ([\\w.]+) get\\w*Extra\\(java\\.lang\\.String";
    public static final Pattern patternIntentExtra = Pattern.compile(regexIntentExtra);

    /** A regular expression to identify calls to get methods in Bundle objects. */
    private static final String regexBundleExtra = "android.os.Bundle: ([\\w.]+) get\\w*\\(java\\.lang\\.String\\)";
    public static final Pattern patternBundleExtra = Pattern.compile(regexBundleExtra);

    /** A regular expression to identify calls to the getAction method. */
    private static final String regexGetAction = "getAction\\(\\)";
    public static final Pattern patterGetAction = Pattern.compile(regexGetAction);

    private static final String regexCallClass = "<(?<class>[^:]+):\\s[^ ]+\\s(?<method>[^()]+)\\((?<parameters>[^)]*)\\)>\\((?<arguments>[^)]*)\\)";
    public static final Pattern patternCallClass = Pattern.compile(regexCallClass);

    // Private constructor to prevent instantiation
    private RegexUtils() {
    }
}