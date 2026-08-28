# Release only: drop everything below Log.e so shipped builds carry no verbose logging.
# Remove logging in release builds (removes Log.d, Log.v, Log.i calls)
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int d(...);
    public static int w(...);
}
