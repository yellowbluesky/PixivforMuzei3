package com.antony.muzei.pixiv

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.antony.muzei.pixiv.settings.blockArtist.BlockedArtistDao
import com.antony.muzei.pixiv.settings.blockArtist.BlockArtistEntity
import com.antony.muzei.pixiv.settings.deleteArtwork.DeletedArtworkIdDao
import com.antony.muzei.pixiv.settings.deleteArtwork.DeletedArtworkIdEntity

@Database(
    entities = [DeletedArtworkIdEntity::class, BlockArtistEntity::class], version = 1, exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    companion object {
        // Singleton prevents multiple instances of database opening at the
        // same time.
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            // if the INSTANCE is not null, then return it,
            // if it is, then create the database
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "word_database"
                    ).build()
                INSTANCE = instance
                // return instance
                instance
            }
        }
    }

    abstract fun deletedArtworkIdDao(): DeletedArtworkIdDao
    abstract fun blockedArtistDao(): BlockedArtistDao
}
