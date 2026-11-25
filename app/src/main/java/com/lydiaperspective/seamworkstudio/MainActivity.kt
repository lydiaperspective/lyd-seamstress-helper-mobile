package com.lydiaperspective.seamworkstudio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val workspaces = listOf("Inspiration", "Sketchbook", "Pattern Designer", "Fabric Layout", "Print & Export")
private data class GarmentProject(val id: Int, val name: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SeamworkApp() }
    }
}

@Composable
private fun SeamworkApp() {
    var projects by remember { mutableStateOf(listOf(GarmentProject(1, "First garment"))) }
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
                activeProject == null -> ProjectHome(projects, { activeProject = it }, { showCreate = true }) {
                    project -> projects = projects.filterNot { it.id == project.id }
                }
                activeWorkspace == null -> WorkspaceHome(activeProject!!, { activeProject = null }) {
                    activeWorkspace = it
                }
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
                            if (name.isNotBlank()) projects = projects + GarmentProject((projects.maxOfOrNull { it.id } ?: 0) + 1, name.trim())
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
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(projects, key = { it.id }) { project ->
                Card(Modifier.fillMaxWidth(), onClick = { open(project) }) {
                    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(project.name, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                        TextButton(onClick = { delete(project) }) { Text("Delete") }
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
                Card(Modifier.weight(1f).height(170.dp), onClick = { open(workspace) }) {
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
private fun WorkspacePage(project: GarmentProject, workspace: String, back: () -> Unit) {
    val description = when (workspace) {
        "Inspiration" -> "Collect garment photos and web references for this project."
        "Sketchbook" -> "Create front, back and side views. S Pen drawing tools are planned here."
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
                Text("Workspace foundation", style = MaterialTheme.typography.titleMedium)
                Text("Native Kotlin + Jetpack Compose screen. Editing tools and persistent storage are the next development steps.",
                    Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
