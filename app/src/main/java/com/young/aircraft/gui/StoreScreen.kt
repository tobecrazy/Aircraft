package com.young.aircraft.gui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreScreen(
    onBackClick: () -> Unit,
    viewModel: StoreViewModel = viewModel()
) {
    val items by viewModel.items.collectAsState()
    val credits by viewModel.credits.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Aircraft", "Weapons", "Power-ups")

    val filteredItems = items.filter {
        when (selectedTab) {
            0 -> it.type == StoreItemType.AIRCRAFT
            1 -> it.type == StoreItemType.WEAPON
            else -> it.type == StoreItemType.POWER_UP
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hangar Store") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    AssistChip(
                        onClick = { },
                        label = { Text("$credits Credits") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.MonetizationOn,
                                contentDescription = null,
                                modifier = Modifier.size(AssistChipDefaults.IconSize),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredItems) { item ->
                    StoreItemCard(
                        item = item,
                        canAfford = credits >= item.price,
                        onBuyClick = { viewModel.buyItem(item) }
                    )
                }
            }
        }
    }
}

@Composable
fun StoreItemCard(
    item: StoreItem,
    canAfford: Boolean,
    onBuyClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = item.imageResId),
                    contentDescription = item.name,
                    modifier = Modifier.size(80.dp),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = item.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.height(32.dp),
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (item.isOwned) {
                Button(
                    onClick = { },
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        disabledContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Owned")
                }
            } else {
                Button(
                    onClick = onBuyClick,
                    enabled = canAfford,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (item.price == 0) "Free" else "${item.price} Cr")
                }
            }
        }
    }
}
