package org.example;

import soot.*;
import soot.options.Options;

public class Main {
    public static void main(String[] args) {

        //prefer Android APK files// -src-prec apk
        Options.v().set_src_prec(Options.src_prec_apk);

        //output as APK, too//-f J
        Options.v().set_output_format(Options.output_format_dex);

        // resolve the PrintStream and System soot-classes
        Scene.v().addBasicClass("java.io.PrintStream", SootClass.SIGNATURES);
        Scene.v().addBasicClass("java.lang.System", SootClass.SIGNATURES);
    }
}