package com.lydiaperspective.seamworkstudio

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.TransformableState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.lydiaperspective.seamworkstudio.data.GarmentProject
import com.lydiaperspective.seamworkstudio.data.InspirationItem
import com.lydiaperspective.seamworkstudio.data.SeamworkDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InspirationWorkspace(
    project: GarmentProject,
    database: SeamworkDatabase,
    back: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items by database.inspirationDao().observeItems(project.id).collectAsState(initial = emptyList())
    var showLinkDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var linkTitle by remember { mutableStateOf("") }
    var linkAddress by remember { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var fullScreenItem by remember { mutableStateOf<InspirationItem?>(null) }
    val selecting = selectedIds.isNotEmpty()

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) { }
            val title = uri.lastPathSegment?.substringAfterLast('/')?.take(48)?.ifBlank { "Photo reference" } ?: "Photo reference"
            scope.launch {
                database.inspirationDao().insert(
                    InspirationItem(projectId = project.id, title = title, kind = "IMAGE", uri = uri.toString())
                )
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 22.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = back) { Text("<  ${project.name}") }
            Spacer(Modifier.weight(1f))
            if (selecting) {
                Text("${selectedIds.size} selected", style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = { selectedIds = emptySet() }) { Text("Cancel") }
                Button(onClick = { showDeleteDialog = true }) { Text("Delete") }
            }
        }
        Text("Inspiration", style = MaterialTheme.typography.headlineLarge)
        Text("A visual gallery for this garment", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))
        if (!selecting) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Text("Add photo")
                }
                OutlinedButton(onClick = { showLinkDialog = true }) { Text("Add web link") }
            }
        } else {
            Text("Tap references to add or remove them from your selection.", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(14.dp))

        if (items.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp)) {
                    Text("Your inspiration gallery is empty", style = MaterialTheme.typography.titleMedium)
                    Text("Add photos or save web pages. Image previews will appear here when available.")
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 170.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    val isSelected = item.id in selectedIds
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (selecting) {
                                        selectedIds = if (isSelected) selectedIds - item.id else selectedIds + item.id
                                    } else if (item.kind == "IMAGE") {
                                        fullScreenItem = item
                                    } else {
                                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.uri))) }
                                    }
                                },
                                onLongClick = {
                                    selectedIds = if (isSelected) selectedIds - item.id else selectedIds + item.id
                                }
                            ),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column {
                            Box(Modifier.fillMaxWidth().height(190.dp)) {
                                if (item.kind == "IMAGE") {
                                    AsyncImage(
                                        model = item.uri,
                                        contentDescription = item.title,
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    WebReferencePreview(url = item.uri, title = item.title)
                                }
                                if (isSelected) {
                                    Surface(
                                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                                        shape = RoundedCornerShape(50),
                                        color = MaterialTheme.colorScheme.primary
                                    ) {
                                        Text("✓", color = Color.White, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                                    }
                                }
                            }
                            Text(
                                item.title,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }

    fullScreenItem?.let { item ->
        FullScreenInspirationImage(item = item, onDismiss = { fullScreenItem = null })
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Remove selected references?") },
            text = { Text("This will permanently remove ${selectedIds.size} selected ${if (selectedIds.size == 1) "item" else "items"} from this project.") },
            confirmButton = {
                TextButton(onClick = {
                    val ids = selectedIds.toList()
                    scope.launch {
                        ids.forEach { database.inspirationDao().delete(it) }
                        selectedIds = emptySet()
                    }
                    showDeleteDialog = false
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Keep") } }
        )
    }

    if (showLinkDialog) {
        AlertDialog(
            onDismissRequest = { showLinkDialog = false },
            title = { Text("Save a web reference") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(linkTitle, { linkTitle = it }, label = { Text("Title") }, singleLine = true)
                    OutlinedTextField(linkAddress, { linkAddress = it }, label = { Text("Web address") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val raw = linkAddress.trim()
                    val address = if (raw.startsWith("https://") || raw.startsWith("http://")) raw else "https://$raw"
                    val valid = runCatching { Uri.parse(address).let { it.scheme in listOf("http", "https") && !it.host.isNullOrBlank() } }.getOrDefault(false)
                    if (valid) {
                        scope.launch {
                            database.inspirationDao().insert(
                                InspirationItem(
                                    projectId = project.id,
                                    title = linkTitle.trim().ifBlank { Uri.parse(address).host ?: "Web reference" },
                                    kind = "LINK",
                                    uri = address
                                )
                            )
                        }
                        linkTitle = ""
                        linkAddress = ""
                        showLinkDialog = false
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showLinkDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun WebReferencePreview(url: String, title: String) {
    val imageUrl by produceState<String?>(initialValue = null, url) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val doc = Jsoup.connect(url).userAgent("Mozilla/5.0 (compatible; SeamworkStudio/1.0)")
                    .timeout(8000).get()
                doc.selectFirst("meta[property=og:image]")?.attr("content")
                    ?.takeIf { it.isNotBlank() }
                    ?: doc.selectFirst("meta[name=twitter:image]")?.attr("content")?.takeIf { it.isNotBlank() }
            }.getOrNull()
        }
    }
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Column(
                Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("↗", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("Web reference", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FullScreenInspirationImage(item: InspirationItem, onDismiss: () -> Unit) {
    var scale by remember(item.id) { mutableFloatStateOf(1f) }
    var offsetX by remember(item.id) { mutableFloatStateOf(0f) }
    var offsetY by remember(item.id) { mutableFloatStateOf(0f) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 6f)
        offsetX += panChange.x
        offsetY += panChange.y
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            Modifier.fillMaxSize().clip(RoundedCornerShape(0.dp)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = item.uri,
                contentDescription = item.title,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    }
                    .transformable(transformState)
                    .pointerInput(item.id) { detectTapGestures(onTap = { onDismiss() }) },
                contentScale = ContentScale.Fit
            )
            Text(
                "Tap anywhere to close",
                modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
