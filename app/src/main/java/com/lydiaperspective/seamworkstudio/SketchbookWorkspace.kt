package com.lydiaperspective.seamworkstudio

import android.content.Context
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Path as NativePath
import android.view.View
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Paint as ComposePaint
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import com.lydiaperspective.seamworkstudio.data.GarmentProject
import com.lydiaperspective.seamworkstudio.data.SeamworkDatabase
import com.lydiaperspective.seamworkstudio.data.SketchPage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private data class InkStroke(val color: Int, val width: Float, val points: List<Offset>)

private class LiveInkView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private var points: List<Offset> = emptyList()
    private var inkColor: Int = AndroidColor.BLACK
    private var inkWidth: Float = 3f

    fun showStroke(color: Int, widthDp: Float, newPoints: List<Offset>) {
        inkColor = color
        inkWidth = widthDp * resources.displayMetrics.density
        points = newPoints
        invalidate()
    }

    fun clearStroke() {
        points = emptyList()
        invalidate()
    }

    override fun onDraw(canvas: AndroidCanvas) {
        super.onDraw(canvas)
        if (points.isEmpty()) return
        paint.color = inkColor
        paint.strokeWidth = inkWidth
        paint.xfermode = if (inkColor == AndroidColor.TRANSPARENT) PorterDuffXfermode(PorterDuff.Mode.CLEAR) else null
        val path = NativePath().apply {
            moveTo(points.first().x * width, points.first().y * height)
            points.drop(1).forEach { lineTo(it.x * width, it.y * height) }
        }
        if (points.size == 1) {
            paint.style = Paint.Style.FILL
            canvas.drawCircle(points[0].x * width, points[0].y * height, inkWidth / 2f, paint)
            paint.style = Paint.Style.STROKE
        } else {
            canvas.drawPath(path, paint)
        }
    }
}

private fun encodeStrokes(strokes: List<InkStroke>) = strokes.joinToString(";") { stroke ->
    "${stroke.color},${stroke.width}," + stroke.points.joinToString("|") { "${it.x},${it.y}" }
}

private fun decodeStrokes(data: String): List<InkStroke> {
    if (data.isBlank()) return emptyList()
    return data.split(";").mapNotNull { encoded ->
        runCatching {
            val fields = encoded.split(",", limit = 3)
            val points = fields[2].split("|").map { pair ->
                val xy = pair.split(",")
                Offset(xy[0].toFloat(), xy[1].toFloat())
            }
            InkStroke(fields[0].toInt(), fields[1].toFloat(), points)
        }.getOrNull()
    }
}

@Composable
fun SketchbookWorkspace(project: GarmentProject, database: SeamworkDatabase, back: () -> Unit) {
    val dao = database.sketchPageDao()
    val pages by dao.observePages(project.id).collectAsState(initial = emptyList())
    var selectedPageId by remember(project.id) { mutableStateOf<Long?>(null) }
    val selectedPage = pages.firstOrNull { it.id == selectedPageId } ?: pages.firstOrNull()
    var showNewPageDialog by remember { mutableStateOf(false) }
    var newPageTitle by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(project.id) {
        if (dao.observePages(project.id).first().isEmpty()) {
            selectedPageId = dao.insertPage(SketchPage(projectId = project.id, title = "Front view"))
        }
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        val compactLayout = maxWidth < 600.dp
        Box(Modifier.fillMaxSize()) {
            if (selectedPage != null) {
                SketchPageEditor(
                    page = selectedPage,
                    save = { dao.updatePage(it) },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            if (selectedPage != null) {
                if (compactLayout) {
                    Row(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 10.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
                            .horizontalScroll(rememberScrollState()).padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilledTonalIconButton(onClick = back) { Text("‹") }
                        pages.forEachIndexed { index, page ->
                            FilledTonalButton(
                                onClick = { selectedPageId = page.id },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (page.id == selectedPage.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) { Text("${index + 1}") }
                        }
                        FilledTonalIconButton(onClick = { newPageTitle = ""; showNewPageDialog = true }) { Text("+") }
                    }
                } else {
                    Column(
                        Modifier.align(Alignment.CenterStart).width(58.dp).padding(5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalIconButton(onClick = back) { Text("‹") }
                        Text("PAGES", style = MaterialTheme.typography.labelSmall)
                        pages.forEachIndexed { index, page ->
                            FilledTonalButton(
                                onClick = { selectedPageId = page.id },
                                contentPadding = PaddingValues(4.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (page.id == selectedPage.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) { Text("${index + 1}", maxLines = 1) }
                        }
                        FilledTonalIconButton(onClick = { newPageTitle = ""; showNewPageDialog = true }) { Text("+") }
                    }
                }
            }
        }
        if (showNewPageDialog) {
            AlertDialog(
                onDismissRequest = { showNewPageDialog = false },
                title = { Text("New sketch page") },
                text = {
                    OutlinedTextField(
                        value = newPageTitle, onValueChange = { newPageTitle = it },
                        label = { Text("Page name") }, placeholder = { Text("e.g. Back view") }, singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            val id = dao.insertPage(SketchPage(
                                projectId = project.id,
                                title = newPageTitle.trim().ifBlank { "Sketch page ${pages.size + 1}" }
                            ))
                            selectedPageId = id
                        }
                        showNewPageDialog = false
                    }) { Text("Create") }
                },
                dismissButton = { TextButton(onClick = { showNewPageDialog = false }) { Text("Cancel") } }
            )
        }
    }
}

@Composable
private fun SketchPageEditor(page: SketchPage, save: suspend (SketchPage) -> Unit, modifier: Modifier = Modifier) {
    var strokes by remember(page.id) { mutableStateOf(decodeStrokes(page.drawingData)) }
    var undoHistory by remember(page.id) { mutableStateOf(listOf<List<InkStroke>>()) }
    var redoHistory by remember(page.id) { mutableStateOf(listOf<List<InkStroke>>()) }
    val liveInkView = remember(page.id) { mutableStateOf<LiveInkView?>(null) }
    var tool by remember(page.id) { mutableStateOf("PEN") }
    var penOnly by remember(page.id) { mutableStateOf(true) }
    var sidebarVisible by remember(page.id) { mutableStateOf(false) }
    var brushColor by remember(page.id) { mutableIntStateOf(AndroidColor.parseColor("#765B49")) }
    var brushSize by remember(page.id) { mutableFloatStateOf(3.5f) }
    var paperSize by remember(page.id) { mutableStateOf(page.paperSize) }
    var backgroundStyle by remember(page.id) { mutableStateOf(page.background) }
    var zoom by remember(page.id) { mutableFloatStateOf(1f) }
    var pan by remember(page.id) { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current

    LaunchedEffect(page.id, strokes, paperSize, backgroundStyle) {
        save(page.copy(
            paperSize = paperSize, background = backgroundStyle,
            drawingData = encodeStrokes(strokes), updatedAt = System.currentTimeMillis()
        ))
    }

    Box(modifier.fillMaxSize().background(Color(0xFFECE8E2))) {
        Box(Modifier.fillMaxSize()) {
            BoxWithConstraints(
                Modifier.fillMaxSize().padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                val ratio = when (paperSize) { "A4" -> 0.707f; "Letter" -> 0.773f; else -> 1.35f }
                val canvasWidth = if (paperSize == "Open") maxWidth else minOf(maxWidth, maxHeight * ratio)
                val canvasHeight = if (paperSize == "Open") maxHeight else canvasWidth / ratio
                Box(
                    Modifier.width(canvasWidth).height(canvasHeight)
                        .then(if (paperSize == "Open") Modifier.background(Color.White) else Modifier.clip(RoundedCornerShape(4.dp)).background(Color.White))
                        .graphicsLayer {
                            scaleX = zoom; scaleY = zoom
                            translationX = pan.x; translationY = pan.y
                        }
                        .pointerInput(penOnly, tool, brushColor, brushSize, zoom) {
                            awaitEachGesture {
                                val down = awaitPointerEvent()
                                val pointer = down.changes.firstOrNull { it.pressed } ?: return@awaitEachGesture
                                if (pointer.type != PointerType.Stylus && penOnly) {
                                    do {
                                        val event = awaitPointerEvent()
                                    } while (event.changes.any { it.pressed })
                                    return@awaitEachGesture
                                }
                                val points = mutableListOf(
                                    Offset(
                                        (pointer.position.x / size.width).coerceIn(0f, 1f),
                                        (pointer.position.y / size.height).coerceIn(0f, 1f)
                                    )
                                )
                                val liveColor = if (tool == "ERASER") AndroidColor.TRANSPARENT else brushColor
                                val liveWidth = if (tool == "ERASER") brushSize * 4f else brushSize
                                liveInkView.value?.showStroke(liveColor, liveWidth, points)
                                var active = true
                                while (active) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == pointer.id }
                                    if (event.changes.count { it.pressed } >= 2) {
                                        active = false
                                        points.clear()
                                    } else if (change == null || !change.pressed) {
                                        active = false
                                    } else {
                                        val next = Offset(
                                            (change.position.x / size.width).coerceIn(0f, 1f),
                                            (change.position.y / size.height).coerceIn(0f, 1f)
                                        )
                                        if (points.lastOrNull() != next) {
                                            points.add(next)
                                            liveInkView.value?.showStroke(liveColor, liveWidth, points)
                                        }
                                        change.consume()
                                    }
                                }
                                if (points.isNotEmpty()) {
                                    undoHistory = undoHistory + listOf(strokes)
                                    val color = if (tool == "ERASER") AndroidColor.TRANSPARENT else brushColor
                                    val width = if (tool == "ERASER") brushSize * 4f else brushSize
                                    strokes = strokes + InkStroke(color, width, points.toList())
                                    redoHistory = emptyList()
                                }
                                liveInkView.value?.clearStroke()
                            }
                        }
                        .pointerInput(penOnly) {
                            awaitEachGesture {
                                do {
                                    val event = awaitPointerEvent()
                                    val pressed = event.changes.filter { it.pressed }
                                    if (pressed.size >= 2) {
                                        val zoomChange = event.calculateZoom()
                                        val panChange = event.calculatePan()
                                        zoom = (zoom * zoomChange).coerceIn(1f, 5f)
                                        pan += panChange
                                        event.changes.forEach { it.consume() }
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        }
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawRect(Color.White)
                        val gridColor = Color(0xFFE9E2DB)
                        val step = with(density) { 24.dp.toPx() }
                        when (backgroundStyle) {
                            "GRID" -> {
                                var x = 0f
                                while (x <= size.width) {
                                    drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), 0.7.dp.toPx()); x += step
                                }
                                var y = 0f
                                while (y <= size.height) {
                                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 0.7.dp.toPx()); y += step
                                }
                            }
                            "DOTS" -> {
                                var x = step
                                while (x < size.width) {
                                    var y = step
                                    while (y < size.height) {
                                        drawCircle(gridColor, 1.1.dp.toPx(), Offset(x, y)); y += step
                                    }
                                    x += step
                                }
                            }
                        }
                        // Keep ink on its own compositing layer so erasing clears ink only,
                        // never the paper, grid, or dot background drawn above.
                        val inkLayer = drawContext.canvas.saveLayer(
                            Rect(0f, 0f, size.width, size.height), ComposePaint()
                        )
                        strokes.forEach { stroke ->
                            val erasing = stroke.color == AndroidColor.TRANSPARENT
                            val blend = if (erasing) BlendMode.Clear else BlendMode.SrcOver
                            if (stroke.points.size == 1) {
                                drawCircle(
                                    color = if (erasing) Color.Transparent else Color(stroke.color),
                                    radius = stroke.width.dp.toPx() / 2f,
                                    center = Offset(stroke.points[0].x * size.width, stroke.points[0].y * size.height),
                                    blendMode = blend
                                )
                            } else {
                                val path = Path().apply {
                                    moveTo(stroke.points.first().x * size.width, stroke.points.first().y * size.height)
                                    stroke.points.drop(1).forEach { lineTo(it.x * size.width, it.y * size.height) }
                                }
                                drawPath(
                                    path,
                                    if (erasing) Color.Transparent else Color(stroke.color),
                                    style = Stroke(stroke.width.dp.toPx(), cap = StrokeCap.Round),
                                    blendMode = blend
                                )
                            }
                        }
                        drawContext.canvas.restoreToCount(inkLayer)
                    }
                    AndroidView(
                        factory = { context -> LiveInkView(context).also { liveInkView.value = it } },
                        modifier = Modifier.fillMaxSize(),
                        update = { liveInkView.value = it }
                    )
                }
            }
            Text(
                page.title,
                Modifier.align(Alignment.TopStart).padding(12.dp),
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF51473F)
            )
            Text(
                "${(zoom * 100).toInt()}%",
                Modifier.align(Alignment.BottomEnd).padding(12.dp),
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF51473F)
            )
        }

        if (sidebarVisible) {
            Column(
                Modifier.align(Alignment.CenterEnd).width(230.dp).fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.97f))
                    .padding(12.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("TOOLS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    TextButton(onClick = { sidebarVisible = false }) { Text("Hide") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = tool == "PEN", onClick = { tool = "PEN" }, label = { Text("Pen") })
                    FilterChip(selected = tool == "ERASER", onClick = { tool = "ERASER" }, label = { Text("Erase") })
                }
                FilterChip(
                    selected = penOnly, onClick = { penOnly = !penOnly },
                    label = { Text(if (penOnly) "S Pen only" else "Pen + touch") }
                )
                Text("Ink color", style = MaterialTheme.typography.labelMedium)
                listOf("#765B49", "#222222", "#B34F46", "#557B91", "#66805A", "#D39A45").chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { hex ->
                            val color = AndroidColor.parseColor(hex)
                            Surface(
                                onClick = { brushColor = color; tool = "PEN" },
                                shape = RoundedCornerShape(50), color = Color(color), modifier = Modifier.size(30.dp),
                                border = if (brushColor == color) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                            ) {}
                        }
                    }
                }
                Text("Brush size", style = MaterialTheme.typography.labelMedium)
                Slider(value = brushSize, onValueChange = { brushSize = it }, valueRange = 1f..12f)
                HorizontalDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        undoHistory.lastOrNull()?.let {
                            redoHistory = redoHistory + listOf(strokes); strokes = it; undoHistory = undoHistory.dropLast(1)
                        }
                    }, enabled = undoHistory.isNotEmpty()) { Text("Undo") }
                    TextButton(onClick = {
                        redoHistory.lastOrNull()?.let {
                            undoHistory = undoHistory + listOf(strokes); strokes = it; redoHistory = redoHistory.dropLast(1)
                        }
                    }, enabled = redoHistory.isNotEmpty()) { Text("Redo") }
                }
                OutlinedButton(onClick = { zoom = 1f; pan = Offset.Zero }, modifier = Modifier.fillMaxWidth()) { Text("Reset view") }
                HorizontalDivider()
                Text("Paper size", style = MaterialTheme.typography.labelMedium)
                listOf("A4", "Letter", "Open").forEach { size ->
                    FilterChip(selected = paperSize == size, onClick = { paperSize = size }, label = { Text(size) })
                }
                Text("Background", style = MaterialTheme.typography.labelMedium)
                listOf("GRID", "DOTS", "BLANK").forEach { style ->
                    FilterChip(selected = backgroundStyle == style, onClick = { backgroundStyle = style },
                        label = { Text(style.lowercase().replaceFirstChar { it.uppercase() }) })
                }
            }
        } else {
            FilledTonalButton(
                onClick = { sidebarVisible = true },
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
            ) { Text("Tools  ☰") }
        }
    }
}
