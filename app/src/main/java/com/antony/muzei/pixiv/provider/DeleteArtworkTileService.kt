package com.antony.muzei.pixiv.provider

import android.os.Build
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.google.android.apps.muzei.api.MuzeiContract
import com.google.android.apps.muzei.api.provider.ProviderContract

@RequiresApi(Build.VERSION_CODES.N)
class DeleteArtworkTileService : TileService() {
    override fun onClick() {
        super.onClick()
        val title = MuzeiContract.Artwork.getCurrentArtwork(applicationContext)?.title

        if (title != null) {
            val selectionClause = "${ProviderContract.Artwork.TITLE} = ?"
            val selectionArgs = arrayOf(title)

            val conResUri = ProviderContract.getProviderClient(applicationContext, PixivArtProvider::class.java).contentUri
            applicationContext.contentResolver.delete(
                    conResUri,
                    selectionClause,
                    selectionArgs
            )
        }
    }
}

