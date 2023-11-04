package com.antony.muzei.pixiv.settings.blockArtist

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class BlockArtistEntity(
    @PrimaryKey val artistId: String
)
