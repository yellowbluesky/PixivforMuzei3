package com.antony.muzei.pixiv.settings.blockArtist

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BlockedArtistDao {
    @Query("SELECT EXISTS(SELECT * FROM BlockArtistEntity WHERE artistId = (:artistId))")
    fun isRowIsExist(artistId: Int): Boolean

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertBlockedArtistId(blockedArtistIds: List<BlockArtistEntity>)
}
