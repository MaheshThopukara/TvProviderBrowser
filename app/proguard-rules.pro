# ── TvProviderBrowser ProGuard / R8 Rules ──

# Keep readable stack traces in crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Navigation Safe Args ──
-keep class * extends androidx.navigation.NavArgs { *; }
-keep class **.*Args { *; }
-keep class **.*Directions { *; }

# ── Kotlin Coroutines ──
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# ── AndroidX Leanback (VerticalGridView uses internal reflection) ──
-keep class androidx.leanback.widget.** { *; }

# ── Hilt ──
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# ── Keep the data model classes (used with ContentResolver) ──
-keep class com.mahesh.tvproviderbrowser.data.model.** { *; }

# ── Suppress common warnings ──
-dontwarn kotlin.reflect.**
-dontwarn org.jetbrains.annotations.**
