# Consumer rules for :developtools.
#
# ML Kit GenAI structured output parses the @Generable result class through the
# KSP-generated provider (looked up via META-INF/services) at runtime, so both
# classes must survive R8 in the consuming app.
-keep class com.young.developtools.viewmodel.CardNumberExtraction { *; }
-keep class com.young.developtools.viewmodel.CardNumberExtraction_GeneratedProvider { *; }
#
# No other keep rules are required:
#
# - Manifest-merged components (all dev-tool activities + FlashlightService) are
#   entry points: AAPT2 emits `-keep class <component> { <init>(); }` for each of
#   them into aapt_rules.txt, so their class names and constructors are already
#   retained. No wildcard activity keep is declared here: the former
#   `-keep public class com.young.developtools.gui.*Activity` also pinned
#   BaseAircraftActivity, which is never launched, and the previous full member
#   keeps (`{ *; }`) had blocked optimization of ~595 members that are reached
#   only through direct references (see the R8 config analyzer report).
#
# - The cross-module hook boundary (DevTools, DebugTools, DeveloperMode,
#   ApiHistoryStore) is wired with direct references only -- no reflection and no
#   name lookup -- so the name-only keeps that used to sit here were redundant:
#   they kept five classes out of obfuscation and shrinking for no runtime gain.
#   The R8 config analyzer scored each of them at one retained class.
#
# - Deliberately NO enum values()/valueOf() keeps here: unlike the game's
#   GameMode/GameDifficulty (persisted by name), nothing in this module restores
#   an enum from a persisted string. Gson only touches Map<String, String> /
#   Map<String, List<String>> / Any, so no model-class keeps are needed either.
