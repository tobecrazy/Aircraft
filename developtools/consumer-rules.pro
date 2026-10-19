# Consumer rules for :developtools.
#
# Manifest-merged components (all dev-tool activities + FlashlightService) are
# entry points: AGP generates class-name + constructor keeps for them
# automatically. The rules below are a small belt-and-braces, CLASS-NAME-ONLY
# fallback so a future AGP/R8 behavior change cannot strip the entry points;
# the previous full member keeps (`{ *; }`) had blocked optimization of ~595
# members that are reached only through direct references (see the R8 config
# analyzer report).
-keep public class com.young.developtools.gui.*Activity
-keep public class com.young.developtools.service.FlashlightService

# The cross-module hook boundary (DevTools, DebugTools, DeveloperMode,
# ApiHistoryStore) is wired with direct references only -- no reflection, so
# member keeps are unnecessary; name-only keeps remain as belt-and-braces.
-keep class com.young.developtools.DevTools
-keep class com.young.developtools.utils.DebugTools
-keep class com.young.developtools.utils.DeveloperMode
-keep class com.young.developtools.repository.ApiHistoryStore

# Deliberately NO enum values()/valueOf() keeps here: unlike the game's
# GameMode/GameDifficulty (persisted by name), nothing in this module restores
# an enum from a persisted string. Gson only touches Map<String, String> /
# Map<String, List<String>> / Any, so no model-class keeps are needed either.
