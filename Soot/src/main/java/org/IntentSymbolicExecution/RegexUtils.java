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

//    private static final String regexCallClass = "<(?<class>[^:]+):\\s[^ ]+\\s(?<method>[^()]+)\\((?<parameters>[^)]*)\\)>\\((?<arguments>[^)]*)\\)";
    private static final String regexCallClass = "^(?:(?<assignation>\\w+)\\s*=\\s*)?(?<invoke>\\w+)\\s+<(?<class>[^:]+):\\s[^ ]+\\s(?<method>[^()]+)\\((?<argumentType>[^)]*)\\)>\\((?<arguments>[^)]*)\\)";
//    private static final String regexCallClass2= "(\\$[a-zA-Z0-9]+)\\s=\\svirtualinvoke\\s+(\\$[a-zA-Z0-9]+)\\.\\<([^:]+):\\s*([a-zA-Z0-9]+)\\s+([a-zA-Z0-9]+)\\(([^)]+)\\)\\>\\(([^)]+)\\)";;
    public static final Pattern patternCallClass = Pattern.compile(regexCallClass);

//    private static final String regexEquals = "^(?<assignation>\\$?\\w+)\\s*=\\s*\\w+\\s+(?<object>\\$?\\w+)\\.<java\\.lang\\.String:\\s*boolean\\s*equals\\(java\\.lang\\.Object\\)\\>\\((?<argument>.*)\\)$";
    private static final String methodCallRegex =
        "^(?:(?<assignation>\\$?\\w+)\\s*=\\s*)?(?<invoke>\\w+)\\s+((?<object>\\$?\\w+)\\.)?\\<(?<objectType>[^:]+):\\s*(?<returnedType>[^:]+)\\s+(?<method>\\w+)\\((?<argumentType>.*?)\\)\\>\\((?<argument>.*?)\\)$"
        ;
    public static final Pattern patternEquals = Pattern.compile(methodCallRegex);

    // Private constructor to prevent instantiation
    private RegexUtils() {
    }
}