package com.antony.muzei.pixiv.provider

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.antony.muzei.pixiv.AppDatabase
import com.antony.muzei.pixiv.settings.deleteArtwork.DeletedArtworkIdEntity
import com.google.android.apps.muzei.api.provider.ProviderContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DeleteArtworkReceiver : BroadcastReceiver(),
    CoroutineScope by CoroutineScope(Dispatchers.Main + SupervisorJob()) {

    override fun onReceive(context: Context, intent: Intent) {
        intent.getStringExtra("artworkId")?.let { artworkId ->
            context.contentResolver.delete(
                ProviderContract.getProviderClient(context, PixivArtProvider::class.java).contentUri,
                "${ProviderContract.Artwork.TOKEN} = ?",
                arrayOf(artworkId)
            )

            launch(Dispatchers.Main) {
                AppDatabase.getInstance(context)?.deletedArtworkIdDao()
                    ?.insertDeletedArtworkId(listOf(DeletedArtworkIdEntity(artworkId)))
            }
        }
    }
}
