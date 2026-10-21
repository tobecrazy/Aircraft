# ============================================================
# Room Database
# Room's runtime consumer rules keep the generated *_Impl classes
# and `* extends androidx.room.RoomDatabase { void <init>(); }`.
# The database, entity, and DAO classes are reached from the
# generated code directly (no reflection), and migrations are
# registered as direct references in DatabaseProvider, so no
# manual keeps are needed (verified with the R8 config analyzer
# report: the previous keep rules matched nothing not already
# retained).
# ============================================================

# ============================================================
# Android Components (referenced by name in the manifest)
# ============================================================
# Manifest components (MusicService) are auto-kept by AAPT2, and
# the binder interaction uses direct references only, so no
# manual member keeps are needed. StarFieldView is instantiated
# exclusively from Compose code (AndroidView factory) and is
# therefore always reachable.
# FlashlightService moved to :developtools (kept via its consumer-rules.pro).

# ============================================================
# Enums (GameMode/GameDifficulty are persisted by name and
# restored via valueOf; GameState is state-machine data)
# ============================================================
# No enum keeps are declared here: the AGP default
# `-keepclassmembers enum *` rule already retains values()/valueOf()
# for every enum, and the R8 config analyzer confirms the former
# per-enum duplicates (GameMode, GameDifficulty, GameState) were
# subsumed by it, so they kept nothing extra.

# ============================================================
# Kotlin singletons (object declarations) — INSTANCE field
# ============================================================
# Removed: DatabaseProvider.INSTANCE and GameStateManager.INSTANCE
# are static-final fields referenced directly, never via
# reflection, so their keep rules matched nothing that R8 would
# otherwise strip (per the R8 config analyzer report).

# ============================================================
# Attributes (needed for Crashlytics symbolication)
# The AGP default file already retains AnnotationDefault and all
# runtime-visible annotation types through its own
# -keepattributes list, so no extra `*Annotation*` line is
# declared here. Coroutines and kotlin.Metadata are covered by
# their own bundled rules.
# ============================================================
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ============================================================
# OkHttp / Firebase (both ship their own consumer rules)
# ============================================================
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn com.google.firebase.**
