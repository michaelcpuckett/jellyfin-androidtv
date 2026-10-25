-dontwarn org.commonmark.ext.gfm.strikethrough.Strikethrough

# Strip low-priority logging at build time, including removable argument formatting.
# Warnings and errors remain available for playback and crash diagnostics.
-assumenosideeffects class timber.log.Timber* {
    public *** v(...);
    public *** d(...);
    public *** i(...);
}

-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

-assumenosideeffects class androidx.media3.common.util.Log {
    public static void v(...);
    public static void d(...);
    public static void i(...);
}
