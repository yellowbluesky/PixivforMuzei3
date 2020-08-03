package com.antony.muzei.pixiv.settings.deleteArtwork

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DeletedArtworkIdDao {
    // For debug purposes only
    @Query("SELECT * FROM DeletedArtworkIdEntity")
    fun getAll(): List<DeletedArtworkIdEntity>

    // Returns true if the passed artworkId is present in the table
    @Query("SELECT EXISTS(SELECT * FROM DeletedArtworkIdEntity WHERE artworkId = (:artworkId))")
    fun isRowIsExist(artworkId : Int) : Boolean

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertDeletedArtworkId(deletedArtworkIds: List<DeletedArtworkIdEntity>)
}
