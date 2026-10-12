package com.cse.dashnav.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.cse.dashnav.app
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Resumes tracking after a reboot or app update if a dash was still in progress. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (!Permissions.fineLocation(context) || !Permissions.backgroundLocation(context)) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (context.app.repository.getActiveDash() != null) TrackingService.resume(context)
            } catch (e: Exception) {
                Log.w("BootReceiver", "Could not resume tracking", e)
            } finally {
                pending.finish()
            }
        }
    }
}
