# RichTextEditor AAR Usage

`richtexteditor` is a standalone Android library module that packages `com.young.richtext.RichTextEditorView` as an AAR. The app consumes it through `implementation project(':richtexteditor')`.

## Build the AAR

From the repository root:

```bash
./gradlew :richtexteditor:assembleRelease
```

The release artifact is generated at:

```text
richtexteditor/build/outputs/aar/richtexteditor-release.aar
```

For local debug validation, build:

```bash
./gradlew :richtexteditor:assembleDebug
```

## Add to Another Android App

Copy the AAR into the consuming app, for example `app/libs/richtexteditor-release.aar`, then add:

```groovy
dependencies {
    implementation files("libs/richtexteditor-release.aar")
    implementation "androidx.core:core-ktx:1.19.0"
}
```

The library is built with:

- `compileSdk 37`
- `minSdk 30`
- Kotlin JVM target `17`
- AndroidX enabled

## XML Usage

```xml
<com.young.richtext.RichTextEditorView
    android:id="@+id/rich_editor"
    android:layout_width="match_parent"
    android:layout_height="240dp" />
```

## Kotlin Usage

```kotlin
val richEditor = findViewById<RichTextEditorView>(R.id.rich_editor)

richEditor.setHint("Write formatted content")
richEditor.setEditorHeight((220 * resources.displayMetrics.density).toInt())

val plainText = richEditor.plainText
val editable = richEditor.text
val isMarkdown = richEditor.isMarkdownMode
```

## Preview HTML

The view owns the editable toolbar and text input. Hosts that need preview mode should render the generated HTML in their own `WebView`.

```kotlin
val body = if (richEditor.isMarkdownMode) {
    RichTextEditorView.processMarkdown(richEditor.plainText)
} else {
    RichTextEditorView.plainTextToHtml(richEditor.plainText)
}

val clickableBody = RichTextEditorView.makeImagesClickable(body)
webView.loadDataWithBaseURL(null, clickableBody, "text/html", "UTF-8", null)
```

For image tap handling:

```kotlin
override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
    if (url != null && RichTextEditorView.isImageTapUrl(url)) {
        val src = RichTextEditorView.extractImageSrcFromTapUrl(url)
        // Open the host app's image details screen.
        return true
    }
    return false
}
```

## Public API

`RichTextEditorView` exposes:

- `editor: EditText`
- `toolbarScroll: View`
- `toolbarDivider: View`
- `text: Editable?`
- `plainText: String`
- `isMarkdownMode: Boolean`
- `setHint(CharSequence)`
- `setEditorBackground(resId: Int)`
- `setEditorHeight(heightPx: Int)`
- `processMarkdown(input: String): String`
- `plainTextToHtml(input: String): String`
- `makeImagesClickable(html: String): String`
- `buildImageTapUrl(src: String): String`
- `isImageTapUrl(url: String): Boolean`
- `extractImageSrcFromTapUrl(url: String): String?`
- `isImageFormatSupportedForPreview(fileNameOrExtension: String): Boolean`

## Notes

The toolbar supports bold, italic, underline, size, color, Markdown toggle, and quick HTML snippets. The view disables `EditText` state saving internally so hosts can restore large HTML/base64 content explicitly without hitting Android `Bundle` size limits.
