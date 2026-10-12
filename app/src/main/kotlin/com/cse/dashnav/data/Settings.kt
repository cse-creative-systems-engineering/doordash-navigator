package com.cse.dashnav.data

import android.content.Context
import androidx.core.content.edit
import com.cse.dashnav.core.WorkDay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _cutoffHour = MutableStateFlow(prefs.getInt(KEY_CUTOFF_HOUR, WorkDay.DEFAULT_CUTOFF_HOUR))

    /** Local hour at which one work day ends and the next begins. */
    val cutoffHour: StateFlow<Int> = _cutoffHour.asStateFlow()

    fun setCutoffHour(hour: Int) {
        val h = hour.coerceIn(0, 23)
        prefs.edit { putInt(KEY_CUTOFF_HOUR, h) }
        _cutoffHour.value = h
    }

    private companion object {
        const val KEY_CUTOFF_HOUR = "cutoff_hour"
    }
}
