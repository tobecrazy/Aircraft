# ============================================================
# Room Database
# Room emits its own rules for the generated _Impl classes, so only the
# entities/DAO the app references by name are kept here.
# ============================================================
-keep class com.young.aircraft.data.AppDatabase { *; }
-keep class com.young.aircraft.data.PlayerGameData { *; }
-keep class com.young.aircraft.data.PlayerGameDataDao { *; }

# Room migrations live in AppDatabase.Companion (registered in DatabaseProvider)
-keepclassmembers class com.young.aircraft.data.AppDatabase$Companion {
    ** MIGRATION_*;
}

# ============================================================
# Android Components (referenced by name in the manifest)
# ============================================================
# FlashlightService moved to :developtools (kept via its consumer-rules.pro).
-keep class com.young.aircraft.service.MusicService { *; }
-keep class com.young.aircraft.service.MusicService$MusicBinder { *; }
-keep class com.young.aircraft.gui.StarFieldView { *; }

# ============================================================
# Enums (GameMode/GameDifficulty are persisted by name and
# restored via valueOf; GameState is state-machine data)
# ============================================================
# GameMode/GameDifficulty are persisted by name and restored via valueOf; the
# explicit values()/valueOf() lines are subsumed by the AGP default
# `-keepclassmembers enum *` rule (see the R8 config analyzer report), so only
# `*;` is declared here. GameState is state-machine data.
-keepclassmembers enum com.young.aircraft.data.GameMode {
    *;
}
-keepclassmembers enum com.young.aircraft.data.GameDifficulty {
    *;
}
-keepclassmembers enum com.young.aircraft.data.GameState {
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
# Attributes (needed for Room / Crashlytics symbolication)
# Coroutines and kotlin.Metadata are covered by their own bundled rules.
# ============================================================
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ============================================================
# OkHttp / Firebase (both ship their own consumer rules)
# ============================================================
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn com.google.firebase.**
