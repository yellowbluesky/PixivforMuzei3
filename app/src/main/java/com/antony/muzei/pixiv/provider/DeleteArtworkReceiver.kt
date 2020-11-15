package com.antony.muzei.pixiv.provider

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.AsyncTask
import com.antony.muzei.pixiv.AppDatabase
import com.antony.muzei.pixiv.settings.deleteArtwork.DeletedArtworkIdEntity
import com.google.android.apps.muzei.api.provider.ProviderContract

class DeleteArtworkReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val artworkId: String? = intent.getStringExtra("artworkId")

        if (artworkId != null) {
            val selectionClause = "${ProviderContract.Artwork.TOKEN} = ?"
            val selectionArgs = arrayOf(artworkId)

            val conResUri = ProviderContract.getProviderClient(context, PixivArtProvider::class.java).contentUri
            context.contentResolver.delete(
                    conResUri,
                    selectionClause,
                    selectionArgs
            )

            val appDatabase = AppDatabase.getInstance(context)
            AsyncTask.execute {
                appDatabase?.deletedArtworkIdDao()?.insertDeletedArtworkId(listOf(DeletedArtworkIdEntity(artworkId)))
            }
        }
    }
}
