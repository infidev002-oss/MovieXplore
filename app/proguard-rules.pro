# Gson / Firebase models
-keep class com.movies.moviexplore.network.model.** { *; }
-keep class com.movies.moviexplore.watchlist.WatchlistItem { *; }
-keepattributes Signature, *Annotation*

# ---- App rules (release: R8 shrink + obfuscate + optimize) ----
# Retrofit, OkHttp, Room, Firebase, Play services, credentials and AndroidX ship their own consumer rules;
# only what they can't know about is listed here.

# Readable Crashlytics stack traces: keep line numbers, hide the original file names.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
-keep public class * extends java.lang.Exception

# Retrofit: the API interface's generic suspend signatures are read by reflection at runtime.
-keepattributes InnerClasses, EnclosingMethod, Exceptions
-keep,allowobfuscation,allowshrinking interface com.movies.moviexplore.network.TmdbApi
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# Enums restored by name (Intent extras, saved state, settings).
-keepclassmembers enum com.movies.moviexplore.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Custom views inflated from XML by class name.
-keep class com.movies.moviexplore.ui.common.GlassFrameLayout { public <init>(...); }
-keep class com.movies.moviexplore.ui.common.AspectRatioFrameLayout { public <init>(...); }

# Optional dependencies referenced by OkHttp / Retrofit / Gson that aren't on Android.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
