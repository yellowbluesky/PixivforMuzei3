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
        MuzeiContract.Artwork.getCurrentArtwork(applicationContext)?.title?.let {
            applicationContext.contentResolver.delete(
                ProviderContract.getProviderClient(applicationContext, PixivArtProvider::class.java).contentUri,
                "${ProviderContract.Artwork.TITLE} = ?",
                arrayOf(it)
            )
        }
    }
}

