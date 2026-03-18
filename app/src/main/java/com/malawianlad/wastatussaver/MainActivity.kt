package com.malawianlad.wastatussaver

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.malawianlad.wastatussaver.ui.StatusFile
import com.malawianlad.wastatussaver.ui.StatusType
import com.malawianlad.wastatussaver.ui.StatusUiState
import com.malawianlad.wastatussaver.ui.StatusViewModel
import com.malawianlad.wastatussaver.ui.StatusViewModelFactory
import com.malawianlad.wastatussaver.ui.theme.WAStatusSaverTheme

// ─────────────────────────────────────────────────────────────────────────────
// Colour tokens  (WhatsApp-style dark palette)
// ─────────────────────────────────────────────────────────────────────────────

private val WaGreen   = Color(0xFF00A884)   // primary action colour
private val WaBg      = Color(0xFF111B21)   // page / scaffold background
private val WaSurface = Color(0xFF1F2C34)   // top bar / card surface
private val WaPanel   = Color(0xFF2A3942)   // secondary surface (info cards)
private val WaMuted   = Color(0xFF8696A0)   // secondary text / icons

// ─────────────────────────────────────────────────────────────────────────────
// Activity
// ─────────────────────────────────────────────────────────────────────────────

class MainActivity : ComponentActivity() {


    private lateinit var viewModel: StatusViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        // SplashScreen must be installed before super.onCreate() and before setContent()
        installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Manually create the ViewModel because it needs an Application argument.
        // StatusViewModelFactory handles this — without it the default provider crashes.
        viewModel = ViewModelProvider(
            this,
            StatusViewModelFactory(application)
        )[StatusViewModel::class.java]

        setContent {
            WAStatusSaverTheme {
                App(viewModel = viewModel, activity = this)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Root composable
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(viewModel: StatusViewModel, activity: Activity) {
    val context    = LocalContext.current
    val uiState    by viewModel.uiState.collectAsStateWithLifecycle()
    val saveResult by viewModel.saveResult.collectAsStateWithLifecycle()

    // Tracks which file the user tapped — non-null = show the preview overlay
    var previewFile by remember { mutableStateOf<StatusFile?>(null) }

    // ── SAF launchers ─────────────────────────────────────────────────────────
    //
    // ActivityResultContracts.OpenDocumentTree() shows the system file picker.
    // The user must navigate INTO the .Statuses folder and tap "Use this folder".
    // We then call takePersistableUriPermission so the grant survives reboots.

    val waLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                activity.contentResolver.takePersistableUriPermission(uri, takeFlags)
                viewModel.saveWaUri(uri)
                Log.i("MainActivity", "Saved WA URI: $uri")

                // Log persisted permissions for debugging
                val perms = activity.contentResolver.persistedUriPermissions
                    .joinToString("; ") { p -> "uri=${p.uri} read=${p.isReadPermission} write=${p.isWritePermission}" }
                Log.i("MainActivity", "Persisted URI permissions: $perms")

                viewModel.loadStatuses()
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to take persistable permission for $uri", e)
            }
        }
    }

    val waBizLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                activity.contentResolver.takePersistableUriPermission(uri, takeFlags)
                viewModel.saveWaBizUri(uri)
                Log.i("MainActivity", "Saved WA Biz URI: $uri")

                // Log persisted permissions for debugging
                val perms = activity.contentResolver.persistedUriPermissions
                    .joinToString("; ") { p -> "uri=${p.uri} read=${p.isReadPermission} write=${p.isWritePermission}" }
                Log.i("MainActivity", "Persisted URI permissions: $perms")

                viewModel.loadStatuses()
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to take persistable permission for $uri", e)
            }
        }
    }

    // Auto-load on first composition if the user already granted access before
    LaunchedEffect(Unit) {
        if (viewModel.hasAnyUri()) {
            viewModel.loadStatuses()
        }
    }

    // Show save feedback as a short Toast then clear it so it doesn't repeat
    LaunchedEffect(saveResult) {
        saveResult?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearSaveResult()
        }
    }

    // ── Scaffold ──────────────────────────────────────────────────────────────

    Scaffold(
        containerColor = WaBg,
        topBar = {
            TopAppBar(
                title = {
                    // Two-line title: app name (bold) + subtitle with counts
                    Column {
                        Text(
                            text = "WA StatusSaver",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                        val subtitle = (uiState as? StatusUiState.Success)?.let {
                            val images = it.images.size
                            val videos = it.videos.size
                            "${images} images · ${videos} videos"
                        } ?: ""
                        if (subtitle.isNotEmpty()) {
                            Text(
                                text = subtitle,
                                fontSize = 12.sp,
                                color = WaMuted
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = WaSurface
                ),
                actions = {
                    // Only show action buttons if at least one folder is granted
                    if (viewModel.hasAnyUri()) {
                        IconButton(onClick = { viewModel.loadStatuses() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = WaMuted
                            )
                        }
                        IconButton(onClick = { waLauncher.launch(waHintUri()) }) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "Change folder",
                                tint = WaMuted
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {

            if (!viewModel.hasAnyUri()) {
                // No folder granted → guide the user through setup
                val waUri = viewModel.getWaUri()
                val waBizUri = viewModel.getWaBizUri()
                val permsInfo = activity.contentResolver.persistedUriPermissions
                    .joinToString("\n") { p -> "uri=${p.uri} read=${p.isReadPermission} write=${p.isWritePermission}" }

                PermissionScreen(
                    onPickWa    = { waLauncher.launch(waHintUri()) },
                    onPickWaBiz = { waBizLauncher.launch(waBizHintUri()) },
                    waUri = waUri,
                    waBizUri = waBizUri,
                    persistedInfo = permsInfo,
                    onClearUris = {
                        viewModel.clearWaUri()
                        viewModel.clearWaBizUri()
                    }
                )
            } else {
                // Folder granted → show the status grid
                StatusScreen(
                    viewModel = viewModel,
                    uiState     = uiState,
                    onFileClick = { previewFile = it },
                    onSave      = { viewModel.saveStatus(it) }
                )
            }

            // Full-screen preview overlay — rendered on top of everything else
            previewFile?.let { file ->
                PreviewOverlay(
                    file      = file,
                    onDismiss = { previewFile = null },
                    onSave    = {
                        viewModel.saveStatus(file)
                        previewFile = null
                    }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SAF hint URIs
//
// These hint the system file picker to open at the correct location by default.
// If the URI is not valid on this device the picker simply opens at storage root.
// ─────────────────────────────────────────────────────────────────────────────

fun waHintUri(): Uri? = runCatching {
    Uri.parse(
        "content://com.android.externalstorage.documents/tree/" +
        "primary%3AAndroid%2Fmedia%2Fcom.whatsapp%2FWhatsApp%2FMedia%2F.Statuses"
    )
}.getOrNull()

fun waBizHintUri(): Uri? = runCatching {
    Uri.parse(
        "content://com.android.externalstorage.documents/tree/" +
        "primary%3AAndroid%2Fmedia%2Fcom.whatsapp.w4b%2FWhatsApp+Business%2FMedia%2F.Statuses"
    )
}.getOrNull()

// ─────────────────────────────────────────────────────────────────────────────
// Permission / Setup screen
//
// Shown when hasAnyUri() == false. Guides the user to pick both folders.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PermissionScreen(
    onPickWa:    () -> Unit,
    onPickWaBiz: () -> Unit,
    waUri: Uri?,
    waBizUri: Uri?,
    persistedInfo: String,
    onClearUris: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WaBg)
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Debug area: show saved URIs and persisted permissions (helps diagnose why app returns to grant screen)
        if (waUri != null || waBizUri != null) {
            Surface(
                color = WaPanel,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(text = "Saved URIs:", color = WaGreen, fontWeight = FontWeight.SemiBold)
                    Text(text = "WA: ${waUri ?: "(none)"}", color = WaMuted, fontSize = 12.sp)
                    Text(text = "WA Biz: ${waBizUri ?: "(none)"}", color = WaMuted, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(text = "Persisted permissions:", color = WaGreen, fontWeight = FontWeight.SemiBold)
                    Text(text = if (persistedInfo.isNotEmpty()) persistedInfo else "(none)", color = WaMuted, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onClearUris, colors = ButtonDefaults.buttonColors(containerColor = WaGreen)) {
                        Text(text = "Reset saved URIs", color = Color.White)
                    }
                }
            }
        }

        // Hero icon with pulsing ring
        val transition = rememberInfiniteTransition()
        val scale by transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(animation = tween(600), repeatMode = RepeatMode.Reverse)
        )

        Box(
            modifier = Modifier
                .size(80.dp)
                .scale(scale)
                .background(WaGreen.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = null,
                tint = WaGreen,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = "Grant folder access",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = "Android 11+ hides the .Statuses folder. You need to open it manually " +
                   "once. Tap a button below, navigate all the way into the .Statuses folder, " +
                   "then tap \"Use this folder\".",
            fontSize = 14.sp,
            color = WaMuted,
            textAlign = TextAlign.Center,
            lineHeight = 21.sp
        )

        Spacer(Modifier.height(28.dp))

        // ── WhatsApp ──────────────────────────────────────────────────────────

        FolderCard(
            appName = "WhatsApp",
            path    = "Android › media › com.whatsapp › WhatsApp › Media › .Statuses"
        )

        Spacer(Modifier.height(10.dp))

        Button(
            onClick  = onPickWa,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = WaGreen),
            shape  = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = null,
                tint = Color.White
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Pick WhatsApp .Statuses folder",
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(22.dp))

        // ── WhatsApp Business ─────────────────────────────────────────────────

        FolderCard(
            appName = "WhatsApp Business",
            path    = "Android › media › com.whatsapp.w4b › WhatsApp Business › Media › .Statuses"
        )

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick  = onPickWaBiz,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape  = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = null,
                tint = WaGreen
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Pick WhatsApp Business folder",
                fontWeight = FontWeight.SemiBold,
                color = WaGreen
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "You only need to do this once. The app remembers permanently.",
            fontSize = 12.sp,
            color = WaMuted,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Small info card showing the folder path the user needs to navigate to.
 */
@Composable
fun FolderCard(appName: String, path: String) {
    Surface(
        color    = WaPanel,
        shape    = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = appName,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = WaGreen
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = path,
                fontSize = 12.sp,
                color = WaMuted,
                lineHeight = 18.sp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Main status screen  (shown after folder access is granted)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun StatusScreen(
    viewModel: StatusViewModel,
    uiState:     StatusUiState,
    onFileClick: (StatusFile) -> Unit,
    onSave:      (StatusFile) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    val selectedUris by viewModel.selectedFiles.collectAsStateWithLifecycle()
    val recentlySaved by viewModel.recentlySaved.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WaBg)
    ) {
        // Tab row — switches between Images and Videos
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor   = WaSurface,
            contentColor     = WaGreen
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick  = { selectedTab = 0 },
                text     = {
                    Text(
                        text  = "Images",
                        color = if (selectedTab == 0) WaGreen else WaMuted
                    )
                }
            )
            Tab(
                selected = selectedTab == 1,
                onClick  = { selectedTab = 1 },
                text     = {
                    Text(
                        text  = "Videos",
                        color = if (selectedTab == 1) WaGreen else WaMuted
                    )
                }
            )
        }

        // Recently saved row (only visible when non-empty)
        if (recentlySaved.isNotEmpty()) {
            Column(modifier = Modifier.padding(start = 8.dp, top = 8.dp)) {
                Text(text = "Recently Saved", color = Color.White, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 8.dp)) {
                    items(recentlySaved) { file ->
                        AsyncImage(
                            model = file.uri,
                            contentDescription = file.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onFileClick(file) }
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                }
            }
        }

        // Render based on current scan state
        when (uiState) {

            is StatusUiState.Idle,
            is StatusUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = WaGreen)
                }
            }

            is StatusUiState.Empty -> {
                EmptyState(onRefresh = { viewModel.loadStatuses() })
            }

            is StatusUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text      = uiState.message,
                        color     = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            }

            is StatusUiState.Success -> {
                // Select the correct list for the active tab
                val files = if (selectedTab == 0) uiState.images else uiState.videos

                if (files.isEmpty()) {
                    EmptyState(
                        message = if (selectedTab == 0)
                            "No image statuses found.\nOpen WhatsApp → Status tab and view some statuses first."
                        else
                            "No video statuses found.\nOpen WhatsApp → Status tab and view some statuses first.",
                        onRefresh = { viewModel.loadStatuses() }
                    )
                } else {
                    // LazyVerticalGrid renders only the items currently on screen,
                    // which keeps memory low even with many high-res thumbnails.
                    LazyVerticalGrid(
                        columns        = GridCells.Fixed(3),
                        contentPadding = PaddingValues(4.dp),
                        modifier       = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = files,
                            key   = { it.uri.toString() }  // stable key prevents recomposition flicker
                        ) { file ->
                            StatusThumbnail(
                                file    = file,
                                onClick = { onFileClick(file) },
                                onSave  = { onSave(file) },
                                isSelected = selectedUris.contains(file.uri),
                                onToggleSelect = { viewModel.toggleSelection(it) }
                            )
                        }
                    }

                    // Bottom action bar for batch actions
                    if (selectedUris.isNotEmpty()) {
                        Box(modifier = Modifier
                            .fillMaxWidth()
                            .background(WaSurface)
                            .padding(8.dp)) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Button(onClick = {
                                    // Build list of StatusFile from current files across both tabs
                                    val toSave = (uiState.images + uiState.videos).filter { selectedUris.contains(it.uri) }
                                    viewModel.batchSave(toSave)
                                }, colors = ButtonDefaults.buttonColors(containerColor = WaGreen)) {
                                    Text(text = "Save all (${selectedUris.size})", color = Color.White)
                                }

                                OutlinedButton(onClick = { viewModel.clearSelection() }) {
                                    Text(text = "Cancel")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Status thumbnail  (grid item) — updated for multi-select and scrim
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun StatusThumbnail(
    file:    StatusFile,
    onClick: () -> Unit,
    onSave:  () -> Unit,
    isSelected: Boolean,
    onToggleSelect: (Uri) -> Unit
) {
    val borderColor = if (isSelected) WaGreen else Color.Transparent

    Box(
        modifier = Modifier
            .padding(2.dp)
            .aspectRatio(1f)                          // always square
            .clip(RoundedCornerShape(6.dp))
            .border(width = if (isSelected) 3.dp else 0.dp, color = borderColor, shape = RoundedCornerShape(6.dp))
            .combinedClickable(
                onClick = {
                    if (isSelected || false) { // selection mode active if any selected — parent handed isSelected per item
                        onToggleSelect(file.uri)
                    } else {
                        onClick()
                    }
                },
                onLongClick = {
                    onToggleSelect(file.uri)
                }
            )
    ) {
        // Coil loads the SAF URI asynchronously on a background thread,
        // scales it to fill the square, and caches it in memory automatically.
        AsyncImage(
            model              = file.uri,
            contentDescription = file.name,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier.fillMaxSize()
        )

        // Subtle gradient scrim at bottom so overlayed buttons remain readable
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .height(28.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f))))
        ) {}

        // Semi-transparent overlay + play icon for video items
        if (file.type == StatusType.VIDEO) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.20f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = "Video",
                    tint = Color.White,
                    modifier = Modifier.size(38.dp)
                )
            }
        }

        // Quick-save button in the bottom-right corner
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(5.dp)
                .size(30.dp)
                .background(Color.Black.copy(alpha = 0.60f), CircleShape)
                .clickable { onSave() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = "Save",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }

        // Selected overlay: green checkmark top-left
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(22.dp)
                    .background(WaGreen, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Preview overlay  (full-screen, shown when a thumbnail is tapped)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PreviewOverlay(
    file:      StatusFile,
    onDismiss: () -> Unit,
    onSave:    () -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
    ) {
        // ── Media content ──────────────────────────────────────────────────────
        when (file.type) {
            StatusType.IMAGE -> {
                AsyncImage(
                    model              = file.uri,
                    contentDescription = file.name,
                    contentScale       = ContentScale.Fit,
                    modifier           = Modifier.fillMaxSize()
                )
            }
            StatusType.VIDEO -> {
                VideoPreview(
                    uri      = file.uri,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                )
            }
        }

        // ── Top bar  (back button + filename + save + share button) ───────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Color.Black.copy(alpha = 0.55f))
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            Text(
                text     = file.name,
                color    = Color.White,
                fontSize = 13.sp,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                maxLines = 1
            )

            IconButton(onClick = {
                // Share intent
                try {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                        type = if (file.type == StatusType.IMAGE) "image/*" else "video/*"
                        putExtra(Intent.EXTRA_STREAM, file.uri)
                        clipData = ClipData.newRawUri("uri", file.uri)
                    }
                    val chooser = Intent.createChooser(intent, "Share via")
                    context.startActivity(chooser)
                } catch (e: Exception) {
                    Log.e("PreviewOverlay", "Share failed", e)
                }
            }) {
                Icon(imageVector = Icons.Default.Share, contentDescription = "Share", tint = Color.White)
            }

            Button(
                onClick = onSave,
                colors  = ButtonDefaults.buttonColors(containerColor = WaGreen),
                shape   = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text       = "Save",
                    color      = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.width(8.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Video player composable
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun VideoPreview(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // remember(uri) ensures the player is only recreated when a different
    // video URI is passed in. Recompositions from unrelated state changes
    // (e.g. screen rotation within the same video) reuse the existing player.
    val exoPlayer = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
    }

    // DisposableEffect releases the player when this composable leaves
    // the composition (i.e. the user closes the preview overlay).
    // Without this the player continues holding the audio session and
    // consuming memory in the background — an ANR or OOM will eventually result.
    DisposableEffect(exoPlayer) {
        onDispose {
            try {
                exoPlayer.stop()
                exoPlayer.release()
            } catch (e: Exception) {
                Log.e("VideoPreview", "Error releasing ExoPlayer", e)
            }
        }
    }

    // AndroidView bridges ExoPlayer's traditional PlayerView (Android View system)
    // into the Compose UI tree.
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player        = exoPlayer
                useController = true   // shows the built-in play/pause + seek bar
            }
        },
        modifier = modifier
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty state
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun EmptyState(
    message: String = "No statuses found.\n" +
                      "Open WhatsApp → Status tab and view some statuses first.",
    onRefresh: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WaBg)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(WaGreen.copy(alpha = 0.10f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = WaGreen.copy(alpha = 0.50f)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text       = message,
                color      = WaMuted,
                textAlign  = TextAlign.Center,
                lineHeight = 22.sp,
                fontSize   = 14.sp
            )

            if (onRefresh != null) {
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRefresh, colors = ButtonDefaults.buttonColors(containerColor = WaGreen)) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(6.dp))
                    Text(text = "Refresh", color = Color.White)
                }
            }
        }
    }
}