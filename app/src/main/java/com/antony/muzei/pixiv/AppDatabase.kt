package com.antony.muzei.pixiv

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.antony.muzei.pixiv.settings.deleteArtwork.DeletedArtworkIdDao
import com.antony.muzei.pixiv.settings.deleteArtwork.DeletedArtworkIdEntity

@Database(entities = arrayOf(DeletedArtworkIdEntity::class), version = 1)
abstract class AppDatabase : RoomDatabase() {
    companion object {
        const val NAME = "DeletedArtworkIdDatabase"
        var appDatabase: AppDatabase? = null

        // singleton creational design pattern
        fun getInstance(context: Context): AppDatabase? {
            if (appDatabase == null) {
                synchronized(this) {
                    appDatabase = Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME).fallbackToDestructiveMigration().build()
                }

            }
            return appDatabase
        }
    }

    abstract fun DeleteArtowkIdDao(): DeletedArtworkIdDao
}
