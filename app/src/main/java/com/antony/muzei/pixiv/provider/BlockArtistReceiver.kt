package com.antony.muzei.pixiv.provider

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.antony.muzei.pixiv.AppDatabase
import com.antony.muzei.pixiv.settings.blockArtist.BlockArtistEntity
import com.google.android.apps.muzei.api.provider.ProviderContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BlockArtistReceiver : BroadcastReceiver(), CoroutineScope by CoroutineScope(Dispatchers.Main + SupervisorJob()) {
    override fun onReceive(context: Context, intent: Intent) {
        intent.getStringExtra("artistId")?.let { artistId ->
            context.contentResolver.delete(
                ProviderContract.getProviderClient(context, PixivArtProvider::class.java).contentUri,
                "${ProviderContract.Artwork.TOKEN} = ?",
                arrayOf(artistId)
            )

            launch(Dispatchers.IO) {
                AppDatabase.getInstance(context).blockedArtistDao()
                    .insertBlockedArtistId(listOf(BlockArtistEntity(artistId)))
            }
        }
    }
}
