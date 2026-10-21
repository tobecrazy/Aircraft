# Consumer rules for :developtools.
#
# Manifest-merged components (all dev-tool activities + FlashlightService) are
# entry points: AAPT2 emits `-keep class <component> { <init>(); }` for each of
# them into aapt_rules.txt, so their class names and constructors are already
# retained. No wildcard activity keep is declared here: the former
# `-keep public class com.young.developtools.gui.*Activity` also pinned
# BaseAircraftActivity, which is never launched, and the previous full member
# keeps (`{ *; }`) had blocked optimization of ~595 members that are reached
# only through direct references (see the R8 config analyzer report).
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
