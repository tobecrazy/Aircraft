# TODOS.md

## Deferred Work

### 1. Privacy policy re-consent on version change
**What:** When the privacy policy content changes, force users to re-accept by checking a policy version number.
**Why:** Play Store requires re-consent if the privacy policy materially changes. Currently, once accepted, users never see the policy again.
**How:** Store `privacy_policy_version_accepted` (String) alongside `privacy_policy_accepted` (Boolean) in SharedPreferences. Compare against a `CURRENT_POLICY_VERSION` constant in the app. If mismatch, show the privacy screen again.
**Priority:** Low — rare event for indie games, but real compliance need.
**Added:** 2026-03-21 (from /plan-eng-review of cinematic first-launch experience)

### 2. Ambient sound effects for cinematic privacy screen
**What:** Add subtle ambient audio (low engine hum, radar ping) to the cinematic privacy policy screen.
**Why:** The "mission briefing" vibe is visual-only. Audio would complete the immersion.
**How:** Tie into existing `MusicService` SoundPool. Add a looping ambient track and an optional "ambient_sound" SharedPreferences toggle. Play on privacy screen, stop on navigation.
**Blocked by:** Audio asset creation (find/create appropriate ambient SFX files).
**Priority:** Low — enhancement, not blocking.
**Added:** 2026-03-21 (from /plan-eng-review of cinematic first-launch experience)
