package com.lydiaperspective.seamworkstudio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import com.lydiaperspective.seamworkstudio.data.GarmentProject
import com.lydiaperspective.seamworkstudio.data.PatternPiece
import com.lydiaperspective.seamworkstudio.data.SeamworkDatabase
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

private data class PatternGeometry(val points: List<Offset>, val edges: Set<Pair<Int, Int>>, val angles: Map<Pair<Int, Int>, Pair<Float, Float>>)

private fun normalizedEdge(a: Int, b: Int): Pair<Int, Int> = if (a < b) a to b else b to a

private fun defaultEdges(count: Int, closed: Boolean): Set<Pair<Int, Int>> = buildSet {
    for (i in 0 until count - 1) add(i to (i + 1))
    if (closed && count > 2) add(0 to (count - 1))
}

private fun defaultAngle(a: Offset, b: Offset): Float =
    Math.toDegrees(kotlin.math.atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble())).toFloat()

private fun encodeGeometry(points: List<Offset>, edges: Set<Pair<Int, Int>>, angles: Map<Pair<Int, Int>, Pair<Float, Float>>): String {
    val p = points.joinToString(";") { "${it.x},${it.y}" }
    val e = edges.sortedWith(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second }).joinToString(";") { "${it.first},${it.second}" }
    val t = angles.entries.sortedWith(compareBy({ it.key.first }, { it.key.second })).joinToString(";") { "${it.key.first},${it.key.second},${it.value.first},${it.value.second}" }
    return "G3|$p|$e|$t"
}

private fun decodeGeometry(data: String, closed: Boolean): PatternGeometry {
    val v3 = data.startsWith("G3|")
    if (data.startsWith("G2|") || v3) {
        val sections = data.split("|", limit = if (v3) 4 else 3)
        val points = sections.getOrNull(1).orEmpty().split(";").filter { it.isNotBlank() }.mapNotNull { item ->
            val pair = item.split(","); if (pair.size != 2) null else runCatching { Offset(pair[0].toFloat(), pair[1].toFloat()) }.getOrNull()
        }
        val edges = sections.getOrNull(2).orEmpty().split(";").filter { it.isNotBlank() }.mapNotNull { item ->
            val pair = item.split(","); if (pair.size != 2) null else runCatching { normalizedEdge(pair[0].toInt(), pair[1].toInt()) }.getOrNull()
        }.filter { it.first in points.indices && it.second in points.indices && it.first != it.second }.toSet()
        val angles = if (v3) sections.getOrNull(3).orEmpty().split(";").filter { it.isNotBlank() }.mapNotNull { item ->
            val v = item.split(","); if (v.size != 4) null else runCatching { normalizedEdge(v[0].toInt(), v[1].toInt()) to (v[2].toFloat() to v[3].toFloat()) }.getOrNull()
        }.toMap() else emptyMap()
        return PatternGeometry(points, edges, angles)
    }
    val points = if (data.isBlank()) emptyList() else data.split(";").mapNotNull { item ->
        val pair = item.split(","); if (pair.size != 2) null else runCatching { Offset(pair[0].toFloat(), pair[1].toFloat()) }.getOrNull()
    }
    return PatternGeometry(points, defaultEdges(points.size, closed), emptyMap())
}
@Composable
fun PatternDesignerWorkspace(
    project: GarmentProject,
    database: SeamworkDatabase,
    back: () -> Unit
) {
    val dao = database.patternPieceDao()
    val pieces by dao.observePieces(project.id).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var selectedId by remember(project.id) { mutableStateOf<Long?>(null) }
    val selectedPiece = pieces.firstOrNull { it.id == selectedId } ?: pieces.firstOrNull()
    var draftPoints by remember(selectedPiece?.id) {
        mutableStateOf(selectedPiece?.let { decodeGeometry(it.pointsData, it.isClosed).points } ?: emptyList())
    }
    var mode by remember { mutableStateOf("ADD") }
    var selectedPoint by remember { mutableIntStateOf(-1) }
    var draftEdges by remember(selectedPiece?.id) { mutableStateOf(selectedPiece?.let { decodeGeometry(it.pointsData, it.isClosed).edges } ?: emptySet()) }
    var draftAngles by remember(selectedPiece?.id) { mutableStateOf(selectedPiece?.let { decodeGeometry(it.pointsData, it.isClosed).angles } ?: emptyMap()) }
    var angleEditorIndex by remember { mutableIntStateOf(-1) }
    var pointMenuIndex by remember { mutableIntStateOf(-1) }
    var pendingConnectionAction by remember { mutableStateOf<String?>(null) }
    var zoom by remember { mutableFloatStateOf(2.5f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var showNewPiece by remember { mutableStateOf(false) }
    var showTools by remember { mutableStateOf(false) }
    var newPieceName by remember { mutableStateOf("") }
    val currentDraftPoints by rememberUpdatedState(draftPoints)

    LaunchedEffect(selectedPiece?.id, selectedPiece?.pointsData) {
        val geometry = selectedPiece?.let { decodeGeometry(it.pointsData, it.isClosed) }
        draftPoints = geometry?.points ?: emptyList()
        draftEdges = geometry?.edges ?: emptySet()
        draftAngles = geometry?.angles ?: emptyMap()
        angleEditorIndex = -1
        selectedPoint = -1
        pointMenuIndex = -1
        pendingConnectionAction = null
    }

    fun saveGeometry(points: List<Offset>, edges: Set<Pair<Int, Int>>, angles: Map<Pair<Int, Int>, Pair<Float, Float>> = draftAngles) {
        val piece = selectedPiece ?: return
        scope.launch {
            dao.updatePiece(piece.copy(pointsData = encodeGeometry(points, edges, angles), updatedAt = System.currentTimeMillis()))
        }
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        Box(Modifier.fillMaxSize().background(Color(0xFFF1EEE9))) {
            Canvas(
                Modifier.fillMaxSize()
                    .pointerInput(mode, selectedPiece?.id, selectedPiece?.isClosed, zoom, pan) {
                        detectTapGestures(onDoubleTap = { tap ->
                            if (mode != "ADD") {
                                val nearest = currentDraftPoints.indices.minByOrNull { index ->
                                    val p = currentDraftPoints[index]
                                    hypot((size.width / 2f + pan.x + p.x * zoom) - tap.x, (size.height / 2f + pan.y + p.y * zoom) - tap.y)
                                }
                                val hit = nearest?.takeIf { index ->
                                    val p = currentDraftPoints[index]
                                    hypot((size.width / 2f + pan.x + p.x * zoom) - tap.x, (size.height / 2f + pan.y + p.y * zoom) - tap.y) < 32f
                                } ?: -1
                                if (hit >= 0) { selectedPoint = hit; pointMenuIndex = hit; pendingConnectionAction = null }
                            }
                        }, onTap = { tap ->
                            val world = Offset(
                                (tap.x - size.width / 2f - pan.x) / zoom,
                                (tap.y - size.height / 2f - pan.y) / zoom
                            )
                            if (mode == "ADD") {
                                if (selectedPiece != null && !selectedPiece.isClosed) {
                                    val next = currentDraftPoints + world
                                    val nextEdges = draftEdges + if (next.size > 1) setOf((next.size - 2) to (next.size - 1)) else emptySet()
                                    draftPoints = next
                                    draftEdges = nextEdges
                                    saveGeometry(next, nextEdges)
                                }
                            } else {
                                val nearest = currentDraftPoints.indices.minByOrNull { index ->
                                    val point = currentDraftPoints[index]
                                    hypot(
                                        (size.width / 2f + pan.x + point.x * zoom) - tap.x,
                                        (size.height / 2f + pan.y + point.y * zoom) - tap.y
                                    )
                                }
                                val target = nearest?.takeIf { index ->
                                    val point = currentDraftPoints[index]
                                    hypot(
                                        (size.width / 2f + pan.x + point.x * zoom) - tap.x,
                                        (size.height / 2f + pan.y + point.y * zoom) - tap.y
                                    ) < 28f
                                } ?: -1
                                if (pendingConnectionAction != null && target >= 0 && target != selectedPoint) {
                                    val edge = normalizedEdge(selectedPoint, target)
                                    val updatedEdges = if (pendingConnectionAction == "CONNECT") draftEdges + edge else draftEdges - edge
                                    draftEdges = updatedEdges
                                    draftAngles = draftAngles.filterKeys { it in updatedEdges }
                                    saveGeometry(currentDraftPoints, updatedEdges, draftAngles)
                                    pendingConnectionAction = null
                                    selectedPoint = target
                                } else {
                                    selectedPoint = target
                                }
                            }
                            }
                        )
                    }
                    .pointerInput(mode, selectedPiece?.id, selectedPiece?.isClosed, zoom, pan) {
                        var movingIndex = -1
                        detectDragGestures(
                            onDragStart = { start ->
                                if (mode == "EDIT") {
                                    movingIndex = currentDraftPoints.indices.minByOrNull { index ->
                                        val point = currentDraftPoints[index]
                                        hypot(
                                            (size.width / 2f + pan.x + point.x * zoom) - start.x,
                                            (size.height / 2f + pan.y + point.y * zoom) - start.y
                                        )
                                    } ?: -1
                                    if (movingIndex >= 0) {
                                        val point = currentDraftPoints[movingIndex]
                                        val distance = hypot(
                                            (size.width / 2f + pan.x + point.x * zoom) - start.x,
                                            (size.height / 2f + pan.y + point.y * zoom) - start.y
                                        )
                                        if (distance > 30f) movingIndex = -1 else selectedPoint = movingIndex
                                    }
                                }
                            },
                            onDrag = { change, _ ->
                                if (movingIndex >= 0 && mode == "EDIT") {
                                    val updated = currentDraftPoints.toMutableList()
                                    updated[movingIndex] = Offset(
                                        (change.position.x - size.width / 2f - pan.x) / zoom,
                                        (change.position.y - size.height / 2f - pan.y) / zoom
                                    )
                                    draftPoints = updated
                                    change.consume()
                                }
                            },
                            onDragEnd = {
                                if (movingIndex >= 0) saveGeometry(currentDraftPoints, draftEdges)
                                movingIndex = -1
                            },
                            onDragCancel = { movingIndex = -1 }
                        )
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            var event = awaitPointerEvent()
                            while (event.changes.count { it.pressed } < 2) {
                                if (event.changes.none { it.pressed }) return@awaitEachGesture
                                event = awaitPointerEvent()
                            }
                            do {
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                zoom = (zoom * zoomChange).coerceIn(0.25f, 20f)
                                pan += panChange
                                event.changes.forEach { it.consume() }
                                event = awaitPointerEvent()
                            } while (event.changes.any { it.pressed })
                        }
                    }
            ) {
                drawRect(Color(0xFFFDFCF9))
                val gridStep = 10f * zoom
                val originX = size.width / 2f + pan.x
                val originY = size.height / 2f + pan.y
                if (gridStep >= 3f) {
                    val firstX = ceil((-originX) / gridStep).toInt()
                    val lastX = ceil((size.width - originX) / gridStep).toInt()
                    for (i in firstX..lastX) {
                        val x = originX + i * gridStep
                        drawLine(Color(0xFFE9E3DC), Offset(x, 0f), Offset(x, size.height), 1f)
                    }
                    val firstY = ceil((-originY) / gridStep).toInt()
                    val lastY = ceil((size.height - originY) / gridStep).toInt()
                    for (i in firstY..lastY) {
                        val y = originY + i * gridStep
                        drawLine(Color(0xFFE9E3DC), Offset(0f, y), Offset(size.width, y), 1f)
                    }
                }
                drawLine(Color(0xFFB9A99B), Offset(originX, 0f), Offset(originX, size.height), 1.5f)
                drawLine(Color(0xFFB9A99B), Offset(0f, originY), Offset(size.width, originY), 1.5f)

                pieces.forEach { piece ->
                    val points = if (piece.id == selectedPiece?.id) draftPoints else decodeGeometry(piece.pointsData, piece.isClosed).points
                    if (points.isNotEmpty()) {
                        val geometry = if (piece.id == selectedPiece?.id) null else decodeGeometry(piece.pointsData, piece.isClosed)
                        val edges = if (piece.id == selectedPiece?.id) draftEdges else geometry!!.edges
                        val angles = if (piece.id == selectedPiece?.id) draftAngles else geometry!!.angles
                        val lineColor = if (piece.id == selectedPiece?.id) Color(0xFF765B49) else Color(0xFF9A9A9A)
                        edges.forEach { (a, b) ->
                            if (a in points.indices && b in points.indices) {
                                val start = Offset(originX + points[a].x * zoom, originY + points[a].y * zoom)
                                val end = Offset(originX + points[b].x * zoom, originY + points[b].y * zoom)
                                val edge = normalizedEdge(a, b)
                                val distance = hypot(end.x - start.x, end.y - start.y)
                                val stored = if (a < b) angles[edge] else angles[edge]?.let { it.second to it.first }
                                val angleA = Math.toRadians((stored?.first ?: defaultAngle(points[a], points[b])).toDouble())
                                val angleB = Math.toRadians((stored?.second ?: defaultAngle(points[b], points[a])).toDouble())
                                val handle = distance / 3f
                                val curve = Path().apply {
                                    moveTo(start.x, start.y)
                                    cubicTo(
                                        start.x + cos(angleA).toFloat() * handle,
                                        start.y + sin(angleA).toFloat() * handle,
                                        end.x + cos(angleB).toFloat() * handle,
                                        end.y + sin(angleB).toFloat() * handle,
                                        end.x, end.y
                                    )
                                }
                                drawPath(curve, lineColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.2f))
                            }
                        }
                        if (piece.id == selectedPiece?.id) {
                            points.forEachIndexed { index, point ->
                                val p = Offset(originX + point.x * zoom, originY + point.y * zoom)
                                drawCircle(
                                    if (index == selectedPoint) Color(0xFFD28B36) else Color(0xFF765B49),
                                    radius = if (index == selectedPoint) 7f else 5f,
                                    center = p
                                )
                                drawCircle(Color.White, radius = 1.8f, center = p)
                            }
                        }
                    }
                }
            }
            if (pointMenuIndex in draftPoints.indices) {
                val point = draftPoints[pointMenuIndex]
                val density = LocalDensity.current
                val menuX = (maxWidth / 2 + with(density) { (pan.x + point.x * zoom).toDp() } + 14.dp).coerceIn(8.dp, maxWidth - 176.dp)
                val menuY = (maxHeight / 2 + with(density) { (pan.y + point.y * zoom).toDp() } + 14.dp).coerceIn(72.dp, maxHeight - 220.dp)
                Surface(Modifier.offset(x = menuX, y = menuY).width(168.dp), shape = MaterialTheme.shapes.medium, tonalElevation = 6.dp, shadowElevation = 8.dp) {
                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Point ${pointMenuIndex + 1}", style = MaterialTheme.typography.labelMedium)
                        TextButton(onClick = {
                            val removed = pointMenuIndex
                            val updatedPoints = draftPoints.toMutableList().also { it.removeAt(removed) }
                            val wasLinkedBefore = removed > 0 && draftEdges.contains(normalizedEdge(removed - 1, removed))
                            val wasLinkedAfter = removed < draftPoints.lastIndex && draftEdges.contains(normalizedEdge(removed, removed + 1))
                            val updatedEdges = draftEdges.mapNotNull { (a, b) ->
                                if (a == removed || b == removed) null else normalizedEdge(if (a > removed) a - 1 else a, if (b > removed) b - 1 else b)
                            }.toMutableSet().apply {
                                if (wasLinkedBefore && wasLinkedAfter && removed > 0 && removed < draftPoints.lastIndex) {
                                    add(normalizedEdge(removed - 1, removed))
                                }
                            }
                            val updatedAngles = draftAngles.mapNotNull { (edge, value) ->
                                if (edge.first == removed || edge.second == removed) null else {
                                    val a = if (edge.first > removed) edge.first - 1 else edge.first
                                    val b = if (edge.second > removed) edge.second - 1 else edge.second
                                    normalizedEdge(a, b) to value
                                }
                            }.toMap().filterKeys { it in updatedEdges }
                            draftPoints = updatedPoints
                            draftEdges = updatedEdges
                            draftAngles = updatedAngles
                            saveGeometry(updatedPoints, updatedEdges, updatedAngles)
                            selectedPoint = -1
                            pointMenuIndex = -1
                        }, modifier = Modifier.fillMaxWidth()) { Text("Delete point") }
                        TextButton(onClick = { selectedPoint = pointMenuIndex; mode = "EDIT"; pointMenuIndex = -1 }, modifier = Modifier.fillMaxWidth()) { Text("Edit point") }
                        TextButton(onClick = { selectedPoint = pointMenuIndex; pendingConnectionAction = "DISCONNECT"; mode = "EDIT"; pointMenuIndex = -1 }, modifier = Modifier.fillMaxWidth()) { Text("Disconnect point") }
                        TextButton(onClick = { selectedPoint = pointMenuIndex; pendingConnectionAction = "CONNECT"; mode = "EDIT"; pointMenuIndex = -1 }, modifier = Modifier.fillMaxWidth()) { Text("Connect point") }
                        TextButton(onClick = { angleEditorIndex = pointMenuIndex; pointMenuIndex = -1 }, modifier = Modifier.fillMaxWidth()) { Text("Change point angle") }
                        TextButton(onClick = { pointMenuIndex = -1 }, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
                    }
                }
            }
            if (angleEditorIndex in draftPoints.indices) {
                val pointIndex = angleEditorIndex
                val connectedEdges = draftEdges.filter { pointIndex == it.first || pointIndex == it.second }
                Surface(Modifier.align(Alignment.Center).widthIn(max = 360.dp).fillMaxWidth(0.9f), shape = MaterialTheme.shapes.large, tonalElevation = 8.dp, shadowElevation = 10.dp) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Point ${pointIndex + 1} · Connection angles", style = MaterialTheme.typography.titleMedium)
                        Text("Adjust each connected segment independently to shape a smooth curve through this point.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (connectedEdges.isEmpty()) { Text("This point has no connections yet.", style = MaterialTheme.typography.bodySmall) }
                        else connectedEdges.forEach { edge ->
                            val atStart = edge.first == pointIndex
                            val other = if (atStart) edge.second else edge.first
                            val defaultStart = defaultAngle(draftPoints[edge.first], draftPoints[edge.second])
                            val defaultEnd = defaultAngle(draftPoints[edge.second], draftPoints[edge.first])
                            val pair = draftAngles[edge] ?: (defaultStart to defaultEnd)
                            val current = if (atStart) pair.first else pair.second
                            Column {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("To point ${other + 1}", style = MaterialTheme.typography.labelLarge)
                                    Text("${current.roundToInt()}°", style = MaterialTheme.typography.labelMedium)
                                }
                                Slider(value = current.coerceIn(-180f, 180f), onValueChange = { value ->
                                    val nextPair = if (atStart) value to pair.second else pair.first to value
                                    val updated = draftAngles + (edge to nextPair)
                                    draftAngles = updated
                                    saveGeometry(draftPoints, draftEdges, updated)
                                }, valueRange = -180f..180f)
                                TextButton(onClick = {
                                    val nextPair = if (atStart) defaultStart to pair.second else pair.first to defaultEnd
                                    val updated = draftAngles + (edge to nextPair)
                                    draftAngles = updated
                                    saveGeometry(draftPoints, draftEdges, updated)
                                }, modifier = Modifier.align(Alignment.End)) { Text("Reset this angle") }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = { angleEditorIndex = -1 }) { Text("Done") } }
                    }
                }
            }
            Column(Modifier.align(Alignment.TopStart).padding(12.dp)) {
                TextButton(onClick = back, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                    Text("‹  ${project.name}")
                }
                Text(selectedPiece?.name ?: "No pattern piece selected", style = MaterialTheme.typography.titleMedium)
                Text("Infinite drafting plane · coordinates in mm", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "Use two pointers to pan and pinch · 10 mm grid",
                Modifier.align(Alignment.BottomStart).padding(12.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!showTools) {
            Button(
                onClick = { showTools = true },
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
            ) { Text("Tools  ☰") }
        }
        if (showTools) Column(
            Modifier.align(Alignment.CenterEnd)
                .width(if (maxWidth < 600.dp) maxWidth * 0.82f else 260.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.97f))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("TOOLS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = { showTools = false }) { Text("Hide") }
            }
            Text("PATTERN PIECES", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Button(onClick = { newPieceName = ""; showNewPiece = true }, modifier = Modifier.fillMaxWidth()) {
                Text("+ New piece")
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                items(pieces, key = { it.id }) { piece ->
                    val active = piece.id == (selectedPiece?.id)
                    OutlinedButton(
                        onClick = { selectedId = piece.id },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(piece.name, maxLines = 1)
                            Text(
                                "${decodeGeometry(piece.pointsData, piece.isClosed).points.size} points" + if (piece.isClosed) " · Closed" else " · Open",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            HorizontalDivider()
            Text("EDITING", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = mode == "ADD", onClick = { mode = "ADD"; selectedPoint = -1 }, label = { Text("Add points") })
                FilterChip(selected = mode == "EDIT", onClick = { mode = "EDIT" }, label = { Text("Edit points") })
            }
            Text(
                when (pendingConnectionAction) {
                    "CONNECT" -> "Tap the point you want to connect to."
                    "DISCONNECT" -> "Tap the point you want to disconnect from."
                    else -> if (mode == "ADD") "Tap the drafting plane to add vertices. Double-tap a point for options."
                    else "Tap a vertex to select or drag it. Double-tap for point options."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (selectedPiece != null) {
                OutlinedButton(
                    onClick = {
                        val closing = !selectedPiece.isClosed
                        val updatedEdges = if (closing && draftPoints.size >= 3) {
                            draftEdges + normalizedEdge(0, draftPoints.lastIndex)
                        } else if (!closing && draftPoints.size >= 3) {
                            draftEdges - normalizedEdge(0, draftPoints.lastIndex)
                        } else draftEdges
                        draftEdges = updatedEdges
                        saveGeometry(draftPoints, updatedEdges)
                        val updated = selectedPiece.copy(isClosed = closing, updatedAt = System.currentTimeMillis())
                        scope.launch { dao.updatePiece(updated) }
                    },
                    enabled = draftPoints.size >= 3 || selectedPiece.isClosed,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (selectedPiece.isClosed) "Reopen path" else "Close shape") }
                OutlinedButton(
                    onClick = {
                        val removed = selectedPoint
                        val updated = draftPoints.toMutableList().also { it.removeAt(removed) }
                        val wasLinkedBefore = removed > 0 && draftEdges.contains(normalizedEdge(removed - 1, removed))
                        val wasLinkedAfter = removed < draftPoints.lastIndex && draftEdges.contains(normalizedEdge(removed, removed + 1))
                        val updatedEdges = draftEdges.mapNotNull { (a, b) ->
                            if (a == removed || b == removed) null else normalizedEdge(if (a > removed) a - 1 else a, if (b > removed) b - 1 else b)
                        }.toMutableSet().apply {
                            if (wasLinkedBefore && wasLinkedAfter && removed > 0 && removed < draftPoints.lastIndex) {
                                add(normalizedEdge(removed - 1, removed))
                            }
                        }
                        val updatedAngles = draftAngles.mapNotNull { (edge, value) ->
                            if (edge.first == removed || edge.second == removed) null else {
                                val a = if (edge.first > removed) edge.first - 1 else edge.first
                                val b = if (edge.second > removed) edge.second - 1 else edge.second
                                normalizedEdge(a, b) to value
                            }
                        }.toMap().filterKeys { it in updatedEdges }
                        draftPoints = updated
                        draftEdges = updatedEdges
                        draftAngles = updatedAngles
                        selectedPoint = -1
                        pointMenuIndex = -1
                        saveGeometry(updated, updatedEdges, updatedAngles)
                    },
                    enabled = selectedPoint in draftPoints.indices,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Remove selected point") }
                OutlinedButton(
                    onClick = {
                        scope.launch { dao.deletePiece(selectedPiece.id) }
                        selectedId = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Delete piece") }
            }
            HorizontalDivider()
            Text("VIEW", style = MaterialTheme.typography.labelLarge)
            Text("${(zoom / 2.5f * 100).roundToInt()}% zoom", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { zoom = (zoom / 1.25f).coerceAtLeast(0.25f) }) { Text("−") }
                OutlinedButton(onClick = { zoom = (zoom * 1.25f).coerceAtMost(20f) }) { Text("+") }
                OutlinedButton(onClick = { zoom = 2.5f; pan = Offset.Zero }) { Text("Reset") }
            }
            Text("Grid: 10 mm", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

    }

    if (showNewPiece) {
        AlertDialog(
            onDismissRequest = { showNewPiece = false },
            title = { Text("New pattern piece") },
            text = {
                OutlinedTextField(
                    value = newPieceName,
                    onValueChange = { newPieceName = it },
                    label = { Text("Piece name") },
                    placeholder = { Text("e.g. Front bodice") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = newPieceName.trim().ifBlank { "Pattern piece ${pieces.size + 1}" }
                    scope.launch {
                        val id = dao.insertPiece(PatternPiece(projectId = project.id, name = name))
                        selectedId = id
                    }
                    showNewPiece = false
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showNewPiece = false }) { Text("Cancel") } }
        )
    }
}
