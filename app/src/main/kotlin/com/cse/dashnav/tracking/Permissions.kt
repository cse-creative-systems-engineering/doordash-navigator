package com.cse.dashnav.tracking

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat

object Permissions {
    fun has(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun fineLocation(context: Context) = has(context, Manifest.permission.ACCESS_FINE_LOCATION)

    fun backgroundLocation(context: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || has(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    fun notifications(context: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || has(context, Manifest.permission.POST_NOTIFICATIONS)

    fun ignoringBatteryOptimizations(context: Context) =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
}
