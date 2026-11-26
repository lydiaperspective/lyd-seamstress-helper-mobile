package com.lydiaperspective.seamworkstudio

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.room.Room
import com.lydiaperspective.seamworkstudio.data.GarmentProject
import com.lydiaperspective.seamworkstudio.data.InspirationItem
import com.lydiaperspective.seamworkstudio.data.SeamworkDatabase
import kotlinx.coroutines.launch

private val workspaces = listOf("Inspiration", "Sketchbook", "Pattern Designer", "Fabric Layout", "Print & Export")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = SeamworkDatabase.get(applicationContext)
        setContent { SeamworkApp(database) }
    }
}

@Composable
private fun SeamworkApp(database: SeamworkDatabase) {
    val projects by database.projectDao().observeProjects().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var activeProject by remember { mutableStateOf<GarmentProject?>(null) }
    var activeWorkspace by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }

    MaterialTheme(colorScheme = lightColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFF765B49),
        secondary = androidx.compose.ui.graphics.Color(0xFF9B806B),
        background = androidx.compose.ui.graphics.Color(0xFFF7F3EE),
        surface = androidx.compose.ui.graphics.Color(0xFFFFFCF8)
    )) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when {
                activeProject == null -> ProjectHome(
                    projects = projects,
                    create = { showCreate = true },
                    delete = { project -> scope.launch { database.projectDao().deleteProject(project.id) } },
                    open = { activeProject = it }
                )
                activeWorkspace == null -> WorkspaceHome(
                    project = activeProject!!,
                    back = { activeProject = null },
                    open = { activeWorkspace = it }
                )
                activeWorkspace == "Inspiration" -> InspirationWorkspace(
                    project = activeProject!!,
                    database = database,
                    back = { activeWorkspace = null }
                )
                else -> WorkspacePage(activeProject!!, activeWorkspace!!) { activeWorkspace = null }
            }
            if (showCreate) {
                var name by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { showCreate = false },
                    title = { Text("New garment project") },
                    text = { OutlinedTextField(name, { name = it }, label = { Text("Project name") }, singleLine = true) },
                    confirmButton = {
                        TextButton(onClick = {
                            if (name.isNotBlank()) scope.launch { database.projectDao().insertProject(GarmentProject(name = name.trim())) }
                            showCreate = false
                        }) { Text("Create") }
                    },
                    dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } }
                )
            }
        }
    }
}

@Composable
private fun ProjectHome(
    projects: List<GarmentProject>,
    open: (GarmentProject) -> Unit,
    create: () -> Unit,
    delete: (GarmentProject) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(28.dp)) {
        Text("SEAMWORK STUDIO", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text("Your projects", style = MaterialTheme.typography.headlineLarge)
        Text("A workspace for every garment you make.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Button(onClick = create) { Text("+  New project") }
        Spacer(Modifier.height(16.dp))
        if (projects.isEmpty()) {
            Text("Your garment projects will appear here.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(projects, key = { it.id }) { project ->
                    Card(onClick = { open(project) }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(project.name, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                            TextButton(onClick = { delete(project) }) { Text("Delete") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkspaceHome(project: GarmentProject, back: () -> Unit, open: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(28.dp)) {
        TextButton(onClick = back) { Text("<  Projects") }
        Text(project.name, style = MaterialTheme.typography.headlineLarge)
        Text("Choose a workspace", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            workspaces.forEach { workspace ->
                Card(onClick = { open(workspace) }, modifier = Modifier.weight(1f).height(170.dp)) {
                    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        Text(workspace, style = MaterialTheme.typography.titleMedium)
                        Text("Open  →", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun InspirationWorkspace(
    project: GarmentProject,
    database: SeamworkDatabase,
    back: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items by database.inspirationDao().observeItems(project.id).collectAsState(initial = emptyList())
    var showLinkDialog by remember { mutableStateOf(false) }
    var linkTitle by remember { mutableStateOf("") }
    var linkAddress by remember { mutableStateOf("") }

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

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        TextButton(onClick = back) { Text("<  ${project.name}") }
        Text("Inspiration", style = MaterialTheme.typography.headlineLarge)
        Text("Collect visual references and useful web links for this garment.")
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Text("Add photo")
            }
            OutlinedButton(onClick = { showLinkDialog = true }) { Text("Add web link") }
        }
        Spacer(Modifier.height(16.dp))
        if (items.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp)) {
                    Text("Your inspiration board is empty", style = MaterialTheme.typography.titleMedium)
                    Text("Add a photo from your device or save a webpage link. References are stored with this project.")
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(items, key = { it.id }) { item ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (item.kind == "IMAGE") "Photo reference" else item.uri,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2
                                )
                            }
                            TextButton(onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.uri)))
                                }
                            }) { Text("Open") }
                            TextButton(onClick = {
                                scope.launch { database.inspirationDao().delete(item.id) }
                            }) { Text("Remove") }
                        }
                    }
                }
            }
        }
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
private fun WorkspacePage(project: GarmentProject, workspace: String, back: () -> Unit) {
    val description = when (workspace) {
        "Sketchbook" -> "Create front, back and side views. S Pen drawing tools are next."
        "Pattern Designer" -> "Draft pattern pieces with physical measurements and seam allowances."
        "Fabric Layout" -> "Arrange pattern pieces and estimate fabric yardage."
        else -> "Prepare print layouts, tiled PDFs and projector calibration."
    }
    Column(Modifier.fillMaxSize().padding(28.dp)) {
        TextButton(onClick = back) { Text("<  " + project.name) }
        Text(workspace, style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(12.dp))
        Text(description, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(20.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp)) {
                Text("Coming next", style = MaterialTheme.typography.titleMedium)
                Text(
                    "This workspace will be implemented as a working tool in a later development step.",
                    Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
