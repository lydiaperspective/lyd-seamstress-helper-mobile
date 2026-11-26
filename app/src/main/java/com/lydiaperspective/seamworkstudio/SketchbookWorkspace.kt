package com.lydiaperspective.seamworkstudio

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lydiaperspective.seamworkstudio.data.GarmentProject
import com.lydiaperspective.seamworkstudio.data.SeamworkDatabase
import com.lydiaperspective.seamworkstudio.data.SketchPage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private data class InkStroke(
    val color: Int,
    val width: Float,
    val points: List<Offset>
)

private fun encodeStrokes(strokes: List<InkStroke>): String = strokes.joinToString(";") { stroke ->
    val points = stroke.points.joinToString("|") { "${it.x},${it.y}" }
    "${stroke.color},${stroke.width},$points"
}

private fun decodeStrokes(data: String): List<InkStroke> {
    if (data.isBlank()) return emptyList()
    return data.split(";").mapNotNull { encoded ->
        runCatching {
            val fields = encoded.split(",", limit = 3)
            val points = fields[2].split("|").map { pair ->
                val coordinates = pair.split(",")
                Offset(coordinates[0].toFloat(), coordinates[1].toFloat())
            }
            InkStroke(fields[0].toLong().toInt(), fields[1].toFloat(), points)
        }.getOrNull()
    }
}

@Composable
fun SketchbookWorkspace(
    project: GarmentProject,
    database: SeamworkDatabase,
    back: () -> Unit
) {
    val dao = database.sketchPageDao()
    val pages by dao.observePages(project.id).collectAsState(initial = emptyList())
    var selectedPageId by remember(project.id) { mutableStateOf<Long?>(null) }
    val selectedPage = pages.firstOrNull { it.id == selectedPageId } ?: pages.firstOrNull()
    var showNewPageDialog by remember { mutableStateOf(false) }
    var newPageTitle by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(project.id) {
        if (dao.observePages(project.id).first().isEmpty()) {
            val id = dao.insertPage(SketchPage(projectId = project.id, title = "Front view"))
            selectedPageId = id
        }
    }

    Column(
        Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = back) { Text("‹  ${project.name}") }
            Spacer(Modifier.weight(1f))
            Text("SKETCHBOOK", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Sketchbook", style = MaterialTheme.typography.headlineLarge)
                Text("Draw, explore and organize garment ideas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = { newPageTitle = ""; showNewPageDialog = true }) { Text("+ Page") }
        }
        if (pages.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pages.forEach { page ->
                    FilterChip(
                        selected = page.id == selectedPage?.id,
                        onClick = { selectedPageId = page.id },
                        label = { Text(page.title, maxLines = 1) }
                    )
                }
            }
        }
        if (selectedPage != null) {
            SketchPageEditor(
                page = selectedPage,
                save = { updated -> dao.updatePage(updated) },
                modifier = Modifier.weight(1f)
            )
        } else {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }

    if (showNewPageDialog) {
        AlertDialog(
            onDismissRequest = { showNewPageDialog = false },
            title = { Text("New sketch page") },
            text = {
                OutlinedTextField(
                    value = newPageTitle,
                    onValueChange = { newPageTitle = it },
                    label = { Text("Page name") },
                    placeholder = { Text("e.g. Back view") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val title = newPageTitle.trim().ifBlank { "Sketch page ${pages.size + 1}" }
                    scope.launch {
                        val id = dao.insertPage(SketchPage(projectId = project.id, title = title))
                        selectedPageId = id
                    }
                    showNewPageDialog = false
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showNewPageDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SketchPageEditor(
    page: SketchPage,
    save: suspend (SketchPage) -> Unit,
    modifier: Modifier = Modifier
) {
    var strokes by remember(page.id) { mutableStateOf(decodeStrokes(page.drawingData)) }
    var undoHistory by remember(page.id) { mutableStateOf(listOf<List<InkStroke>>()) }
    var redoHistory by remember(page.id) { mutableStateOf(listOf<List<InkStroke>>()) }
    var currentPoints by remember(page.id) { mutableStateOf<List<Offset>>(emptyList()) }
    var tool by remember(page.id) { mutableStateOf("PEN") }
    var brushColor by remember(page.id) { mutableIntStateOf(AndroidColor.parseColor("#765B49")) }
    var brushSize by remember(page.id) { mutableFloatStateOf(3.5f) }
    var paperSize by remember(page.id) { mutableStateOf(page.paperSize) }
    var backgroundStyle by remember(page.id) { mutableStateOf(page.background) }
    var zoom by remember(page.id) { mutableFloatStateOf(1f) }
    var pan by remember(page.id) { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current

    LaunchedEffect(page.id, strokes, paperSize, backgroundStyle) {
        save(page.copy(
            paperSize = paperSize,
            background = backgroundStyle,
            drawingData = encodeStrokes(strokes),
            updatedAt = System.currentTimeMillis()
        ))
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(selected = tool == "PEN", onClick = { tool = "PEN" }, label = { Text("Pen") })
            FilterChip(selected = tool == "ERASER", onClick = { tool = "ERASER" }, label = { Text("Eraser") })
            TextButton(onClick = {
                if (strokes.isNotEmpty()) {
                    undoHistory = undoHistory + listOf(strokes)
                    strokes = strokes.dropLast(1)
                    redoHistory = emptyList()
                }
            }, enabled = strokes.isNotEmpty()) { Text("Undo") }
            TextButton(onClick = {
                val previous = undoHistory.lastOrNull()
                if (previous != null) {
                    redoHistory = redoHistory + listOf(strokes)
                    strokes = previous
                    undoHistory = undoHistory.dropLast(1)
                }
            }, enabled = undoHistory.isNotEmpty()) { Text("Redo") }
            TextButton(onClick = { zoom = 1f; pan = Offset.Zero }) { Text("Reset view") }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("#765B49", "#222222", "#B34F46", "#557B91", "#66805A", "#D39A45").forEach { hex ->
                val color = AndroidColor.parseColor(hex)
                Surface(
                    onClick = { brushColor = color; tool = "PEN" },
                    shape = RoundedCornerShape(50),
                    color = Color(color),
                    modifier = Modifier.size(28.dp),
                    border = if (brushColor == color && tool == "PEN") androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null
                ) {}
            }
            Text("Size", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = brushSize,
                onValueChange = { brushSize = it },
                valueRange = 1f..12f,
                modifier = Modifier.width(110.dp)
            )
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Paper", style = MaterialTheme.typography.labelMedium)
            listOf("A4", "Letter", "Open").forEach { size ->
                FilterChip(selected = paperSize == size, onClick = { paperSize = size }, label = { Text(size) })
            }
            Spacer(Modifier.width(6.dp))
            Text("Page", style = MaterialTheme.typography.labelMedium)
            listOf("GRID", "DOTS", "BLANK").forEach { style ->
                FilterChip(
                    selected = backgroundStyle == style,
                    onClick = { backgroundStyle = style },
                    label = { Text(style.lowercase().replaceFirstChar { it.uppercase() }) }
                )
            }
        }

        BoxWithConstraints(
            Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center
        ) {
            val ratio = when (paperSize) { "A4" -> 0.707f; "Letter" -> 0.773f; else -> 1.35f }
            val canvasWidth = minOf(maxWidth, 900.dp)
            val canvasHeight = minOf(maxHeight, canvasWidth / ratio)
            Box(
                Modifier.width(canvasWidth).height(canvasHeight)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color.White)
            ) {
                val transformState = rememberTransformableState { zoomChange, panChange, _ ->
                    zoom = (zoom * zoomChange).coerceIn(1f, 4f)
                    pan += panChange
                }
                Canvas(
                    Modifier.fillMaxSize()
                        .graphicsLayer {
                            scaleX = zoom
                            scaleY = zoom
                            translationX = pan.x
                            translationY = pan.y
                        }
                        .transformable(transformState)
                        .pointerInput(page.id, tool, brushColor, brushSize) {
                            detectDragGestures(
                                onDragStart = { point ->
                                    currentPoints = listOf(Offset(
                                        (point.x / size.width).coerceIn(0f, 1f),
                                        (point.y / size.height).coerceIn(0f, 1f)
                                    ))
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val point = Offset(
                                        (change.position.x / size.width).coerceIn(0f, 1f),
                                        (change.position.y / size.height).coerceIn(0f, 1f)
                                    )
                                    currentPoints = currentPoints + point
                                },
                                onDragEnd = {
                                    if (currentPoints.isNotEmpty()) {
                                        undoHistory = undoHistory + listOf(strokes)
                                        val color = if (tool == "ERASER") AndroidColor.WHITE else brushColor
                                        strokes = strokes + InkStroke(color, if (tool == "ERASER") brushSize * 4f else brushSize, currentPoints)
                                        redoHistory = emptyList()
                                        currentPoints = emptyList()
                                    }
                                },
                                onDragCancel = { currentPoints = emptyList() }
                            )
                        }
                ) {
                    drawRect(Color.White)
                    val gridColor = Color(0xFFE9E2DB)
                    val step = with(density) { 24.dp.toPx() }
                    when (backgroundStyle) {
                        "GRID" -> {
                            var x = 0f
                            while (x <= size.width) {
                                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), 0.7.dp.toPx())
                                x += step
                            }
                            var y = 0f
                            while (y <= size.height) {
                                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 0.7.dp.toPx())
                                y += step
                            }
                        }
                        "DOTS" -> {
                            var x = step
                            while (x < size.width) {
                                var y = step
                                while (y < size.height) {
                                    drawCircle(gridColor, radius = 1.1.dp.toPx(), center = Offset(x, y))
                                    y += step
                                }
                                x += step
                            }
                        }
                    }
                    (strokes + listOfNotNull(
                        currentPoints.takeIf { it.isNotEmpty() }?.let {
                            InkStroke(if (tool == "ERASER") AndroidColor.WHITE else brushColor, brushSize, it)
                        }
                    )).forEach { stroke ->
                        if (stroke.points.size == 1) {
                            drawCircle(
                                Color(stroke.color),
                                radius = stroke.width.dp.toPx() / 2f,
                                center = Offset(stroke.points[0].x * size.width, stroke.points[0].y * size.height)
                            )
                        } else {
                            val path = Path().apply {
                                moveTo(stroke.points.first().x * size.width, stroke.points.first().y * size.height)
                                stroke.points.drop(1).forEach { lineTo(it.x * size.width, it.y * size.height) }
                            }
                            drawPath(
                                path,
                                Color(stroke.color),
                                style = Stroke(width = stroke.width.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }
                }
            }
        }
        Text(
            "Pinch to zoom • Drag with the S Pen or finger to draw • Your page saves automatically",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
