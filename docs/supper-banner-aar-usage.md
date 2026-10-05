# SupperBanner AAR Usage

`supperbanner` is a standalone Android library module that packages the auto-playing banner carousel as an AAR. The app consumes it through `implementation project(':supperbanner')`. The module has no dependency on the game, on `:richtexteditor`, or on any app theme class.

## Build the AAR

From the repository root:

```bash
./gradlew :supperbanner:assembleRelease
```

The release artifact is generated at:

```text
supperbanner/build/outputs/aar/supperbanner-release.aar
```

For local debug validation, build:

```bash
./gradlew :supperbanner:assembleDebug
```

## Publish the AAR

The module applies `maven-publish` and declares a single `release` publication (`com.young:supperbanner:1.0.0`, packaging `aar`, with a sources jar). By default it publishes to a local file repository under the module build directory, so no credentials are needed:

```bash
./gradlew :supperbanner:publishReleasePublicationToBuildRepoRepository
```

Artifacts land in `supperbanner/build/repo/com/young/supperbanner/1.0.0/`. To install into `~/.m2` instead:

```bash
./gradlew :supperbanner:publishToMavenLocal
```

To push to an internal Maven server, add a remote repository next to the existing `buildRepo` entry in `supperbanner/build.gradle.kts`:

```kotlin
publishing {
    repositories {
        maven {
            name = "internal"
            url = uri("https://maven.example.com/releases")
            credentials {
                username = providers.gradleProperty("mavenUser").orNull
                password = providers.gradleProperty("mavenPassword").orNull
            }
        }
    }
}
```

The publication does not declare a license in the POM — the repository has no `LICENSE` file, so add a `licenses { license { ... } }` block once one exists.

Bump the version by editing `group` and `version` at the top of `supperbanner/build.gradle.kts`.

## Add to Another Android App

Copy the AAR into the consuming app, for example `app/libs/supperbanner-release.aar`, then add:

```groovy
dependencies {
    implementation files("libs/supperbanner-release.aar")
    implementation "androidx.core:core-ktx:1.19.1"
    implementation "androidx.recyclerview:recyclerview:1.4.0"
    implementation "androidx.viewpager2:viewpager2:1.1.0"
    implementation "io.coil-kt:coil:2.7.0"
}
```

The library is built with:

- `compileSdk 37`
- `minSdk 31`
- Kotlin JVM target `17`

## XML Usage

```xml
<com.young.supperbanner.SupperBannerView
    android:id="@+id/supper_banner"
    android:layout_width="match_parent"
    android:layout_height="190dp" />
```

## Kotlin Usage

```kotlin
val banner = findViewById<SupperBannerView>(R.id.supper_banner)

banner.setItems(
    listOf(
        SupperBannerItem(
            name = "Local drawable",
            description = "Bundled background",
            image = SupperBannerImage.Local(R.drawable.background)
        ),
        SupperBannerItem(
            name = "Remote image",
            description = "Loaded with Coil",
            image = SupperBannerImage.Network("https://example.com/image.png")
        )
    )
)

banner.setOnBannerClickListener { item, position ->
    // item.name / item.description / item.image are available here.
}

// Optional: restyle the page indicators to match the host theme.
banner.setIndicatorCustomizer { indicator, selected, _ ->
    indicator.setTextColor(if (selected) Color.BLACK else Color.LTGRAY)
}
```

To embed the view inside Compose, use `AndroidView`:

```kotlin
AndroidView(
    modifier = Modifier.fillMaxWidth().height(190.dp),
    factory = { context -> SupperBannerView(context) },
    update = { banner -> banner.setItems(bannerItems) }
)
```

## Public API

`SupperBannerView` exposes:

- `setItems(newItems: List<SupperBannerItem>)`
- `setAutoPlayEnabled(enabled: Boolean)`
- `setTransitionTimeMillis(timeMillis: Long)`
- `setShowImageInfo(show: Boolean)`
- `setShowIndicator(show: Boolean)`
- `setOnBannerClickListener(listener: ((SupperBannerItem, Int) -> Unit)?)`
- `setIndicatorCustomizer(customizer: ((TextView, Boolean, Int) -> Unit)?)`
- `setColors(value: SupperBannerColors)`
- `setTransition(transition: SupperBannerTransition)`

`SupperBannerConfig` exposes:

- `DEFAULT_TRANSITION_TIME_MS` (`3_000L`)
- `MIN_TRANSITION_TIME_MS` (`800L`)
- `MAX_TRANSITION_TIME_MS` (`15_000L`)
- `coerceTransitionTimeMillis(value: Long): Long`

`setTransitionTimeMillis` already clamps through `coerceTransitionTimeMillis`; call the helper yourself only when you need the clamped value for a settings UI.

## Data Model

```kotlin
data class SupperBannerItem(
    val name: String,
    val description: String,
    val image: SupperBannerImage
)

sealed class SupperBannerImage {
    data class Local(@DrawableRes val resId: Int) : SupperBannerImage()
    data class Network(val url: String) : SupperBannerImage()
}
```

Network images load through Coil with a crossfade; the library ships its own `ic_placeholder` drawable as the placeholder and error drawable.

## Customizing Colors

Every color the view paints with lives in `SupperBannerColors`. The parameterless constructor reproduces the original dark tactical look, so nothing changes until you call `setColors`. `copy()` overrides a single field and leaves the rest at their defaults:

```kotlin
banner.setColors(
    SupperBannerColors(
        background = 0xFF102030.toInt(),
        indicator = SupperBannerIndicatorColors(
            fillSelected = accent,          // theme accent for the active dot
            textUnselected = Color.TRANSPARENT
        )
    )
)
```

To follow a host theme, map the theme's own colors in; the library has no dependency on any app theme class:

```kotlin
banner.setColors(
    SupperBannerColors(
        title = MaterialTheme.colorScheme.onSurface,
        description = MaterialTheme.colorScheme.onSurfaceVariant,
        indicator = SupperBannerIndicatorColors(fillSelected = MaterialTheme.colorScheme.primary)
    )
)
```

`SupperBannerColors` fields — all `Int` ARGB, all defaulted:

| Field | Default | Used for |
|---|---|---|
| `background` | `0xFF151A24` | Card fill behind the pager |
| `border` | `0x2AFFFFFF` | 1dp card stroke |
| `scrimStart` | `Color.TRANSPARENT` | Top of the info-panel gradient |
| `scrimEnd` | `0xCC050812` | Bottom of the info-panel gradient |
| `title` | `Color.WHITE` | Item name |
| `description` | `0xFFCBD5E8` | Item description |
| `indicator` | see below | Page dots |

`SupperBannerIndicatorColors` fields:

| Field | Default |
|---|---|
| `textSelected` | `0xFF07100B` |
| `textUnselected` | `0xCCFFFFFF` |
| `fillSelected` | `0xFF00FF88` |
| `fillUnselected` | `0x442A3342` |
| `strokeSelected` | `0xAAFFFFFF` |
| `strokeUnselected` | `0x55FFFFFF` |

`setColors` repaints immediately and may be called at any time, including after `setItems`. It re-applies indicator colors too — but if an indicator customizer is installed, the customizer still runs afterwards and keeps the last word on the dots. Use `setColors` for color and the customizer for anything it cannot express (typeface, stroke width, shape).

## Transition Effects

`setTransition` installs a `ViewPager2.PageTransformer`. `SupperBannerEffect.FADE` is the default, so a `SupperBannerTransition()` with no arguments cross-fades. `SupperBannerEffect.NONE` removes the transformer and restores the plain ViewPager2 slide.

```kotlin
banner.setTransition(SupperBannerTransition(effect = SupperBannerEffect.COVERFLOW))
```

| Effect | Look | Key properties used |
|---|---|---|
| `NONE` | Default slide | — |
| `FADE` | Cross-fade, plainest option (default) | `minAlpha` |
| `ZOOM_OUT` | Outgoing page shrinks and fades | `minScale`, `minAlpha` |
| `DEPTH` | Incoming page starts small and behind | `minScale` |
| `CUBE` | 3D cube flip on the leading edge | `maxRotation`, `cameraDistance` |
| `ROTATION_GATE` | 3D door flip on the centre | `maxRotation`, `cameraDistance`, `minAlpha` |
| `COVERFLOW` | Centre page full size, sides shrink + rotate | `minScale`, `coverflowAngle`, `minAlpha` |
| `STACK` | Tinder-style card stack | `stackSpread`, `minScale`, `minAlpha` |
| `PARALLAX` | Image layer lags behind the pager | `parallaxFraction` |
| `ACCORDION` | Horizontal fold from the leading edge | `minAlpha` |
| `SHADER` | AGSL circular dissolve | API 33+, falls back to `FADE` |

`SupperBannerTransition` also carries the calibration knobs; every field is optional and out-of-range values are clamped rather than rejected.

```kotlin
banner.setTransition(
    SupperBannerTransition(
        effect = SupperBannerEffect.COVERFLOW,
        minScale = 0.7f,
        coverflowAngle = 45f
    )
)
```

The `Aircraft` app wires this to a dropdown in `DevelopSettingsActivity`; see [DevelopSettingsActivity](../app/src/main/java/com/young/aircraft/gui/DevelopSettingsActivity.kt) for a worked example.

Three limits worth knowing before you pick an effect:

- `PARALLAX` offsets the image layer only. The title/description panel lives outside the pager and is re-bound on `onPageSelected`, so text does not parallax. Lagging a page widens the gap to its neighbour, so the page is scaled by `1 + 2 × parallaxFraction` to keep the seam covered — a visible zoom is the cost of the effect on a `ViewPager2`, and the same effect in Compose hides it with `contentPadding` instead.
- `COVERFLOW` and `STACK` raise `offscreenPageLimit` to 3 in `setTransition`; the other effects leave it at the ViewPager2 default of 1.
- `SHADER` needs API 33+ and catches AGSL compile failures, degrading to `FADE` rather than taking the view down.
- Page-animation speed is not configurable — ViewPager2 does not expose its scroll duration, only `setCurrentItem(..., smoothScroll)`. `setTransitionTimeMillis` controls the auto-play dwell, not the animation.

## Notes

- Auto-play stops and restarts on attach, detach, page change, and every setter, so it never keeps posting while the view is off-screen. Transition times are clamped to the supported range.
- The view is a plain Android `View`, not Compose. It carries no strings and no theme colors of its own, so it drops into any host without localization changes.
- The item-click contract and the details/download screen are app concerns. `BannerDetailsActivity` and `BannerDetailsViewModel` in `:app` own that flow and only depend on the `SupperBannerItem` / `SupperBannerImage` types exported here.
