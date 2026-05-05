package com.young.aircraft.gui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.young.aircraft.R
import com.young.aircraft.data.BannerDetailsIntentContract
import com.young.aircraft.data.BannerDetailsSource
import com.young.aircraft.viewmodel.BannerDetailsEvent
import com.young.aircraft.viewmodel.BannerDetailsUiState
import com.young.aircraft.viewmodel.BannerDetailsViewModel
import kotlinx.coroutines.launch

class BannerDetailsActivity : AppCompatActivity() {

    private lateinit var viewModel: BannerDetailsViewModel

    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("image/jpeg")
    ) { uri ->
        if (uri != null) viewModel.saveImage(uri)
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        if (BannerDetailsIntentContract.fromIntent(intent) == null) {
            finish()
            return
        }
        viewModel = ViewModelProvider(
            this,
            BannerDetailsViewModel.Factory(this, intent)
        )[BannerDetailsViewModel::class.java]

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        setContent {
            MaterialTheme {
                val uiState by viewModel.uiState.collectAsState()
                BannerDetailsScreen(
                    uiState = uiState,
                    onBack = { finish() },
                    onDownload = { createDocumentLauncher.launch(uiState.details.downloadFileName) }
                )
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is BannerDetailsEvent.SaveCompleted -> {
                            Toast.makeText(
                                this@BannerDetailsActivity,
                                if (event.saved) R.string.banner_details_save_success else R.string.banner_details_save_failed,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }
    }

    companion object {
        fun createIntent(context: Context, item: SupperBannerItem): Intent =
            Intent(context, BannerDetailsActivity::class.java).apply {
                putExtra(BannerDetailsIntentContract.EXTRA_NAME, item.name)
                putExtra(BannerDetailsIntentContract.EXTRA_DESCRIPTION, item.description)
                when (val image = item.image) {
                    is SupperBannerImage.Local -> {
                        putExtra(BannerDetailsIntentContract.EXTRA_SOURCE_TYPE, BannerDetailsIntentContract.SOURCE_LOCAL)
                        putExtra(BannerDetailsIntentContract.EXTRA_RES_ID, image.resId)
                    }
                    is SupperBannerImage.Network -> {
                        putExtra(BannerDetailsIntentContract.EXTRA_SOURCE_TYPE, BannerDetailsIntentContract.SOURCE_NETWORK)
                        putExtra(BannerDetailsIntentContract.EXTRA_URL, image.url)
                    }
                }
            }
    }
}

private val DetailsBackground = Color(0xFF0F1118)
private val DetailsHeader = Color(0xFF161A26)
private val DetailsAccent = Color(0xFF00FF88)
private val DetailsPanel = Color(0x20252A3A)
private val DetailsPanelStrong = Color(0xFF171D29)
private val DetailsText = Color(0xFFD8E0EF)
private val DetailsSubText = Color(0xFFAAB4C8)
private val DetailsBorder = Color(0x3300FF88)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BannerDetailsScreen(
    uiState: BannerDetailsUiState,
    onBack: () -> Unit,
    onDownload: () -> Unit
) {
    Scaffold(
        containerColor = DetailsBackground,
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
        ),
        topBar = {
            DetailsTopBar(
                isSaving = uiState.isSaving,
                onBack = onBack,
                onDownload = onDownload
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                FullImagePanel(imageModel = uiState.imageModel, contentDescription = uiState.details.name)
            }
            item {
                DetailsSummaryPanel(uiState = uiState, onDownload = onDownload)
            }
            item {
                if (uiState.isSaving) {
                    SavingPanel()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailsTopBar(
    isSaving: Boolean,
    onBack: () -> Unit,
    onDownload: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.banner_details_title),
                color = DetailsAccent,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_header_back),
                    contentDescription = stringResource(R.string.history_back),
                    tint = DetailsText
                )
            }
        },
        actions = {
            IconButton(
                enabled = !isSaving,
                onClick = { expanded = true }
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_qr_save),
                    contentDescription = stringResource(R.string.banner_details_menu),
                    tint = if (isSaving) DetailsSubText else DetailsAccent
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.banner_details_download)) },
                    onClick = {
                        expanded = false
                        onDownload()
                    }
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = DetailsHeader)
    )
}

@Composable
private fun FullImagePanel(
    imageModel: Any,
    contentDescription: String
) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DetailsBorder, RoundedCornerShape(8.dp)),
        color = DetailsPanelStrong,
        shape = RoundedCornerShape(8.dp)
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(imageModel)
                .crossfade(true)
                .build(),
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 420.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Fit,
            loading = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 420.dp)
                        .background(DetailsPanelStrong),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = DetailsAccent)
                }
            },
            error = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 420.dp)
                        .background(DetailsPanelStrong)
                        .padding(18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.banner_details_image_failed),
                        color = DetailsSubText,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        )
    }
}

@Composable
private fun DetailsSummaryPanel(
    uiState: BannerDetailsUiState,
    onDownload: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = DetailsPanelStrong,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = uiState.details.name,
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = uiState.details.description,
                color = DetailsSubText,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall
            )
            HorizontalDivider(color = DetailsBorder)
            MetadataRow(uiState = uiState)
            Button(
                enabled = !uiState.isSaving,
                onClick = onDownload,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DetailsAccent,
                    contentColor = DetailsBackground,
                    disabledContainerColor = Color(0xFF26352F),
                    disabledContentColor = DetailsSubText
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_qr_save),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(
                        if (uiState.isSaving) R.string.banner_details_saving
                        else R.string.banner_details_download
                    ),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun MetadataRow(uiState: BannerDetailsUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DetailChip(
            label = stringResource(R.string.banner_details_source_label),
            value = stringResource(
                when (uiState.details.source) {
                    is BannerDetailsSource.Local -> R.string.banner_details_source_local
                    is BannerDetailsSource.Network -> R.string.banner_details_source_network
                }
            )
        )
        DetailChip(
            label = stringResource(R.string.banner_details_filename_label),
            value = uiState.details.downloadFileName
        )
    }
}

@Composable
private fun DetailChip(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x161B2D25), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = DetailsSubText,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            color = DetailsText,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun SavingPanel() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF111722),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.banner_details_saving),
                color = DetailsText,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium
            )
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = DetailsAccent,
                trackColor = Color(0xFF24362F)
            )
        }
    }
}
