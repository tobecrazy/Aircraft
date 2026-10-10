# Consumer rules for :developtools.
#
# Manifest-merged components (all dev-tool activities + FlashlightService) are
# entry points: AGP generates keeps for them automatically. The rules below are
# explicit belt-and-braces so a future AGP/R8 behavior change cannot strip the
# release-unlock path (About -> 8 taps -> DevPrefs flag -> DeveloperMode gate).
-keep public class com.young.developtools.gui.*Activity { *; }
-keep public class com.young.developtools.service.FlashlightService { *; }
-keepclassmembers class com.young.developtools.utils.DebugTools { *; }
-keepclassmembers class com.young.developtools.utils.DeveloperMode { *; }
-keep class com.young.developtools.DevTools { *; }
-keep interface com.young.developtools.repository.ApiHistoryStore { *; }

# Deliberately NO enum values()/valueOf() keeps here: unlike the game's
# GameMode/GameDifficulty (persisted by name), nothing in this module restores
# an enum from a persisted string. Gson only touches Map<String, String> /
# Map<String, List<String>> / Any, so no model-class keeps are needed either.
