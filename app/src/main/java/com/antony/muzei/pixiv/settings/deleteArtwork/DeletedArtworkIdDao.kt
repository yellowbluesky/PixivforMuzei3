package com.antony.muzei.pixiv.settings.deleteArtwork

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DeletedArtworkIdDao {
    @Query("SELECT 1 FROM DeletedArtworkIdEntity WHERE artworkId IN (:artworkIds)")
    fun loadAllByIds(artworkIds: IntArray): List<DeletedArtworkIdEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertDeletedArtworkId(deletedArtworkIds: List<DeletedArtworkIdEntity>)
}
