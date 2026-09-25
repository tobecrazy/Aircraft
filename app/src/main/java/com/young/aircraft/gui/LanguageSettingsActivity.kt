package com.young.aircraft.gui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import com.young.aircraft.R
import com.young.aircraft.ui.theme.AccentGreen
import com.young.aircraft.ui.theme.AircraftTheme
import com.young.aircraft.ui.theme.DividerGreen

/** Empty tag means "follow system" (clears per-app locales). */
private val LANGUAGE_OPTIONS = listOf(
    "" to R.string.language_follow_system,
    "zh-CN" to R.string.language_simplified_chinese,
    "zh-TW" to R.string.language_traditional_taiwan,
    "zh-HK" to R.string.language_traditional_hongkong,
    "en" to R.string.language_english
)

class LanguageSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        setContent {
            AircraftTheme {
                LanguageSettingsScreen(
                    onBack = { finish() },
                    onSave = ::applyLanguage
                )
            }
        }
    }

    private fun applyLanguage(tag: String) {
        AppCompatDelegate.setApplicationLocales(
            if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList()
            else LocaleListCompat.forLanguageTags(tag)
        )
        finish()
    }
}

@Composable
fun LanguageSettingsScreen(
    onBack: () -> Unit,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Native language names are resource strings kept identical in every locale so each
    // row always renders in its own language; "follow system" is the only translated label.
    var selected by rememberSaveable {
        mutableStateOf(AppCompatDelegate.getApplicationLocales().toLanguageTags())
    }

    Column(modifier = modifier.fillMaxSize().background(ScreenBg)) {
        SettingsHeader(
            title = stringResource(R.string.language_settings_title),
            onBack = onBack,
            endContent = {
                Text(
                    text = stringResource(R.string.language_save),
                    color = AccentGreen,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .clickable(onClick = { onSave(selected) }, role = Role.Button)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(DividerGreen))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 16.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .padding(horizontal = 14.dp)
                    .background(TileBg, RoundedCornerShape(16.dp))
                    .selectableGroup()
            ) {
                LANGUAGE_OPTIONS.forEachIndexed { index, (tag, labelRes) ->
                    if (index > 0) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(DividerGreen.copy(alpha = 0.3f)))
                    }
                    LanguageRow(
                        label = stringResource(labelRes),
                        selected = tag == selected,
                        onClick = { selected = tag }
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TitleWhite,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Text(
                text = "✓",
                color = AccentGreen,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Preview(name = "Language Settings Screen", widthDp = 420, heightDp = 920, showBackground = true, backgroundColor = 0xFF0F1118)
@Composable
private fun LanguageSettingsScreenPreview() {
    LanguageSettingsScreen(onBack = {}, onSave = {})
}
