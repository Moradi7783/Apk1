package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [IpEntity::class], version = 1, exportSchema = false)
abstract class LakDatabase : RoomDatabase() {
    abstract fun ipDao(): IpDao

    companion object {
        @Volatile
        private var INSTANCE: LakDatabase? = null

        fun getInstance(context: Context): LakDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LakDatabase::class.java,
                    "lakdns_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
