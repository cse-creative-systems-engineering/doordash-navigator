package com.cse.dashnav.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [DashEntity::class, TrackPointEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dashDao(): DashDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "dashnav.db").build()
    }
}
