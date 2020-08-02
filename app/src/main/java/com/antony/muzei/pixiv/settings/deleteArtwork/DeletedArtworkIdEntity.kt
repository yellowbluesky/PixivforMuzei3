package com.antony.muzei.pixiv.settings.deleteArtwork

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class DeletedArtworkIdEntity(
        @PrimaryKey val artworkId: String
)
