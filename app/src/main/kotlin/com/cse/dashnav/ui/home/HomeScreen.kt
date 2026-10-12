package com.cse.dashnav.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cse.dashnav.DashNavApp
import com.cse.dashnav.app
import com.cse.dashnav.data.DaySummary
import com.cse.dashnav.tracking.LiveStats
import com.cse.dashnav.tracking.Permissions
import com.cse.dashnav.tracking.TrackingService
import com.cse.dashnav.ui.Format
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(app: DashNavApp) : ViewModel() {
    val days = combine(app.repository.observeDashes(), app.settings.cutoffHour, ::Pair)
        .mapLatest { (dashes, cutoff) -> app.repository.summarize(dashes, cutoff) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val live = TrackingService.liveStats
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenDay: (LocalDate) -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val vm = viewModel { HomeViewModel(context.app) }
    val days by vm.days.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DashNav") },
                actions = {
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "Settings") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(padding),
        ) {
            item { PermissionsCard() }
            item { DashCard(live) }
            item {
                Text("Work days", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            }
            if (days.isEmpty()) {
                item { Text("No dashes yet. Start one above.", style = MaterialTheme.typography.bodyMedium) }
            }
            items(days, key = { it.date.toString() }) { day -> DayRow(day, onClick = { onOpenDay(day.date) }) }
        }
    }
}

@Composable
private fun DashCard(live: LiveStats?) {
    val context = LocalContext.current
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted[Manifest.permission.ACCESS_FINE_LOCATION] == true) TrackingService.start(context)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            if (live == null) {
                Text("Not dashing", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        if (Permissions.fineLocation(context)) {
                            TrackingService.start(context)
                        } else {
                            locationLauncher.launch(
                                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text("Start dash", style = MaterialTheme.typography.titleMedium) }
            } else {
                val now by produceState(System.currentTimeMillis()) {
                    while (true) {
                        value = System.currentTimeMillis()
                        delay(1_000)
                    }
                }
                Text("Dashing · ${Format.duration(now - live.startTimeMs)}", style = MaterialTheme.typography.titleLarge)
                Text(Format.miles(live.distanceMeters), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                val accuracy = live.lastAccuracyM?.let { if (it > 1_000f) "no fix" else "±${it.toInt()} m" } ?: "waiting for GPS"
                val state = if (live.parked) "parked" else "moving"
                Text(
                    "GPS $accuracy · $state · ${live.pointCount} points",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { TrackingService.stop(context) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text("End dash", style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}

/** Lists what tracking needs that isn't granted yet. Hidden once everything is in place. */
@SuppressLint("BatteryLife")
@Composable
private fun PermissionsCard() {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }

    val onResult: (Any) -> Unit = { refresh++ }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions(), onResult)
    val singleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), onResult)

    val fine = remember(refresh) { Permissions.fineLocation(context) }
    val background = remember(refresh) { Permissions.backgroundLocation(context) }
    val notifications = remember(refresh) { Permissions.notifications(context) }
    val battery = remember(refresh) { Permissions.ignoringBatteryOptimizations(context) }
    if (fine && background && notifications && battery) return

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Setup for reliable tracking", style = MaterialTheme.typography.titleMedium)
            if (!fine) {
                SetupRow("Location access", "Needed to record your route.") {
                    locationLauncher.launch(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                    )
                }
            }
            if (fine && !background && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                SetupRow("Allow location all the time", "Keeps tracking if the app restarts mid-dash. Pick \"Allow all the time\".") {
                    singleLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            }
            if (!notifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                SetupRow("Notifications", "Shows the tracking notification with live miles.") {
                    singleLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            if (!battery) {
                SetupRow("Unrestricted battery", "Stops the phone from killing tracking to save power.") {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri()),
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupRow(title: String, detail: String, onFix: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick = onFix) { Text("Allow") }
    }
}

@Composable
private fun DayRow(day: DaySummary, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Format.day(day.date), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(Format.miles(day.distanceMeters), style = MaterialTheme.typography.titleMedium)
        }
        val dashes = if (day.dashCount == 1) "1 dash" else "${day.dashCount} dashes"
        val status = if (day.inProgress) " · in progress" else ""
        Text(
            "$dashes · ${Format.duration(day.activeMs)}$status",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider()
}
