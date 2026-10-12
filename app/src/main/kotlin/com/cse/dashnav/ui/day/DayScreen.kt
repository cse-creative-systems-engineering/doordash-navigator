package com.cse.dashnav.ui.day

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cse.dashnav.DashNavApp
import com.cse.dashnav.app
import com.cse.dashnav.core.Geo
import com.cse.dashnav.core.PointStatus
import com.cse.dashnav.core.TrackExport
import com.cse.dashnav.data.DashTrack
import com.cse.dashnav.data.workDay
import com.cse.dashnav.data.zone
import com.cse.dashnav.ui.Format
import com.cse.dashnav.ui.dashColor
import com.cse.dashnav.ui.map.MapBox
import com.cse.dashnav.ui.map.dotsOverlay
import com.cse.dashnav.ui.map.fitTo
import com.cse.dashnav.ui.map.rememberMapView
import com.cse.dashnav.ui.map.routeLine
import com.cse.dashnav.ui.map.toGeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class DayViewModel(private val app: DashNavApp, val date: LocalDate) : ViewModel() {
    private val repo = app.repository

    /** Null while loading. */
    val tracks = app.settings.cutoffHour
        .flatMapLatest { cutoff ->
            repo.observeDay(date, cutoff).map { dashes -> dashes.filter { it.workDay(cutoff) == date } }
        }
        .mapLatest { repo.dayTracks(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun saveOdometer(dashId: Long, start: Double?, end: Double?) {
        viewModelScope.launch { repo.setOdometer(dashId, start, end) }
    }

    suspend fun exportFile(context: Context, gpx: Boolean): File = withContext(Dispatchers.IO) {
        val dashes = repo.exportDashes(tracks.value.orEmpty().map { it.dash })
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "dashnav-$date.${if (gpx) "gpx" else "csv"}")
        file.writeText(if (gpx) TrackExport.gpx(dashes) else TrackExport.csv(dashes))
        file
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayScreen(date: LocalDate, onBack: () -> Unit, onReplay: () -> Unit) {
    val context = LocalContext.current
    val vm = viewModel(key = "day-$date") { DayViewModel(context.app, date) }
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Format.day(date)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    TextButton(onClick = { scope.launch { share(context, vm.exportFile(context, gpx = true), "application/gpx+xml") } }) {
                        Text("GPX")
                    }
                    TextButton(onClick = { scope.launch { share(context, vm.exportFile(context, gpx = false), "text/csv") } }) {
                        Text("CSV")
                    }
                },
            )
        },
    ) { padding ->
        val loaded = tracks
        if (loaded == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        DayContent(loaded, onReplay, vm::saveOdometer, Modifier.padding(padding))
    }
}

@Composable
private fun DayContent(
    tracks: List<DashTrack>,
    onReplay: () -> Unit,
    onSaveOdometer: (Long, Double?, Double?) -> Unit,
    modifier: Modifier,
) {
    var showRaw by rememberSaveable { mutableStateOf(false) }
    Column(modifier) {
        // The map stays outside the list so dragging it doesn't scroll the page.
        DayMap(tracks, showRaw, Modifier.fillMaxWidth().height(300.dp))
        LazyColumn(
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    Button(onClick = onReplay, enabled = tracks.isNotEmpty()) {
                        Icon(Icons.Filled.PlayArrow, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Replay day")
                    }
                    FilterChip(selected = showRaw, onClick = { showRaw = !showRaw }, label = { Text("Raw GPS") })
                }
            }
            item { DayTotals(tracks, Modifier.padding(horizontal = 16.dp)) }
            itemsIndexed(tracks, key = { _, t -> t.dash.id }) { i, t ->
                DashDetail(i, t, onSaveOdometer, Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@Composable
private fun DayMap(tracks: List<DashTrack>, showRaw: Boolean, modifier: Modifier) {
    val mapView = rememberMapView()
    var fitted by remember { mutableStateOf(false) }
    LaunchedEffect(tracks, showRaw) {
        mapView.overlays.clear()
        if (showRaw) {
            val all = tracks.flatMap { it.track.points }
            val kept = all.filter { it.status == PointStatus.MOVING || it.status == PointStatus.STATIONARY }
            val rejected = all.filter { it.status == PointStatus.REJECTED_ACCURACY || it.status == PointStatus.REJECTED_JUMP }
            mapView.overlays += dotsOverlay(kept.map { it.point.toGeoPoint() }, Color(0x88757575), 5f)
            mapView.overlays += dotsOverlay(rejected.map { it.point.toGeoPoint() }, Color(0xCCD32F2F), 7f)
        }
        tracks.forEachIndexed { i, t ->
            mapView.overlays += mapView.routeLine(t.track.anchors.map { it.point.toGeoPoint() }, dashColor(i))
        }
        mapView.invalidate()
        if (!fitted) {
            mapView.fitTo(tracks.flatMap { t -> t.track.anchors.map { it.point.toGeoPoint() } })
            fitted = true
        }
    }
    MapBox(mapView, modifier)
}

@Composable
private fun DayTotals(tracks: List<DashTrack>, modifier: Modifier) {
    val meters = tracks.sumOf { it.track.distanceMeters }
    val activeMs = tracks.sumOf { (it.dash.endTimeMs ?: System.currentTimeMillis()) - it.dash.startTimeMs }
    val odoMiles = tracks.mapNotNull { it.odometerMiles() }
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("${Format.miles(meters)} GPS", style = MaterialTheme.typography.headlineSmall)
            val dashes = if (tracks.size == 1) "1 dash" else "${tracks.size} dashes"
            Text("$dashes · ${Format.duration(activeMs)} active", style = MaterialTheme.typography.bodyMedium)
            if (odoMiles.size == tracks.size && tracks.isNotEmpty()) {
                val gpsMiles = Geo.metersToMiles(meters)
                Text(
                    "Odometer ${String.format(Locale.US, "%.1f", odoMiles.sum())} mi · ${diffLabel(gpsMiles, odoMiles.sum())}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun DashDetail(
    index: Int,
    t: DashTrack,
    onSaveOdometer: (Long, Double?, Double?) -> Unit,
    modifier: Modifier,
) {
    val zone = t.dash.zone()
    val end = t.dash.endTimeMs
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(dashColor(index)))
                Spacer(Modifier.width(8.dp))
                Text("Dash ${index + 1}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(Format.miles(t.track.distanceMeters), style = MaterialTheme.typography.titleMedium)
            }
            val endLabel = end?.let { Format.clock(it, zone) } ?: "now"
            val duration = Format.duration((end ?: System.currentTimeMillis()) - t.dash.startTimeMs)
            Text("${Format.clock(t.dash.startTimeMs, zone)} – $endLabel · $duration", style = MaterialTheme.typography.bodyMedium)

            val rejected = t.track.points.count { it.status == PointStatus.REJECTED_ACCURACY || it.status == PointStatus.REJECTED_JUMP }
            Text(
                "${t.track.points.size} GPS points · $rejected ignored",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            t.track.gaps.forEach { gap ->
                Text(
                    "Signal lost ${Format.clock(gap.startMs, zone)}–${Format.clock(gap.endMs, zone)}" +
                        if (gap.bridgedMeters > 0) " · ${Format.miles(gap.bridgedMeters)} estimated" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            OdometerEntry(t, onSaveOdometer)
        }
    }
}

@Composable
private fun OdometerEntry(t: DashTrack, onSave: (Long, Double?, Double?) -> Unit) {
    var start by rememberSaveable(t.dash.id) { mutableStateOf(t.dash.odometerStart?.toPlain() ?: "") }
    var end by rememberSaveable(t.dash.id) { mutableStateOf(t.dash.odometerEnd?.toPlain() ?: "") }
    val dirty = start.toDoubleOrNull() != t.dash.odometerStart || end.toDoubleOrNull() != t.dash.odometerEnd

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = start,
            onValueChange = { start = it },
            label = { Text("Odometer start") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = end,
            onValueChange = { end = it },
            label = { Text("Odometer end") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
    }
    if (dirty) {
        OutlinedButton(onClick = { onSave(t.dash.id, start.toDoubleOrNull(), end.toDoubleOrNull()) }) { Text("Save odometer") }
    }
    t.odometerMiles()?.let { odo ->
        Text(
            "Odometer ${String.format(Locale.US, "%.1f", odo)} mi · ${diffLabel(t.track.miles, odo)}",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun DashTrack.odometerMiles(): Double? {
    val s = dash.odometerStart ?: return null
    val e = dash.odometerEnd ?: return null
    return (e - s).takeIf { it > 0 }
}

private fun diffLabel(gpsMiles: Double, odoMiles: Double): String {
    val pct = (gpsMiles - odoMiles) / odoMiles * 100
    return String.format(Locale.US, "GPS %+.1f%%", pct)
}

private fun Double.toPlain(): String = if (this % 1.0 == 0.0) toLong().toString() else toString()

private fun share(context: Context, file: File, mime: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType(mime)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, "Export ${file.name}"))
}
