package com.young.aircraft.gui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.young.aircraft.gui.ThemedMessage

/**
 * Read-only telemetry panel for the debug assistant modules. Body is monospace and
 * scrollable because kernel/browser dumps are far taller than a native message dialog
 * can show, and the close button is a 48dp target rather than a system one.
 */
internal const val InfoCopyButtonTag = "info_dialog_copy"

@Composable
fun InfoDialogContent(
    title: String,
    body: String,
    closeText: String,
    copyText: String,
    copiedText: String
) {
    val colors = MaterialTheme.colorScheme
    val palette = gameDialogPalette(colors.primary)
    val dismiss = LocalDialogDismiss.current
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.statCardContainer, RoundedCornerShape(20.dp))
            .border(1.5.dp, palette.badgeBorder, RoundedCornerShape(20.dp))
            .padding(24.dp)
    ) {
        Text(
            text = title,
            color = palette.titleColor,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp, bottom = 16.dp)
                .height(1.dp)
                .background(palette.dividerColor)
        )

        Text(
            text = body,
            color = colors.onSurface,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .fillMaxWidth()
                // ponytail: capped at half the screen; dumps longer than that are debug-only noise.
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState())
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag(InfoCopyButtonTag)
                    .background(palette.statCardContainer, RoundedCornerShape(12.dp))
                    .border(1.dp, palette.badgeBorder, RoundedCornerShape(12.dp))
                    .clickable {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.setPrimaryClip(ClipData.newPlainText(title, body))
                        ThemedMessage.makeText(context, copiedText, ThemedMessage.LENGTH_SHORT).show()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = copyText,
                    color = colors.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .background(colors.primary, RoundedCornerShape(12.dp))
                    .clickable { dismiss?.invoke() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = closeText,
                    color = colors.onPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
