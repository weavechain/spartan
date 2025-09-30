package com.weavechain.zk.spartan;

public class Spartan {

    public static void init() {
        try {
            String libname = "blst";
            String os = System.getProperty("os.name");
            if (os != null && os.toLowerCase(java.util.Locale.US).contains("windows")) {
                libname = "lib" + libname;
            }
            System.loadLibrary(libname);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not initialize BLST library", e);
        }
    }
}
