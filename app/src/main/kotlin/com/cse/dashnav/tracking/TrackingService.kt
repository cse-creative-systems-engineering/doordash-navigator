package com.cse.dashnav.tracking

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.cse.dashnav.DashNavApp
import com.cse.dashnav.MainActivity
import com.cse.dashnav.R
import com.cse.dashnav.app
import com.cse.dashnav.core.Geo
import com.cse.dashnav.core.PointStatus
import com.cse.dashnav.core.TrackFilter
import com.cse.dashnav.core.TrackPoint
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale

data class LiveStats(
    val dashId: Long,
    val startTimeMs: Long,
    val distanceMeters: Double,
    val pointCount: Int,
    val lastAccuracyM: Float?,
    val parked: Boolean,
    val fastSampling: Boolean,
)

/**
 * Records GPS for the active dash. Every fix is written to the database as it arrives, so a crash
 * or a kill by the OS loses nothing; on restart the service picks the active dash back up.
 */
class TrackingService : LifecycleService() {

    private lateinit var fused: FusedLocationProviderClient
    private val writeLock = Mutex()

    /** Serializes starting and stopping so a quick start/stop can't leave a dash open. */
    private val lifecycleLock = Mutex()
    private var stopRequested = false
    private var dashId: Long? = null
    private var dashStartMs = 0L
    private var filter = TrackFilter()
    private var pointCount = 0
    private var parkedSinceMs: Long? = null
    private var fastSampling = true
    private var lastNotificationMs = 0L

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach(::onLocation)
        }
    }

    override fun onCreate() {
        super.onCreate()
        fused = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopTracking()
            return START_NOT_STICKY
        }
        if (!Permissions.fineLocation(this)) {
            Log.w(TAG, "No location permission; not tracking")
            stopSelf()
            return START_NOT_STICKY
        }
        // Must go foreground right away, before any async work.
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification("Starting…"),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
        )
        if (dashId == null || stopRequested) {
            stopRequested = false
            // ACTION_START begins a dash; anything else (sticky restart, boot) only resumes one.
            val startNew = intent?.action == ACTION_START
            lifecycleScope.launch { begin(startNew) }
        }
        return START_STICKY
    }

    private suspend fun begin(startNew: Boolean) = lifecycleLock.withLock {
        if (stopRequested || dashId != null) return@withLock
        val repo = app.repository
        val dash = if (startNew) repo.startDash() else repo.getActiveDash()
        if (dash == null) {
            stopForegroundAndSelf()
            return@withLock
        }
        dashId = dash.id
        dashStartMs = dash.startTimeMs
        // Rebuild the live filter from what was already recorded, in case we're resuming after a kill.
        filter = TrackFilter()
        parkedSinceMs = null
        val existing = repo.rawPoints(dash.id)
        existing.forEach(filter::add)
        pointCount = existing.size
        publish(lastAccuracy = null)
        requestUpdates(fast = true)
    }

    private fun requestUpdates(fast: Boolean) {
        fastSampling = fast
        val interval = if (fast) FAST_INTERVAL_MS else SLOW_INTERVAL_MS
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, interval)
            .setMinUpdateIntervalMillis(if (fast) 1_000 else interval / 2)
            .setWaitForAccurateLocation(false)
            .build()
        try {
            fused.removeLocationUpdates(callback)
            fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission revoked", e)
            stopTracking()
        }
    }

    private fun onLocation(location: Location) {
        if (stopRequested) return
        val id = dashId ?: return
        val point = location.toTrackPoint()
        @Suppress("DEPRECATION")
        val isMock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) location.isMock else location.isFromMockProvider
        lifecycleScope.launch { writeLock.withLock { app.repository.recordPoint(id, point, isMock) } }

        val classified = filter.add(point)
        pointCount++
        adaptSampling(classified.status, point.timeMs)
        publish(lastAccuracy = point.accuracyM)
    }

    /** Sample less often after a minute parked; go back to fast sampling as soon as we move. */
    private fun adaptSampling(status: PointStatus, timeMs: Long) {
        when (status) {
            PointStatus.MOVING -> {
                parkedSinceMs = null
                if (!fastSampling) requestUpdates(fast = true)
            }
            PointStatus.STATIONARY -> {
                val since = parkedSinceMs ?: timeMs.also { parkedSinceMs = it }
                if (fastSampling && timeMs - since > PARKED_SLOWDOWN_MS) requestUpdates(fast = false)
            }
            else -> Unit
        }
    }

    private fun publish(lastAccuracy: Float?) {
        val id = dashId ?: return
        _liveStats.value = LiveStats(
            dashId = id,
            startTimeMs = dashStartMs,
            distanceMeters = filter.distanceMeters,
            pointCount = pointCount,
            lastAccuracyM = lastAccuracy,
            parked = parkedSinceMs != null,
            fastSampling = fastSampling,
        )
        val now = System.currentTimeMillis()
        if (now - lastNotificationMs > NOTIFICATION_THROTTLE_MS) {
            lastNotificationMs = now
            val miles = String.format(Locale.US, "%.1f mi", Geo.metersToMiles(filter.distanceMeters))
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(miles))
        }
    }

    private fun stopTracking() {
        stopRequested = true
        fused.removeLocationUpdates(callback)
        _liveStats.value = null
        lifecycleScope.launch {
            lifecycleLock.withLock {
                val repo = app.repository
                val id = dashId ?: repo.getActiveDash()?.id
                dashId = null
                // Let queued point writes land before the dash is closed and its distance cached.
                writeLock.withLock { }
                if (id != null) repo.endDash(id)
                // A new start may have arrived while we were closing the dash; leave that one running.
                if (stopRequested) stopForegroundAndSelf()
            }
        }
    }

    private fun stopForegroundAndSelf() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        fused.removeLocationUpdates(callback)
        super.onDestroy()
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, TrackingService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, DashNavApp.TRACKING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Dash in progress")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, "End dash", stop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val TAG = "TrackingService"
        private const val ACTION_START = "com.cse.dashnav.START"
        private const val ACTION_RESUME = "com.cse.dashnav.RESUME"
        private const val ACTION_STOP = "com.cse.dashnav.STOP"
        private const val NOTIFICATION_ID = 1
        private const val FAST_INTERVAL_MS = 3_000L
        private const val SLOW_INTERVAL_MS = 15_000L
        private const val PARKED_SLOWDOWN_MS = 60_000L
        private const val NOTIFICATION_THROTTLE_MS = 5_000L

        private val _liveStats = MutableStateFlow<LiveStats?>(null)

        /** Stats for the dash being recorded right now, or null when not tracking. */
        val liveStats: StateFlow<LiveStats?> = _liveStats.asStateFlow()

        fun start(context: Context) = send(context, ACTION_START)

        fun resume(context: Context) = send(context, ACTION_RESUME)

        fun stop(context: Context) {
            context.startService(Intent(context, TrackingService::class.java).setAction(ACTION_STOP))
        }

        private fun send(context: Context, action: String) {
            ContextCompat.startForegroundService(context, Intent(context, TrackingService::class.java).setAction(action))
        }
    }
}

private fun Location.toTrackPoint() = TrackPoint(
    timeMs = time,
    lat = latitude,
    lon = longitude,
    accuracyM = if (hasAccuracy()) accuracy else Float.MAX_VALUE,
    speedMps = if (hasSpeed()) speed else null,
    bearingDeg = if (hasBearing()) bearing else null,
    altitudeM = if (hasAltitude()) altitude else null,
)
