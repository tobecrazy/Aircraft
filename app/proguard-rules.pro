# ============================================================
# Room Database
# ============================================================
-keep class com.young.aircraft.data.AppDatabase { *; }
-keep class com.young.aircraft.data.PlayerGameData { *; }
-keep class com.young.aircraft.data.PlayerGameDataDao { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *

# Room uses reflection to access migrations by name
-keepclassmembers class com.young.aircraft.data.AppDatabase$Companion {
    ** MIGRATION_*;
}

# ============================================================
# Android Components (referenced by name in manifest/layout XML)
# ============================================================
-keep class com.young.aircraft.service.MusicService { *; }
-keep class com.young.aircraft.service.MusicService$MusicBinder { *; }
-keep class com.young.aircraft.gui.StarFieldView { *; }
-keep class com.young.aircraft.gui.HistoryFragment { *; }

# ============================================================
# Enums (GameDifficulty has constructor params accessed reflectively)
# ============================================================
-keepclassmembers enum class com.young.aircraft.data.GameDifficulty {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    *;
}
-keepclassmembers enum class com.young.aircraft.data.GameState {
    *;
}

# ============================================================
# Kotlin singletons (object declarations) — INSTANCE field
# ============================================================
-keepclassmembers class com.young.aircraft.providers.DatabaseProvider {
    public static final ** INSTANCE;
}
-keepclassmembers class com.young.aircraft.common.GameStateManager {
    public static final ** INSTANCE;
}

# ============================================================
# Kotlin metadata & coroutines (required for Room KSP, Flow, etc.)
# ============================================================
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlinx.coroutines.** { *; }

# ============================================================
# OkHttp
# ============================================================
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ============================================================
# Firebase
# ============================================================
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**

# ============================================================
# AndroidX & View Binding
# ============================================================
-keep class * extends androidx.appcompat.app.AppCompatActivity { *; }
-keep class * extends androidx.fragment.app.Fragment { *; }
-keepclassmembers class * extends androidx.recyclerview.widget.RecyclerView.Adapter {
    *;
}
-keepclassmembers class * implements android.view.View$OnClickListener {
    public void onClick(android.view.View);
}

# ============================================================
# General Android rules
# ============================================================
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.app.Application
-keep public class * extends android.app.Activity
-keep public class * extends android.view.View {
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}