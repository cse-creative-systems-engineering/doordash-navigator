package com.cse.dashnav

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.cse.dashnav.data.AppDatabase
import com.cse.dashnav.data.DashRepository
import com.cse.dashnav.data.Settings
import org.osmdroid.config.Configuration
import java.io.File

class DashNavApp : Application() {
    lateinit var repository: DashRepository
        private set
    lateinit var settings: Settings
        private set

    override fun onCreate() {
        super.onCreate()
        repository = DashRepository(AppDatabase.create(this).dashDao())
        settings = Settings(this)

        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = File(filesDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid-tiles")
        }

        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                TRACKING_CHANNEL_ID,
                getString(R.string.tracking_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    companion object {
        const val TRACKING_CHANNEL_ID = "tracking"
    }
}

val Context.app: DashNavApp get() = applicationContext as DashNavApp
