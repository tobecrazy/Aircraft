package com.young.aircraft.gui

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.young.aircraft.R
import com.young.aircraft.ui.Aircraft

@Composable
fun LaunchScreen(
    onStartGame: (Int) -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onStoreClick: () -> Unit
) {
    val jetPlanes = Aircraft.JET_PLANES
    var selectedJetIndex by remember { mutableIntStateOf(0) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Background
        Image(
            painter = painterResource(id = R.drawable.launch_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Overlay for readability
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black.copy(alpha = 0.3f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "AIRCRAFT",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 8.sp,
                        color = Color.White
                    )
                )

                Text(
                    text = "PILOT SELECTION",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                // Jet Selection
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clickable {
                            selectedJetIndex = (selectedJetIndex + 1) % jetPlanes.size
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = jetPlanes[selectedJetIndex],
                        transitionSpec = {
                            fadeIn() + scaleIn() togetherWith fadeOut() + scaleOut()
                        }, label = "JetAnimation"
                    ) { jetRes ->
                        Image(
                            painter = painterResource(id = jetRes),
                            contentDescription = "Selected Jet",
                            modifier = Modifier.size(120.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))

                // Menu Buttons
                Column(
                    modifier = Modifier.width(IntrinsicSize.Max),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MenuButton(
                        text = stringResource(R.string.start_game),
                        icon = Icons.Default.PlayArrow,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        onClick = { onStartGame(selectedJetIndex) }
                    )

                    MenuButton(
                        text = stringResource(R.string.hangar_store),
                        icon = Icons.Default.ShoppingCart,
                        onClick = onStoreClick
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MenuButton(
                            text = stringResource(R.string.start_history),
                            icon = Icons.Default.History,
                            modifier = Modifier.weight(1f),
                            onClick = onHistoryClick
                        )
                        MenuButton(
                            text = stringResource(R.string.game_settings),
                            icon = Icons.Default.Settings,
                            modifier = Modifier.weight(1f),
                            onClick = onSettingsClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MenuButton(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}
