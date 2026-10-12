package com.cse.dashnav.ui.replay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cse.dashnav.DashNavApp
import com.cse.dashnav.app
import com.cse.dashnav.core.ReplayState
import com.cse.dashnav.core.ReplayTimeline
import com.cse.dashnav.data.DashTrack
import com.cse.dashnav.data.workDay
import com.cse.dashnav.data.zone
import com.cse.dashnav.ui.Format
import com.cse.dashnav.ui.dashColor
import com.cse.dashnav.ui.map.MapBox
import com.cse.dashnav.ui.map.fitTo
import com.cse.dashnav.ui.map.rememberMapView
import com.cse.dashnav.ui.map.routeLine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.time.LocalDate
import java.time.ZoneId

data class ReplayData(val tracks: List<DashTrack>, val timeline: ReplayTimeline, val zone: ZoneId)

class ReplayViewModel(private val app: DashNavApp, val date: LocalDate) : ViewModel() {
    private val _data = MutableStateFlow<ReplayData?>(null)
    val data = _data.asStateFlow()

    private var tracks: List<DashTrack> = emptyList()

    init {
        viewModelScope.launch {
            val cutoff = app.settings.cutoffHour.value
            val dashes = app.repository.observeDay(date, cutoff).first().filter { it.workDay(cutoff) == date }
            tracks = app.repository.dayTracks(dashes)
            build(skipIdle = true)
        }
    }

    fun build(skipIdle: Boolean) {
        val zone = tracks.firstOrNull()?.dash?.zone() ?: ZoneId.systemDefault()
        _data.value = ReplayData(tracks, ReplayTimeline(tracks.map { it.track }, skipIdle = skipIdle), zone)
    }
}

private val SPEEDS = listOf(1, 10, 30, 60, 120)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReplayScreen(date: LocalDate, onBack: () -> Unit) {
    val context = LocalContext.current
    val vm = viewModel(key = "replay-$date") { ReplayViewModel(context.app, date) }
    val data by vm.data.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Replay · ${Format.day(date)}") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        val d = data
        when {
            d == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            d.timeline.isEmpty -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No GPS data recorded for this day.")
            }
            else -> ReplayContent(d, onSkipIdleChange = vm::build, modifier = Modifier.padding(padding))
        }
    }
}

@Composable
private fun ReplayContent(data: ReplayData, onSkipIdleChange: (Boolean) -> Unit, modifier: Modifier) {
    val timeline = data.timeline
    var playMs by rememberSaveable { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var speed by rememberSaveable { mutableIntStateOf(30) }
    var skipIdle by rememberSaveable { mutableStateOf(true) }
    var follow by rememberSaveable { mutableStateOf(true) }

    // Rebuilding the timeline (skip idle toggled) changes its length; keep the position in range.
    LaunchedEffect(timeline) { playMs = playMs.coerceIn(0, timeline.durationMs) }

    LaunchedEffect(playing, speed, timeline) {
        if (!playing) return@LaunchedEffect
        var last = withFrameMillis { it }
        while (playing) {
            val now = withFrameMillis { it }
            playMs = (playMs + (now - last) * speed).coerceAtMost(timeline.durationMs)
            last = now
            if (playMs >= timeline.durationMs) playing = false
        }
    }

    val state = timeline.stateAt(playMs)

    Column(modifier.fillMaxSize()) {
        ReplayMap(data, playMs, state, follow, Modifier.fillMaxWidth().weight(1f))
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state != null) Readout(state, data)
            Slider(
                value = playMs.toFloat(),
                onValueChange = { playMs = it.toLong() },
                valueRange = 0f..timeline.durationMs.coerceAtLeast(1).toFloat(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = {
                    playMs = timeline.dashStartPlayMs.lastOrNull { it < playMs - 1_000 } ?: 0L
                }) { Icon(Icons.Filled.SkipPrevious, "Previous dash") }
                FilledIconButton(onClick = {
                    if (!playing && playMs >= timeline.durationMs) playMs = 0
                    playing = !playing
                }) {
                    if (playing) Icon(Icons.Filled.Pause, "Pause") else Icon(Icons.Filled.PlayArrow, "Play")
                }
                IconButton(onClick = {
                    timeline.dashStartPlayMs.firstOrNull { it > playMs }?.let { playMs = it }
                }) { Icon(Icons.Filled.SkipNext, "Next dash") }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SPEEDS.forEach { s ->
                        FilterChip(selected = speed == s, onClick = { speed = s }, label = { Text("${s}×") })
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = skipIdle,
                    onClick = {
                        skipIdle = !skipIdle
                        onSkipIdleChange(skipIdle)
                    },
                    label = { Text("Skip idle time") },
                )
                FilterChip(selected = follow, onClick = { follow = !follow }, label = { Text("Follow car") })
            }
        }
    }
}

@Composable
private fun Readout(state: ReplayState, data: ReplayData) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            Format.clock(state.realTimeMs, data.zone, seconds = true),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f),
        )
        Text(Format.miles(state.dayMeters), style = MaterialTheme.typography.headlineSmall)
    }
    val motion = if (state.isIdle) "Parked" else Format.mph(state.speedMps)
    Text(
        "Dash ${state.dashIndex + 1} of ${data.tracks.size} · $motion",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ReplayMap(data: ReplayData, playMs: Long, state: ReplayState?, follow: Boolean, modifier: Modifier) {
    val mapView = rememberMapView()
    val timeline = data.timeline

    // Route for each dash, split out from the timeline's frames.
    val dashRoutes = remember(timeline) {
        timeline.frames.groupBy { it.dashIndex }.toSortedMap().values.map { frames ->
            frames.map { GeoPoint(it.lat, it.lon) }
        }
    }
    val dashFrameOffsets = remember(dashRoutes) { dashRoutes.runningFold(0) { acc, r -> acc + r.size } }
    val overlays = remember(timeline) {
        val full = dashRoutes.mapIndexed { i, route -> mapView.routeLine(route, dashColor(i).copy(alpha = 0.3f)) }
        val driven = dashRoutes.indices.map { i -> mapView.routeLine(emptyList(), dashColor(i), 12f) }
        val marker = Marker(mapView).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            setInfoWindow(null)
        }
        Triple(full, driven, marker)
    }

    LaunchedEffect(overlays) {
        val (full, driven, marker) = overlays
        mapView.overlays.clear()
        mapView.overlays.addAll(full)
        mapView.overlays.addAll(driven)
        mapView.overlays.add(marker)
        mapView.fitTo(dashRoutes.flatten())
    }

    LaunchedEffect(playMs, state, overlays) {
        val (_, driven, marker) = overlays
        val s = state ?: return@LaunchedEffect
        val here = GeoPoint(s.lat, s.lon)
        val reached = timeline.framesReached(playMs)
        driven.forEachIndexed { i, line ->
            val start = dashFrameOffsets[i]
            val inDash = (reached - start).coerceIn(0, dashRoutes[i].size)
            val pts = dashRoutes[i].subList(0, inDash)
            line.setPoints(if (i == s.dashIndex && inDash > 0) pts + here else pts)
        }
        marker.position = here
        if (follow) mapView.controller.setCenter(here)
        mapView.invalidate()
    }

    MapBox(mapView, modifier)
}
