package com.antony.muzei.pixiv.provider

import android.content.*
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.preference.PreferenceManager
import androidx.work.*
import com.antony.muzei.pixiv.AppDatabase
import com.antony.muzei.pixiv.PixivMuzeiSupervisor
import com.antony.muzei.pixiv.PixivMuzeiSupervisor.getAccessToken
import com.antony.muzei.pixiv.PixivProviderConst
import com.antony.muzei.pixiv.PixivProviderConst.AUTH_MODES
import com.antony.muzei.pixiv.R
import com.antony.muzei.pixiv.provider.exceptions.AccessTokenAcquisitionException
import com.antony.muzei.pixiv.provider.exceptions.FilterMatchNotFoundException
import com.antony.muzei.pixiv.provider.network.PixivAuthFeedJsonService
import com.antony.muzei.pixiv.provider.network.PixivImageDownloadService
import com.antony.muzei.pixiv.provider.network.RestClient
import com.antony.muzei.pixiv.provider.network.moshi.AuthArtwork
import com.antony.muzei.pixiv.provider.network.moshi.Contents
import com.antony.muzei.pixiv.provider.network.moshi.Illusts
import com.antony.muzei.pixiv.provider.network.moshi.RankingArtwork
import com.antony.muzei.pixiv.util.HostManager
import com.google.android.apps.muzei.api.provider.Artwork
import com.google.android.apps.muzei.api.provider.ProviderContract
import com.google.android.apps.muzei.api.provider.ProviderContract.getProviderClient
import okhttp3.*
import okio.BufferedSink
import okio.buffer
import okio.sink
import retrofit2.Call
import java.io.File
import java.io.OutputStream
import java.util.concurrent.TimeUnit

class PixivArtWorker(context: Context, workerParams: WorkerParameters) :
    Worker(context, workerParams) {
    companion object {
        const val LOG_TAG = "ANTONY_WORKER"
        private const val WORKER_TAG = "ANTONY"
        private val IMAGE_EXTENSIONS = arrayOf(".jpg", ".png")

        // Variable that tracks if the artwork cache needs to be cleared
        private var clearArtwork = false

        internal fun enqueueLoad(clearArtworkRequested: Boolean, context: Context?) {
            if (clearArtworkRequested) {
                clearArtwork = true
            }

            context?.also {
                Constraints.Builder().apply {
                    setRequiredNetworkType(NetworkType.CONNECTED)
                }.let { builder ->
                    OneTimeWorkRequest.Builder(PixivArtWorker::class.java)
                        .setConstraints(builder.build())
                        .addTag(WORKER_TAG)
                        .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
                        .build()
                }.let { request ->
                    WorkManager.getInstance(it)
                        .enqueueUniqueWork(WORKER_TAG, ExistingWorkPolicy.KEEP, request)
                }
            }

            // The Work must be a UniqueWork
            // If not unique work, multiple works can be submitted at the processed at the same time
            // This can lead to race conditions if a new access token is needed
            // Additionally, we definitely do not want to spam the API
        }
    }

    /*
    * Performs binary search to find when the oldest bookmark artwork was made.
    * Buckle up for a fairly convoluted explanation
    * Context: Imagine a user account with 1000 bookmarked artworks made over the past one year
    * The Pixiv API has no way to arbitrarily navigate to any single bookmarked artwork.
    * Instead, when making an API call we may specify a "max_bookmark_id" parameter
    * This parameter is a timestamp of some sort. From my investigations, it appears to be a counting
    * in centiseconds from an epoch sometime in early 2016. This doesn't seem entirely correct, but exact understanding
    * is not strictly required
    * The parameter is a "sliding window", and varying this parameter varies what artworks are returned in the API response
    * As the parameter is a timestamp, adjusting it can have unpredictable results:
    *   If the user has made many bookmarks in a short period of time, then a slight adjustment will present completely new artworks
    *   If the user has slowly added bookmarks, then even a large adjustment will not result in a dramatically different API response
    *
    * Binary search parameters
    *   Stop criteria: API response with non-empty artwork list, but also a null next_url value
    *   Heuristics:
    *       We are too high if the API response contains a next_url value
    *       We are low if the API response contains no artworks.
    *   Search space: Between maximal value and 0
    *   Initial value: maximal value
    *   Initial step: to halfway within the search space
    *
    * Binary Search Algorithm
    *   Every step, if the stop criteria is not met, the search will move up or down by half the last step
    *
    *
    * Example:
    *   Say that the initial value is 100, and the target value is 67.5
    *       The inital search at 100 is too high, so we go down by half the search space to 50
    *       50 is too low, go up by half the previous step (25 = 50 / 2) to 75
    *       75 is too high, so we go down by half the previous step (12.5 = 25 / 2) to 67.5
    *       We have a match
    */
    private fun findBookmarkStartTime(userId: String) {
        Log.d(LOG_TAG, "Looking for oldest bookmark id")
        val service: PixivAuthFeedJsonService = RestClient.getRetrofitAuthInstance()
            .create(PixivAuthFeedJsonService::class.java)
        var call: Call<Illusts?> = service.getBookmarkJson(userId)

        var counter = 0
        var callingId = 0L
        // setting initial value
        var illusts = call.execute().body()!!
        var step = illusts.next_url!!.substringAfter("max_bookmark_id=").toLong()
        while (true) {
            counter++
            Log.d(LOG_TAG, "Iteration $counter")
            if (illusts.next_url != null) {
                // we are too high
                val currentId = illusts.next_url!!.substringAfter("max_bookmark_id=").toLong()
                step /= 2
                callingId = currentId - step
                call = service.getBookmarkOffsetJson(userId, callingId.toString())
            } else if (illusts.artworks.isEmpty()) {
                step /= 2
                callingId += step
                call = service.getBookmarkOffsetJson(userId, callingId.toString())
            } else {
                Log.d(LOG_TAG, "Found at $callingId")
                break
            }
            illusts = call.execute().body()!!
        }
        with(PreferenceManager.getDefaultSharedPreferences(applicationContext).edit()) {
            putLong("oldestMaxBookmarkId", callingId)
            apply()
        }
    }


    private fun downloadImage(
        responseBody: ResponseBody?,
        filename: String,
        storeInExtStorage: Boolean
    ): Uri {
        val fileType = responseBody?.contentType()

        return if (!storeInExtStorage) {
            downloadImageInternal(responseBody, filename, fileType)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            downloadImageExternalApi29(responseBody, filename, fileType)
        } else {
            downloadImageExternalApi28(responseBody, filename, fileType)
        }
    }

    // Function to download images to external storage
    // External storage in this case refers to /storage/emulated/0/Pictures/PixivForMuzei3
    // Option is also there to store onto an SD card if present
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun downloadImageExternalApi29(
        responseBody: ResponseBody?,
        filename: String,
        fileType: MediaType?,
    ): Uri {
        Log.i(LOG_TAG, "Downloading artwork, external API >29")
        val contentResolver = applicationContext.contentResolver

        // If image already exists on the filesystem, then we can skip downloading it
        isImageAlreadyDownloadedApi29(contentResolver, filename)?.let {
            Log.i(LOG_TAG, "Image already exists, early exiting")
            return it
        }

        // Inserting the filename, relative path within the /Pictures folder, and MIME type into the content provider
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PixivForMuzei3")
            put(MediaStore.MediaColumns.MIME_TYPE, fileType.toString())
        }

        // Default option is VOLUME_EXTERNAL_PRIMARY
        // If user has selected the option to store onto SD card, first we check if there is more than one storage mounted
        // Handles the case when SD card is selected but is then unmounted
        // Then, iterate through the array of mounted storages until we find one that is not VOLUME_EXTERNAL_PRIMARY
        // The manual iterating is required as I received a user report where VOLUME_EXTERNAL_PRIMARY was not the first entry
        var volumeName = MediaStore.VOLUME_EXTERNAL_PRIMARY
        if (!PreferenceManager.getDefaultSharedPreferences(applicationContext)
                .getString("pref_selectWhichExtStorage", "phone").equals("phone")
        ) {
            MediaStore.getExternalVolumeNames(applicationContext).takeIf { it.size > 1 }
                ?.let { volumeNames ->
                    for (volume in volumeNames) {
                        if (volume != MediaStore.VOLUME_EXTERNAL_PRIMARY) {
                            volumeName = volume
                        }
                    }
                }
        }

        //Gives us a URI to save the image to
        val imageUri = contentResolver.insert(MediaStore.Images.Media.getContentUri(volumeName), contentValues)!!
        // Null asserted here because if contentResolver.insert() returns a null for whatever reason, we really cannot proceed

        // The other method using BufferedSink doesn't work all we have is a URI to sink into
        val fis = responseBody!!.byteStream()
        val fosExternal: OutputStream? = contentResolver.openOutputStream(imageUri)
        val buffer = ByteArray(1024 * 1024 * 10)
        var lengthInternal: Int
        while (fis.read(buffer).also { lengthInternal = it } > 0) {
            fosExternal!!.write(buffer, 0, lengthInternal)
        }
        fosExternal!!.close()
        fis.close()

        Log.i(LOG_TAG, "Downloaded")
        return imageUri
    }

    /* Checking if existing copy of images exists*/
    // Returns the Uri of an image with matching filename
    // otherwise returns null
    private fun isImageAlreadyDownloadedApi29(contentResolver: ContentResolver, filename: String): Uri? {
        // Specifying that I want only the _ID column returned
        // Specifying that I only want rows that have a DISPLAY_NAME matching the filename passed
        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.DISPLAY_NAME} = ?",
            arrayOf(filename),
            null
        )?.let {
            if (it.count != 0) {
                Log.v(LOG_TAG, "downloadImageAPI10: Duplicate found")
                val imageUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    it.getInt(it.getColumnIndexOrThrow(MediaStore.Images.ImageColumns._ID)).toLong()
                )
                it.close()
                return imageUri
            }
        }
        return null
    }

    // Function to download images to "external storage"
    // External storage is described at the path below
    // This function is used when downloading on external storage on Api 28 or lower
    private fun downloadImageExternalApi28(
        responseBody: ResponseBody?,
        filename: String,
        fileType: MediaType?,
    ): Uri {
        Log.i(LOG_TAG, "Downloading artwork, external API < 28")
        // Checks if directory exists. If nonexistent, then create it
        val directory = File("/storage/emulated/0/Pictures/PixivForMuzei3/")
        if (!directory.exists()) {
            directory.mkdirs()
        }

        val image = File(directory, "$filename.${fileType!!.subtype}").also { image ->
            if (!image.exists()) {
                // Broadcast the addition of a new media file
                // Solves problem where the images were not showing up in their gallery up until a scan was triggered
                MediaScannerConnection.scanFile(applicationContext, arrayOf(image.toString()), null, null)
            } else {
                // If the image has already been downloaded, do not redownload
                Log.i(LOG_TAG, "Artwork exists, early exit")
                return Uri.fromFile(image)
            }
        }
        val sink: BufferedSink = image.sink().buffer()
        sink.writeAll(responseBody!!.source())
        responseBody.close()
        sink.close()
        Log.i(LOG_TAG, "Downloaded")
        return Uri.fromFile(image)
    }


    // Function used to download images to internal storage
    // Internal storage in this case is /storage/emulated/0/Android/data/com.antony.muzei.pixiv/files
    private fun downloadImageInternal(
        responseBody: ResponseBody?,
        filename: String,
        fileType: MediaType?,
    ): Uri {
        Log.i(LOG_TAG, "Downloading artwork, internal")
        File(
            applicationContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "$filename.${fileType!!.subtype}"
            // TODO handle this null asserted
        ).also {
            if (it.exists()) {
                Log.i(LOG_TAG, "Artwork exists, early exit")
                return Uri.fromFile(it)
            }
        }.let {
            with(it.sink().buffer()) {
                writeAll(responseBody!!.source())
                responseBody.close()
                close()
            }
            Log.i(LOG_TAG, "Downloaded")
            return Uri.fromFile(it)
        }
    }

    private fun isDesiredPixelSize(
        width: Int,
        height: Int,
        settingMinimumHeight: Int,
        settingMinimumWidth: Int,
        settingAspectRatio: Int
    ): Boolean {
        return when (settingAspectRatio) {
            0 -> height >= (settingMinimumHeight * 10) && width >= (settingMinimumWidth * 10)
            1 -> height >= (settingMinimumHeight * 10)
            2 -> width >= (settingMinimumWidth * 10)
            else -> true
        }
    }

    // 0: Anything goes
    // 1: Portrait
    // 2: Landscape
    private fun isDesiredAspectRatio(width: Int, height: Int, settingAspectRatio: Int): Boolean {
        return when (settingAspectRatio) {
            0 -> true
            1 -> height >= width
            2 -> height <= width
            else -> true
        }
    }

    // Scalar must match with scalar in SettingsActivity
    private fun isEnoughViews(viewCount: Int, settingMinimumViewCount: Int): Boolean {
        return viewCount >= settingMinimumViewCount * 500
    }

    // Returns true if the artwork's ID exists int the DeletedArtwork database
    // If the database in inaccessible for whatever reason, false is returned
    private fun isBeenDeleted(illustId: Int): Boolean {
        return AppDatabase.getInstance(applicationContext)?.deletedArtworkIdDao()
            ?.isRowIsExist(illustId) ?: false
    }


    // Returns true if the image currently exists in the app's ContentProvider, e.g. it can be selected by Muzei at any time as the wallpaper
    private fun isDuplicateArtwork(illustId: Int): Boolean {
        // SQL pseudocode
        // FROM PixivArtProvider.providerClient SELECT _id WHERE token = illustId
        applicationContext.contentResolver.query(
            getProviderClient(applicationContext, PixivArtProvider::class.java).contentUri,
            arrayOf(ProviderContract.Artwork._ID),
            "${ProviderContract.Artwork.TOKEN} = ?",
            arrayOf(illustId.toString()),
            null
        )?.let {
            val duplicateFound = it.count > 0
            it.close()
            return duplicateFound
        }
        // This is only reachable if contentResolver.query() returned null for whatever reason, and should never happen
        return false
    }

    /*
    Ranking images are only provided with a URL to a low resolution thumbnail
    We want the high resolution image, so we need to do some work first

    Secondly, the thumbnail is always a .jpg
    For the high resolution image we require a correct file extension
    This method tests all file extensions (PNG or JPG) until a good response is received
        i.e. a response that is not a 400 class error
    Returns a ResponseBody which contains the picture to download
*/
    private fun getRemoteFileExtension(thumbnailUrl: String): ResponseBody? {
        Log.i(LOG_TAG, "Getting remote file extensions")
        /* Deliberately not turned into scope function to optimize readability */

        // This function is given a thumbnail URL like this
        //  https://tc-pximg01.techorus-cdn.com/c/240x480/img-master/img/2020/02/19/00/00/39/79583564_p0_master1200.jpg
        val transformUrl =
            "https://i.pximg.net/img-original" + thumbnailUrl.substring(thumbnailUrl.indexOf("/img/"))
                .replace("_master1200", "")
        // At this point we have a url like this:
        //  https://i.pximg.net/img-original/img/2020/02/19/00/00/39/79583564_p0.jpg
        val transformUrlNoExtension = transformUrl.substring(0, transformUrl.length - 4)
        // Last transformation removes the trailing file extensions
        //  https://i.pximg.net/img-original/img/2020/02/19/00/00/39/79583564_p0

        for (extension in IMAGE_EXTENSIONS) {
            val urlToTest = transformUrlNoExtension + extension

            val finalUrl = HostManager.get().replaceUrl(urlToTest)
            val remoteFileExtenstionRequest: Request = Request.Builder()
                .url(finalUrl)
                .get()
                .build()
            val imageHttpClient = OkHttpClient.Builder()
                .addInterceptor(Interceptor { chain: Interceptor.Chain ->
                    val original = chain.request()
                    val request = original.newBuilder()
                        .header("Referer", PixivProviderConst.PIXIV_API_HOST_URL)
                        .build()
                    chain.proceed(request)
                })
                .build()

            imageHttpClient.newCall(remoteFileExtenstionRequest).execute().let {
                if (it.isSuccessful) {
                    Log.i(LOG_TAG, "Getting remote file extensions completed")
                    return it.body
                }
            }
        }
        // TODO don't throw a null, throw an exception
        return null
    }

    // Each call to this function returns a single Ranking artwork
    private fun getArtworkRanking(contents: Contents): Artwork {
        Log.i(LOG_TAG, "Getting ranking artwork")
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)

        // Filtering
        Log.i(LOG_TAG, "Filtering ranking artwork")
        val rankingArtwork = filterArtworkRanking(
            contents.artworks,
            sharedPrefs.getBoolean("pref_showManga", false),
            sharedPrefs.getStringSet("pref_rankingFilterSelect", setOf("0")) ?: setOf("0"),
            sharedPrefs.getString("pref_aspectRatioSelect", "0")?.toInt() ?: 0,
            sharedPrefs.getInt("prefSlider_minViews", 0),
            sharedPrefs.getInt("prefSlider_minimumWidth", 0),
            sharedPrefs.getInt("prefSlider_minimumHeight", 0)
        )
        Log.i(LOG_TAG, "Filtering ranking artwork completed")

        val attribution = contents.date.run {
            substring(0, 4) + "/" + substring(4, 6) + "/" + substring(6, 8) + " "
        }.plus(
            when (contents.mode) {
                "daily" -> applicationContext.getString(R.string.attr_daily)
                "weekly" -> applicationContext.getString(R.string.attr_weekly)
                "monthly" -> applicationContext.getString(R.string.attr_monthly)
                "rookie" -> applicationContext.getString(R.string.attr_rookie)
                "original" -> applicationContext.getString(R.string.attr_original)
                "male" -> applicationContext.getString(R.string.attr_male)
                "female" -> applicationContext.getString(R.string.attr_female)
                else -> ""
            }
        ).plus(rankingArtwork.rank)

        val token = rankingArtwork.illust_id.toString()

        // this may be null
        // if it's null, then we have experienced an issue getting remote file extensions
        val remoteFileExtension = getRemoteFileExtension(rankingArtwork.url)
        val localUri = downloadImage(
            remoteFileExtension,
            token,
            sharedPrefs.getBoolean("pref_storeInExtStorage", false)
        )
        // TODO file size limit filter
        // TODO handle this null
        // what does a null mean here
        remoteFileExtension!!.close()

        Log.i(LOG_TAG, "Getting ranking artwork completed")
        return Artwork.Builder()
            .title(rankingArtwork.title)
            .byline(rankingArtwork.user_name)
            .attribution(attribution)
            .persistentUri(localUri)
            .token(token)
            .webUri(Uri.parse(PixivProviderConst.PIXIV_ARTWORK_URL + token))
            .build()
    }

    private fun filterArtworkRanking(
        artworkList: List<RankingArtwork>,
        settingShowManga: Boolean,
        settingNsfwSelection: Set<String>,
        settingAspectRatio: Int,
        settingMinimumViewCount: Int,
        settingMinimumWidth: Int,
        settingMinimumHeight: Int
    ): RankingArtwork {
        val predicates: List<(RankingArtwork) -> Boolean> = listOfNotNull(
            { !isDuplicateArtwork(it.illust_id) },
            { isEnoughViews(it.view_count, settingMinimumViewCount) },
            { settingShowManga || !settingShowManga && it.illust_type == 0 },
            { isDesiredAspectRatio(it.width, it.height, settingAspectRatio) },
            { isDesiredPixelSize(it.width, it.height, settingMinimumHeight, settingMinimumWidth, settingAspectRatio) },
            { !isBeenDeleted(it.illust_id) },
            { settingNsfwSelection.contains(it.illust_content_type.sexual.toString()) },
            // There are only two NSFW levels. If user has selected both, don't bother filtering NSFW, they want everything
            { settingNsfwSelection.size == 2 || settingNsfwSelection.contains(it.illust_content_type.sexual.toString()) },
        )

        val filteredArtworksList = artworkList.filter { candidate ->
            predicates.all { it(candidate) }
        }.also {
            if (it.isEmpty()) {
                throw FilterMatchNotFoundException("All ranking artworks iterated over, fetching a new Contents")
            }
            Log.d(LOG_TAG, "${it.size} artworks remaining before NSFW filtering")
        }

        return filteredArtworksList.random()
    }

    private fun getArtworkAuth(
        artworkList: List<AuthArtwork>,
        isRecommended: Boolean
    ): Artwork {
        Log.i(LOG_TAG, "Getting auth artwork")
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)

        Log.i(LOG_TAG, "Filtering auth artwork")
        val selectedArtwork = filterArtworkAuth(
            artworkList,
            sharedPrefs.getBoolean("pref_showManga", false),
            sharedPrefs.getStringSet("pref_authFilterSelect", setOf("2")) ?: setOf("2"),
            sharedPrefs.getString("pref_aspectRatioSelect", "0")?.toInt() ?: 0,
            sharedPrefs.getInt("prefSlider_minViews", 0),
            isRecommended,
            sharedPrefs.getInt("prefSlider_minimumWidth", 0),
            sharedPrefs.getInt("prefSlider_minimumHeight", 0)
        )
        Log.i(LOG_TAG, "Filtering auth artwork completed")

        // Variables for submitting to Muzei
        val imageUrl: String? = if (selectedArtwork.meta_pages.isEmpty()) {
            selectedArtwork
                .meta_single_page
                .original_image_url
        } else {
            selectedArtwork
                .meta_pages[0]
                .image_urls
                .original
        }

        val useCeuiLiSAWay = true
        val imageDataResponse = if (useCeuiLiSAWay) {
            /**
             * new code, replace url host to ip address and download
             * this way runs well on my phone
             */
            val finalUrl = HostManager.get().replaceUrl(imageUrl)
            Log.d("finalUrl", finalUrl)
            val request: Request = Request.Builder().url(finalUrl).get().build()
            val imageHttpClient = OkHttpClient.Builder()
                .addInterceptor(Interceptor { chain: Interceptor.Chain ->
                    val original = chain.request()
                    val newRequest = original.newBuilder()
                        .header("Referer", PixivProviderConst.PIXIV_API_HOST_URL)
                        .build()
                    chain.proceed(newRequest)
                })
                //.addInterceptor(NetworkTrafficLogInterceptor())
                .build()
            imageHttpClient.newCall(request).execute().body
        } else {
            // its your original code
            val service =
                RestClient.getRetrofitImageInstance().create(PixivImageDownloadService::class.java)
            val call = service.downloadImage(imageUrl)
            call.execute().body()
        }

        val token = selectedArtwork.id.toString()
        val localUri = downloadImage(
            imageDataResponse, token, sharedPrefs.getBoolean("pref_storeInExtStorage", false)
        )
        imageDataResponse!!.close()

        Log.i(LOG_TAG, "Getting auth artwork completed")
        return Artwork.Builder()
            .title(selectedArtwork.title)
            .byline(selectedArtwork.user.name)
            .persistentUri(localUri)
            .token(token)
            .webUri(Uri.parse(PixivProviderConst.PIXIV_ARTWORK_URL + token))
            .build()
    }

    private fun filterArtworkAuth(
        artworkList: List<AuthArtwork>,
        settingShowManga: Boolean,
        settingNsfwSelection: Set<String>,
        settingAspectRatio: Int,
        settingMinimumViews: Int,
        settingIsRecommended: Boolean,
        settingMinimumWidth: Int,
        settingMinimumHeight: Int
    ): AuthArtwork {
        val predicates: List<(AuthArtwork) -> Boolean> = listOfNotNull(
            { !isDuplicateArtwork(it.id) },
            { settingShowManga || !settingShowManga && it.type != "manga" },
            { isDesiredAspectRatio(it.width, it.height, settingAspectRatio) },
            { isDesiredPixelSize(it.width, it.height, settingMinimumWidth, settingMinimumHeight, settingAspectRatio) },
            { isEnoughViews(it.total_view, settingMinimumViews) },
            { !isBeenDeleted(it.id) },
            {
                settingIsRecommended || settingNsfwSelection.size == 4 ||
                        settingNsfwSelection.contains(it.sanity_level.toString()) ||
                        (settingNsfwSelection.contains("8") && it.x_restrict == 1)
            }
            // If feed mode is recommended or user has selected all possible NSFW levels, then don't bother filtering NSFW
            // Recommended only provides SFW artwork
        )

        val filteredArtworksList = artworkList.filter { candidate ->
            predicates.all { it(candidate) }
        }.also {
            if (it.isEmpty()) {
                throw FilterMatchNotFoundException("All auth artworks iterated over, fetching a new Illusts")
            }
            Log.d(LOG_TAG, "${it.size} artworks remaining before NSFW filtering")
        }
        return filteredArtworksList.random()
    }

    private fun getArtworksBookmark(): List<Artwork> {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)

        // If we do not know the oldest bookmark id for the currently signed in account
        if (sharedPrefs.getLong("oldestMaxBookmarkId", 0L) == 0L) {
            findBookmarkStartTime(sharedPrefs.getString("userId", "")!!)
        }

        val bookmarksHelper = BookmarksHelper(sharedPrefs.getString("userId", "")!!)

        // find the lower bound
        val oldestBookmarkId = sharedPrefs.getLong("oldestMaxBookmarkId", 0)
        // Find the upper bound
        val currentBookmarkId: Long =
            (bookmarksHelper.getNewIllusts().next_url?.substringAfter("max_bookmark_id=")!!
                .toLong() * 1.01).toLong()

        var bookmarkArtworks = bookmarksHelper.getNewIllusts(
            (oldestBookmarkId..currentBookmarkId).random().toString()
        ).artworks
        val artworkList = mutableListOf<Artwork>()
        for (i in 0 until sharedPrefs.getInt("prefSlider_numToDownload", 2)) {
            try {
                artworkList.add(buildArtworkAuth(bookmarkArtworks, false))
            } catch (e: FilterMatchNotFoundException) {
                Log.i(LOG_TAG, "Fetching new bookmarks")
            }
            bookmarkArtworks = bookmarksHelper.getNewIllusts(
                (oldestBookmarkId..currentBookmarkId).random().toString()
            ).artworks
        }
        return artworkList
    }

    private fun getArtworksAuth(updateMode: String): List<Artwork> {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        // Determines if any extra information is needed, and passes it along
        // If bookmark, specify a second data for offset
        val data = when (updateMode) {
            "bookmark" -> sharedPrefs.getString("userId", "")
            "artist" -> sharedPrefs.getString("pref_artistId", "")
            "tag_search" -> sharedPrefs.getString("pref_tagSearch", "")
            else -> ""
        }
        // illustsHelper is stateful, stores a copy of Illusts, and can fetch a new one if needed
        val illustsHelper = IllustsHelper(updateMode, data ?: "")
        var authArtworkList = illustsHelper.getNewIllusts().artworks

        val artworkList = mutableListOf<Artwork>()
        for (i in 0 until sharedPrefs.getInt("prefSlider_numToDownload", 2)) {
            try {
                artworkList.add(
                    buildArtworkAuth(
                        authArtworkList.toMutableList(),
                        updateMode == "recommended"
                    )
                )
            } catch (e: FilterMatchNotFoundException) {
                Log.i(LOG_TAG, "Fetching new illusts")
                authArtworkList = illustsHelper.getNextIllusts().artworks
            }
        }
        return artworkList
    }

    private fun getArtworksRanking(updateMode: String): List<Artwork> {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        // contentsHelper is stateful, stores a copy of Contents, and can fetch a new one if needed
        val contentsHelper = ContentsHelper(updateMode)
        var contents = contentsHelper.getNewContents()

        val artworkList = mutableListOf<Artwork>()
        for (i in 0 until sharedPrefs.getInt("prefSlider_numToDownload", 2)) {
            try {
                artworkList.add(buildArtworkRanking(contents))
            } catch (e: FilterMatchNotFoundException) {
                Log.i(LOG_TAG, "Fetching new contents")
                contents = contentsHelper.getNextContents()
            }
        }
        return artworkList
    }

    // Returns a list of Artworks to Muzei
    //
    private fun getArtworks(): List<Artwork>? {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        var updateMode = sharedPrefs.getString("pref_updateMode", "daily") ?: "daily"

        // Gets an up to date access token if required
        if (AUTH_MODES.contains(updateMode)) {
            try {
                getAccessToken()
            } catch (e: AccessTokenAcquisitionException) {
                updateMode = authHandleAuthFailure(sharedPrefs) ?: return null
            }
        }

        // App has functionality to temporarily or permanently change the update mode if authentication fails
        // i.e. update mode can change between the previous if block and this if block
        // Thus two identical if statements are required
        Log.i(LOG_TAG, "Feed mode: $updateMode")
        val artworkList: List<Artwork> = if (updateMode == "bookmark") {
            getArtworksBookmark()
        } else if (AUTH_MODES.contains(updateMode)) {
            getArtworksAuth(updateMode)
        } else {
            getArtworksRanking(updateMode)
        }
        Log.i(LOG_TAG, "Submitting ${artworkList.size} artworks")
        return artworkList
    }

    private fun authHandleAuthFailure(sharedPrefs: SharedPreferences): String? {
        Log.i(LOG_TAG, "Failed to acquire access token")
        when (sharedPrefs.getString("pref_authFailAction", "changeDaily")) {
            "changeDaily" -> {
                Log.i(LOG_TAG, "Changing mode to daily")
                sharedPrefs.edit().putString("pref_updateMode", "daily").apply()
                PixivMuzeiSupervisor.post(Runnable {
                    Toast.makeText(applicationContext, R.string.toast_authFailedSwitch, Toast.LENGTH_SHORT).show()
                })
                return "daily"
            }
            "doNotChange_downDaily" -> {
                Log.i(LOG_TAG, "Downloading a single daily")
                PixivMuzeiSupervisor.post(Runnable {
                    Toast.makeText(applicationContext, R.string.toast_authFailedDown, Toast.LENGTH_SHORT).show()
                })
                return "daily"
            }
            "doNotChange_doNotDown" -> {
                Log.i(LOG_TAG, "Retrying with no changes")
                PixivMuzeiSupervisor.post(Runnable {
                    Toast.makeText(applicationContext, R.string.toast_authFailedRetry, Toast.LENGTH_SHORT).show()
                })
                return null
            }
            else -> return null
        }
    }

    override fun doWork(): Result {
        Log.i(LOG_TAG, "Starting work")
        with(getProviderClient(applicationContext, PixivArtProvider::class.java)) {
            val artworks = getArtworks() ?: return Result.retry()
            if (clearArtwork) {
                clearArtwork = false
                setArtwork(artworks)
            } else {
                addArtwork(artworks)
            }
        }
        Log.i(LOG_TAG, "Work completed")
        return Result.success()
    }
}

// refactor one more function
