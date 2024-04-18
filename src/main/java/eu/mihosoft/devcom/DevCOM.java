package eu.mihosoft.devcom;

import org.tinylog.Logger;

public final class DevCOM {

    private final static String TAG = "eu.mihosoft.devcom:devcom";
    private static boolean initialized = false;

    private DevCOM() {
        throw new AssertionError("Don't instantiate me");
    }


    /**
     * Initializes the library. This method must be called before using any other class of this library.
     * If it is not explicitly called, it will be called with default settings when the first class of this library
     * is used.
     * <p>
     *     This method can be used to set the app id for native serial comm resources. This is useful if you want to
     *     use the same native serial comm resources in different applications. By setting the app id, you can ensure
     *     that the same native resources are used by different applications. If you don't set the app id, a random
     *     app id will be generated.
     * </p>
     * @param appIdForNativeSerialCommResources the app id for native serial comm resources (optional)
     * @see <a href="https://github.com/Fazecast/jSerialComm?tab=readme-ov-file#usage">jSerialComm Usage</a>
     */
    public static void init(String appIdForNativeSerialCommResources) {
        if(appIdForNativeSerialCommResources==null || appIdForNativeSerialCommResources.trim().isEmpty()) {
            System.setProperty("jSerialComm.library.randomizeNativeName", "true");
            Logger.tag(TAG).info("Randomizing jSerialComm native library name.");
        } else {
            System.setProperty("fazecast.jSerialComm.appid", appIdForNativeSerialCommResources);
            Logger.tag(TAG).info("Setting jSerialComm appid to: " + appIdForNativeSerialCommResources);
        }

        initialized = true;
    }

    /**
     * Initializes the library. This method must be called before using any other class of this library. If it is not
     * explicitly called, it will be called with default settings when the first class of this library is used.
     * @see #init(String)
     */
    public static void init() {
        init(null);
    }


    static void checkInit() {
        if(!initialized) {
            init();
        }
    }
}
