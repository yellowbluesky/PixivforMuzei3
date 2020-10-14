/*
 *     This file is part of PixivforMuzei3.
 *
 *     PixivforMuzei3 is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program  is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.antony.muzei.pixiv.provider

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import androidx.work.*
import com.antony.muzei.pixiv.AppDatabase
import com.antony.muzei.pixiv.PixivMuzeiSupervisor.getAccessToken
import com.antony.muzei.pixiv.PixivMuzeiSupervisor.post
import com.antony.muzei.pixiv.R
import com.antony.muzei.pixiv.provider.exceptions.AccessTokenAcquisitionException
import com.antony.muzei.pixiv.provider.exceptions.CorruptFileException
import com.antony.muzei.pixiv.provider.exceptions.FilterMatchNotFoundException
import com.antony.muzei.pixiv.provider.network.AuthJsonServerResponse
import com.antony.muzei.pixiv.provider.network.ImageDownloadServerResponse
import com.antony.muzei.pixiv.provider.network.RankingJsonServerResponse
import com.antony.muzei.pixiv.provider.network.RestClient
import com.antony.muzei.pixiv.provider.network.moshi.AuthArtwork
import com.antony.muzei.pixiv.provider.network.moshi.Contents
import com.antony.muzei.pixiv.provider.network.moshi.Illusts
import com.antony.muzei.pixiv.provider.network.moshi.RankingArtwork
import com.google.android.apps.muzei.api.provider.Artwork
import com.google.android.apps.muzei.api.provider.ProviderContract.getProviderClient
import okhttp3.ResponseBody
import retrofit2.Call
import java.io.*
import java.util.*
import java.util.concurrent.TimeUnit

class PixivArtWorker(
        context: Context,
        params: WorkerParameters
) : Worker(context, params) {

    companion object {
        private const val LOG_TAG = "ANTONY_WORKER"
        private const val WORKER_TAG = "ANTONY"
        private val IMAGE_EXTENSIONS = arrayOf(".png", ".jpg")
        private var clearArtwork = false

        fun enqueueLoad(clear: Boolean, context: Context?) {
            if (clear) {
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
                }.also { request ->
                    WorkManager.getInstance(it).enqueueUniqueWork(WORKER_TAG, ExistingWorkPolicy.KEEP, request)
                }
            }
            // Must be a uniqueWork
            // If not Muzei will queue MANY at once on initial load
            // This is good for saturating a network link and for fast picture downloads
            // However, race conditions develop if work required is authenticated
            // unique work ensures that only one Artwork is being processed at once
        }
    }

    enum class FileType {
        OTHER, JPEG, PNG
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
    @Throws(IOException::class)
    private fun getRemoteFileExtension(url: String): ResponseBody? {
        Log.i(LOG_TAG, "Getting remote file extensions")
        // This function is given a thumbnail URL like this
        //  https://tc-pximg01.techorus-cdn.com/c/240x480/img-master/img/2020/02/19/00/00/39/79583564_p0_master1200.jpg

        val transformUrl = "https://i.pximg.net/img-original" + url.substring(url.indexOf("/img/")).replace("_master1200", "")
        // At this point we have a url like this:
        //  https://i.pximg.net/img-original/img/2020/02/19/00/00/39/79583564_p0.jpg

        val transformUrlNoExtension = transformUrl.substring(0, transformUrl.length - 4)
        // Last transformation to remove the file extension
        //  https://i.pximg.net/img-original/img/2020/02/19/00/00/39/79583564_p0

        val bypassActive = PreferenceManager.getDefaultSharedPreferences(applicationContext).getBoolean("pref_enableNetworkBypass", false)
        for (extension in IMAGE_EXTENSIONS) {
            val urlToTest = transformUrlNoExtension + extension
            val service = RestClient.getRetrofitImageInstance(bypassActive).create(ImageDownloadServerResponse::class.java)
            val responseBodyResponse = service.downloadImage(urlToTest).execute()
            val response = responseBodyResponse.raw()
            if (response.isSuccessful) {
                Log.i(LOG_TAG, "Gotten remote file extensions")
                return responseBodyResponse.body()
            }
        }
        Log.e(LOG_TAG, "Failed to get remote file extensions")
        // TODO don't throw a null, throw an exception
        return null
    }

    /*
        PixivforMuzei3 often downloads an incomplete image, i.e. the lower section of images is not
        downloaded, the file header is intact but file closer is not present.
        This function converts the image to a byte representation, then checks the last few bytes
        in the image for a valid file closer.
        If image is incomplete, throws CorruptFileException
        Returns:
            PNG
            JPG
            CorruptFileException
    */
    @Throws(IOException::class, CorruptFileException::class)
    private fun getLocalFileExtension(image: File): FileType {
        Log.d(LOG_TAG, "getting file type")
        val randomAccessFile = RandomAccessFile(image, "r")
        val byteArray = ByteArray(10)
        randomAccessFile.read(byteArray, 0, 2)
        var fileType: FileType = FileType.OTHER

        // ByteArray used instead of read()
        //  read() increments the file-pointer offset, causing successive reads to read different bytes
        if (byteArray[0] == 0x89.toByte() && byteArray[1] == 0x50.toByte()) {
            randomAccessFile.seek(image.length() - 8)
            if (randomAccessFile.readShort() == 0x4945.toShort() && randomAccessFile.readShort() == 0x4E44.toShort()) {
                Log.d(LOG_TAG, "PNG")
                fileType = FileType.PNG
            } else {
                randomAccessFile.close()
                throw CorruptFileException("Corrupt PNG")
            }
        } else if (byteArray[0] == 0xFF.toByte() && byteArray[1] == 0xD8.toByte()) {
            randomAccessFile.seek(image.length() - 2)
            if (randomAccessFile.readShort() == 0xFFD9.toShort()) {
                Log.d(LOG_TAG, "JPG")
                fileType = FileType.JPEG
            } else {
                randomAccessFile.close()
                throw CorruptFileException("Corrupt JPG")
            }
        }
        randomAccessFile.close()
        return fileType
    }

    /*
        First downloads the file to ExternalFilesDir, always with a png file extension
        Checks if the file is incomplete; if incomplete deletes it and passes a CorruptFileException
        up the chain
        Otherwise returns a Uri to the File to the caller
        If option is checked, also makes a copy into external storage
        The external storage copy is not used for backing any database
        The external storage copy also has correct file extensions
     */
    @Throws(IOException::class, CorruptFileException::class)
    private fun downloadFile(responseBody: ResponseBody?,
                             filename: String): Uri {
        Log.i(LOG_TAG, "Downloading file")
        val context = applicationContext
        val imageInternal = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "$filename.png")
        val fosInternal = FileOutputStream(imageInternal)
        val inputStreamNetwork = responseBody!!.byteStream()
        val bufferTemp = ByteArray(1024 * 1024 * 10)
        var readTemp: Int
        while (inputStreamNetwork.read(bufferTemp).also { readTemp = it } != -1) {
            fosInternal.write(bufferTemp, 0, readTemp)
        }
        inputStreamNetwork.close()
        fosInternal.close()
        responseBody.close()

        // TODO make this an enum
        val fileExtension = getLocalFileExtension(imageInternal)
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)

        // If option in SettingsActivity is checked AND permission is granted
        if (sharedPrefs.getBoolean("pref_storeInExtStorage", false) &&
                ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            var fosExternal: OutputStream? = null
            var allowedToStoreIntoExternal = false

            // Android 10 introduced scoped storage
            // Different code path depending on Android APi level
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentResolver = context.contentResolver
                val contentValues = ContentValues()

                // Check if existing copy of file exists
                val projection = arrayOf(MediaStore.Images.Media._ID)
                val selection = "title = ?"
                //String selection ={MediaStore.Images.Media.DISPLAY_NAME + " = ? AND ", MediaStore.Images.Media.RELATIVE_PATH + " = ?"};
                val selectionArgs = arrayOf(filename)
                val cursor = contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, selection, selectionArgs, null)
                if (cursor!!.count == 0) {
                    contentValues.put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PixivForMuzei3")
                    if (fileExtension == FileType.PNG) {
                        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    } else if (fileExtension == FileType.JPEG) {
                        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    }
                    // Gets a list of mounted volumes
                    val stringSet = MediaStore.getExternalVolumeNames(applicationContext)
                    val volumeName: String
                    // Sets the download location to be on phone storage or on the SD Card
                    val storeIntoWhichStorage: String? = sharedPrefs.getString("pref_selectWhichExtStorage", "empty")
                    volumeName = if (storeIntoWhichStorage.equals("sdCard")) {
                        stringSet.elementAt(1)
                    } else {
                        stringSet.elementAt(0)
                    }
                    // Gives us a URI to save the image to
                    val imageUri = contentResolver.insert(MediaStore.Images.Media.getContentUri(volumeName), contentValues)
                    fosExternal = contentResolver.openOutputStream(imageUri!!)
                    allowedToStoreIntoExternal = true
                }
                cursor.close()
            } else {
                val directoryString = "/storage/emulated/0/Pictures/PixivForMuzei3/"
                val directory = File(directoryString)
                if (!directory.exists()) {
                    directory.mkdirs()
                }

                // If the image has already been downloaded, do not redownload
                val imagePng = File(directoryString, "$filename.png")
                val imageJpg = File(directoryString, "$filename.jpg")
                if (!imageJpg.exists() && !imagePng.exists()) {
                    if (fileExtension == FileType.PNG) {
                        fosExternal = FileOutputStream(imagePng)
                        context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(imagePng)))
                    } else if (fileExtension == FileType.JPEG) {
                        fosExternal = FileOutputStream(imageJpg)
                        context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(imageJpg)))
                    }
                    allowedToStoreIntoExternal = true
                }
            }

            // Finally copies the image into external storage if allowed to
            if (allowedToStoreIntoExternal) {
                val fis = FileInputStream(imageInternal)
                val buffer = ByteArray(1024 * 1024 * 10)
                var lengthInternal: Int
                while (fis.read(buffer).also { lengthInternal = it } > 0) {
                    fosExternal!!.write(buffer, 0, lengthInternal)
                }
                fosExternal!!.close()
                fis.close()
            }
        }
        return Uri.fromFile(imageInternal)
    }

    // TODO is this even necessary anymore
    private fun isArtworkNull(artwork: Artwork?): Boolean =
            artwork.also {
                it ?: Log.e(LOG_TAG, "Null artwork returned, retrying at later time")
            } == null

    /*
        Provided an artowrk ID (token), traverses the PixivArtProvider ContentProvider to sees
        if there is already a duplicate artwork with the same ID (token)
     */
    private fun isDuplicateArtwork(token: Int): Boolean {
        var duplicateFound = false
        val projection = arrayOf("_id")
        val selection = "token = ?"
        val selectionArgs = arrayOf(token.toString())
        val conResUri = getProviderClient(applicationContext, PixivArtProvider::class.java).contentUri
        val cursor: Cursor? = applicationContext.contentResolver.query(conResUri, projection, selection, selectionArgs, null)

        if (cursor != null) {
            duplicateFound = cursor.count > 0
        }
        cursor?.close()
        return duplicateFound
    }

    private fun hasDesiredPixelSize(
            width: Int,
            height: Int,
            minimumWidth: Int,
            minimumHeight: Int,
            aspectRatioSetting: Int
    ): Boolean =
            when (aspectRatioSetting) {
                0 -> height >= (minimumHeight * 10) && width >= (minimumWidth * 10)
                1 -> height >= (minimumHeight * 10)
                2 -> width >= (minimumWidth * 10)
                else -> true
            }

    /*
        0   Any aspect ratio
        1   Landscape
        2   Portrait
     */
    private fun isDesiredAspectRatio(
            width: Int,
            height: Int,
            aspectRatioSetting: Int
    ): Boolean =
            when (aspectRatioSetting) {
                0 -> true
                1 -> height >= width
                2 -> height <= width
                else -> true
            }

    // Scalar must match with scalar in SettingsActivity
    private fun isEnoughViews(
            artworkViewCount: Int,
            minimumDesiredViews: Int
    ): Boolean = artworkViewCount >= minimumDesiredViews * 500

    private fun isImageTooLarge(sizeBytes: Long, limitBytes: Long): Boolean = sizeBytes > limitBytes

    private fun isBeenDeleted(artworkId: Int): Boolean =
            (AppDatabase.getInstance(applicationContext)?.deletedArtworkIdDao()?.isRowIsExist(artworkId)!!)


    /*
        Receives a Contents object, which contains a representnation of a set of artworks
        Passes it off to filterArtworkRanking(), which returns one ranking artwork
        Builds an Artwork object off returned ranking artwork
     */
    @Throws(IOException::class, CorruptFileException::class, FilterMatchNotFoundException::class)
    private fun getArtworkRanking(contents: Contents?): Artwork {
        Log.i(LOG_TAG, "getArtworkRanking(): Entering")
        var attribution = ""
        when (contents!!.mode) {
            "daily" -> attribution = applicationContext.getString(R.string.attr_daily)
            "weekly" -> attribution = applicationContext.getString(R.string.attr_weekly)
            "monthly" -> attribution = applicationContext.getString(R.string.attr_monthly)
            "rookie" -> attribution = applicationContext.getString(R.string.attr_rookie)
            "original" -> attribution = applicationContext.getString(R.string.attr_original)
            "male" -> attribution = applicationContext.getString(R.string.attr_male)
            "female" -> attribution = applicationContext.getString(R.string.attr_female)
            else -> ""
        }
        val attributionDate = contents.date
        val attTrans = attributionDate.substring(0, 4) + "/" + attributionDate.substring(4, 6) + "/" + attributionDate.substring(6, 8) + " "

        //writeToFile(overallJson, "rankingLog.txt");
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)

        // Filter variables, to pass to filterArtworkRanking()
        val showManga = sharedPrefs.getBoolean("pref_showManga", false)
        val defaultRankingSelect: MutableSet<String> = HashSet()
        defaultRankingSelect.add("0")
        val rankingFilterSelect = sharedPrefs.getStringSet("pref_rankingFilterSelect", defaultRankingSelect)
        val aspectRatioSettings = sharedPrefs.getString("pref_aspectRatioSelect", "0")!!.toInt()
        val minimumViews = sharedPrefs.getInt("prefSlider_minViews", 0)

        // Filtering
        val rankingArtwork = filterArtworkRanking(contents.artworks.toMutableList(),
                showManga,
                rankingFilterSelect,
                aspectRatioSettings,
                minimumViews,
                sharedPrefs.getInt("prefSlider_minimumWidth", 0),
                sharedPrefs.getInt("prefSlider_minimumHeight", 0)
        )

        // Variables to submit to Muzei
        val token = rankingArtwork!!.illust_id.toString()
        attribution = attTrans + attribution
        attribution += rankingArtwork.rank

        // Actually downloading the selected artwork
        val remoteFileExtension = getRemoteFileExtension(rankingArtwork.url)
        val localUri = downloadFile(remoteFileExtension, token)

//        val fileSizeLimit = sharedPrefs.getInt("prefSlider_maxFileSize", 0)
//        // 1024 scalar to convert MB to byte
//        if (fileSizeLimit != 0 && isImageTooLarge(remoteFileExtension!!.contentLength(), fileSizeLimit * 1048576.toLong()))
//        {
//            Log.v("SIZE", "too chonk")
//            //throw new ImageTooLargeException("");
//            // grab a new image, somehwo loop back
//        }
//        else
//        {
//            Log.v("SIZE", "good size")
//        }
        remoteFileExtension!!.close()
        Log.i(LOG_TAG, "getArtworkRanking(): Exited")
        return Artwork.Builder()
                .title(rankingArtwork.title)
                .byline(rankingArtwork.user_name)
                .attribution(attribution)
                .persistentUri(localUri)
                .token(token)
                .webUri(Uri.parse(PixivArtProviderDefines.MEMBER_ILLUST_URL + token))
                .build()
    }

    /*
        Filters through a MutableList containing RankingArtwork's.
        Picks one image based on the user's various filtering settings.

            NSFW filtering is performed by checking the value of the "sexual" JSON string
            Manga filtering is performed by checking the value of the "illust_type" JSON string
    */
    @Throws(FilterMatchNotFoundException::class)
    private fun filterArtworkRanking(rankingArtworkList: MutableList<RankingArtwork>,
                                     showManga: Boolean,
                                     selectedFilterLevelSet: Set<String>?,
                                     aspectRatioSetting: Int,
                                     minimumViews: Int,
                                     minimumWidth: Int,
                                     minimumHeight: Int
    ): RankingArtwork? {
        Log.i(LOG_TAG, "filterRanking(): Entering")
        rankingArtworkList.shuffle()
        for (randomArtwork in rankingArtworkList) {
            if (isDuplicateArtwork(randomArtwork.illust_id)) {
                Log.v(LOG_TAG, "Duplicate ID: " + randomArtwork.illust_id)
                continue
            }
            if (!isEnoughViews(randomArtwork.view_count, minimumViews)) {
                Log.v(LOG_TAG, "Not enough views")
                continue
            }
            if (!showManga && randomArtwork.illust_type != 0) {
                Log.v(LOG_TAG, "Manga not desired " + randomArtwork.illust_id)
                continue
            }
            if (!isDesiredAspectRatio(randomArtwork.width,
                            randomArtwork.height, aspectRatioSetting)) {
                Log.v(LOG_TAG, "Rejecting aspect ratio")
                continue
            }
            if (!hasDesiredPixelSize(randomArtwork.width, randomArtwork.height, minimumWidth, minimumHeight, aspectRatioSetting)) {
                Log.v(LOG_TAG, "Image below desired pixel size")
                continue
            }
            if (isBeenDeleted(randomArtwork.illust_id)) {
                Log.v(LOG_TAG, "Previously deleted")
                continue
            }

            for (s in selectedFilterLevelSet!!) {
                if (s.toInt() == randomArtwork.illust_content_type.sexual) {
                    return randomArtwork
                }
            }
        }
        throw FilterMatchNotFoundException("")
    }

    /*
        Receives a list of auth artworks
        Passes it off to filterArtworkRanking(), which returns one ranking artwork
        Builds an Artwork object off returned ranking artwork
     */
    @Throws(FilterMatchNotFoundException::class, IOException::class, CorruptFileException::class)
    private fun getArtworkAuth(authArtworkList: List<AuthArtwork>, isRecommended: Boolean): Artwork {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)

        // Filter variables to pass to filterArtworkAuth()
        val aspectRatioSettings = sharedPrefs.getString("pref_aspectRatioSelect", "0")!!.toInt()
        val showManga = sharedPrefs.getBoolean("pref_showManga", false)
        // null default case allowed
        // App *MUST* be first opened in order to change the update mode and log in
        // Opening the app populates the shared preference with a default entry
        // As opposed to ranking, where there can be an empty shared preference
        val selectedFilterLevel = sharedPrefs.getStringSet("pref_authFilterSelect", null)
        val minimumViews = sharedPrefs.getInt("prefSlider_minViews", 0)

        // Filtering
        val selectedArtwork = filterArtworkAuth(
                authArtworkList.toMutableList(),
                showManga,
                selectedFilterLevel,
                aspectRatioSettings,
                minimumViews,
                isRecommended,
                sharedPrefs.getInt("prefSlider_minimumWidth", 0),
                sharedPrefs.getInt("prefSlider_minimumHeight", 0)
        )

        // Variables for submitting to Muzei
        val imageUrl: String
        imageUrl = if (selectedArtwork!!.meta_pages.size == 0) {
            Log.d(LOG_TAG, "Picture is a single image")
            selectedArtwork
                    .meta_single_page
                    .original_image_url
        } else {
            Log.d(LOG_TAG, "Picture is part of an album")
            selectedArtwork
                    .meta_pages[0]
                    .image_urls
                    .original
        }
        val token = selectedArtwork.id.toString()
        val bypassActive = sharedPrefs.getBoolean("pref_enableNetworkBypass", false)

        // Actually downloading the file
        val service = RestClient.getRetrofitImageInstance(bypassActive).create(ImageDownloadServerResponse::class.java)
        val call = service.downloadImage(imageUrl)
        val imageDataResponse = call.execute().body()
        val localUri = downloadFile(imageDataResponse, token)
//        val fileSizeLimitMegabytes = sharedPrefs.getInt("prefSlider_maxFileSize", 0)
//        // 1024 scalar to convert from MB to bytes
//        if (fileSizeLimitMegabytes != 0 && isImageTooLarge(imageDataResponse!!.contentLength(), fileSizeLimitMegabytes * 1048576.toLong()))
//        {
//            Log.v("SIZE", "too chonk")
//        }
//        else
//        {
//            Log.v("SIZE", "good size")
//        }

        imageDataResponse!!.close()
        Log.i(LOG_TAG, "getArtworkAuth(): Exited")
        return Artwork.Builder()
                .title(selectedArtwork.title)
                .byline(selectedArtwork.user.name)
                .persistentUri(localUri)
                .token(token)
                .webUri(Uri.parse(PixivArtProviderDefines.MEMBER_ILLUST_URL + token))
                .build()
    }

    /*
        Called by getArtworkAuth to return details about an artwork that complies with
        filtering restrictions set by the user

        For NSFW filtering the two relevant JSON strings are "sanity_level" and "x_restrict"
            sanity_level
                2 -> Completely SFW
                4 -> Moderately ecchi e.g. beach bikinis, slight upskirts
                6 -> Very ecchi e.g. more explicit and suggestive themes
             x_restrict
                1 -> R18 e.g. nudity and penetration

            In this code x_restrict is treated as a level 8 sanity_level

        For manga filtering, the value of the "type" string is checked for either "manga" or "illust"
     */
    @Throws(FilterMatchNotFoundException::class)
    private fun filterArtworkAuth(authArtworkList: MutableList<AuthArtwork>,
                                  showManga: Boolean,
                                  selectedFilterLevelSet: Set<String>?,
                                  aspectRatioSetting: Int,
                                  minimumViews: Int,
                                  isRecommended: Boolean,
                                  minimumWidth: Int,
                                  minimumHeight: Int
    ): AuthArtwork? {
        Log.i(LOG_TAG, "filterArtworkAuth(): Entering")
        authArtworkList.shuffle()
        for (randomArtwork in authArtworkList) {
            // Check if duplicate before any other check to not waste time
            if (isDuplicateArtwork(randomArtwork.id)) {
                Log.v(LOG_TAG, "Duplicate ID: " + randomArtwork.id)
                continue
            }

            // If user does not want manga to display
            if (!showManga && randomArtwork.type != "illust") {
                Log.d(LOG_TAG, "Manga not desired")
                continue
            }

            // Filter artwork based on chosen aspect ratio
            if (!isDesiredAspectRatio(randomArtwork.width,
                            randomArtwork.height, aspectRatioSetting)) {
                Log.d(LOG_TAG, "Rejecting aspect ratio")
                continue
            }

            if (!hasDesiredPixelSize(randomArtwork.width, randomArtwork.height, minimumWidth, minimumHeight, aspectRatioSetting)) {
                Log.v(LOG_TAG, "Image below desired pixel size")
                continue
            }

            if (!isEnoughViews(randomArtwork.total_view, minimumViews)) {
                Log.d(LOG_TAG, "Not enough views")
                continue
            }

            if (isBeenDeleted(randomArtwork.id)) {
                Log.v(LOG_TAG, "Previously deleted")
                continue
            }

            // All artworks in recommended are SFW, we can skip this check
            if (isRecommended) {
                return randomArtwork
            } else {
                // See if there is a match between chosen artwork's sanity level and those desired
                for (s in selectedFilterLevelSet!!) {
                    if (s == randomArtwork.sanity_Level.toString()) {
                        Log.d(LOG_TAG, "sanity_level found is " + randomArtwork.sanity_Level)
                        return randomArtwork
                    } else if (s == "8" && randomArtwork.x_restrict == 1) {
                        Log.d(LOG_TAG, "x_restrict found")
                        return randomArtwork
                    }
                }
            }
        }
        throw FilterMatchNotFoundException("too many retries")

    }

    /*
        Main meat of the app
        Obtains an up to date access token if required
        Obtains objects that represent each update mode, and will continue to obtain objects until
        enough artworks have satisfied
        Returns a list of Artwork's for submission into Muzei
     */
    @get:Throws(IOException::class, CorruptFileException::class)
    private val artwork: ArrayList<Artwork>?
        get() {
            val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            var updateMode = sharedPrefs.getString("pref_updateMode", "daily")

            // These modes require an access token, so we check for and acquire one first
            if (PixivArtProviderDefines.AUTH_MODES.contains(updateMode)) {
                try {
                    getAccessToken()
                } catch (ex: AccessTokenAcquisitionException) {
                    when (sharedPrefs.getString("pref_authFailAction", "changeDaily")) {
                        "changeDaily" -> {
                            Log.d(LOG_TAG, "Auth failed, changing mode to daily")
                            sharedPrefs.edit().putString("pref_updateMode", "daily").apply()
                            updateMode = "daily"
                            post(Runnable {
                                Toast.makeText(
                                        applicationContext,
                                        R.string.toast_authFailedSwitch,
                                        Toast.LENGTH_SHORT
                                ).show()
                            })
                        }
                        "doNotChange_downDaily" -> {
                            Log.d(LOG_TAG, "Auth failed, downloading a single daily")
                            updateMode = "daily"
                            post(Runnable {
                                Toast.makeText(applicationContext, R.string.toast_authFailedDown, Toast.LENGTH_SHORT).show()
                            })
                        }
                        "doNotChange_doNotDown" -> {
                            Log.d(LOG_TAG, "Auth failed, retrying with no changes")
                            post(Runnable {
                                Toast.makeText(
                                        applicationContext,
                                        R.string.toast_authFailedRetry,
                                        Toast.LENGTH_SHORT
                                ).show()
                            })
                            return null
                        }
                    }
                }
            }

            val artworkArrayList = ArrayList<Artwork>()
            var artwork: Artwork
            val bypassActive = sharedPrefs.getBoolean("pref_enableNetworkBypass", false)
            if (PixivArtProviderDefines.AUTH_MODES.contains(updateMode)) {
                val service = RestClient.getRetrofitAuthInstance(bypassActive).create(AuthJsonServerResponse::class.java)
                var call: Call<Illusts?>
                call = when (updateMode) {
                    "follow" -> service.followJson
                    "bookmark" -> service.getBookmarkJson(sharedPrefs.getString("userId", ""))
                    "recommended" -> service.recommendedJson
                    "artist" -> service.getArtistJson(sharedPrefs.getString("pref_artistId", ""))
                    "tag_search" -> service.getTagSearchJson(sharedPrefs.getString("pref_tagSearch", ""))
                    else -> throw IllegalStateException("Unexpected value: $updateMode")
                }
                var illusts = call.execute().body()
                var authArtworkList = illusts!!.artworks
                for (i in 0 until sharedPrefs.getInt("prefSlider_numToDownload", 2)) {
                    try {
                        artwork = getArtworkAuth(authArtworkList, updateMode == "recommended")
                        if (isArtworkNull(artwork)) {
                            throw CorruptFileException("")
                        }
                        artworkArrayList.add(artwork)
                    } catch (e: FilterMatchNotFoundException) {
                        e.printStackTrace()
                        // I'm not sure how many times we can keep getting the nextUrl
                        // TODO implement a limit on the number of nextUrls
                        call = service.getNextUrl(illusts!!.nextUrl)
                        illusts = call.execute().body()
                        authArtworkList = illusts!!.artworks
                    }
                }
            } else {
                val service = RestClient.getRetrofitRankingInstance(bypassActive).create(RankingJsonServerResponse::class.java)
                var call = service.getRankingJson(updateMode)
                var contents = call.execute().body()
                var pageNumber = 1
                var date = contents!!.date
                var prevDate = contents.prev_date
                for (i in 0 until sharedPrefs.getInt("prefSlider_numToDownload", 2)) {
                    try {
                        artwork = getArtworkRanking(contents)
                        if (isArtworkNull(artwork)) {
                            throw CorruptFileException("")
                        }
                        artworkArrayList.add(artwork)
                    } catch (e: FilterMatchNotFoundException) {
                        e.printStackTrace()
                        // If enough artworks are not found in the 50 from the first page of the rankings,
                        // keep looking through the next pages or days
                        // We can continue to look through the 450 rankings for that day
                        // There is a tenth page actually, but the next page number integer becomes a boolean
                        // GSON can't handle this and throws a fit.
                        // Thus I've limited my app to parsing only the top 450 rankings
                        if (pageNumber != 9) {
                            pageNumber++
                            call = service.getRankingJson(updateMode, pageNumber, date)
                            contents = call.execute().body()
                        } else {
                            // If we for some reason cannot find enough artwork to satisfy the filter
                            // from the top 450, then we can look at the previous day's ranking
                            pageNumber = 1
                            call = service.getRankingJson(updateMode, pageNumber, prevDate)
                            contents = call.execute().body()
                            date = contents!!.date
                            prevDate = contents.prev_date
                        }
                    }
                }
            }
            Log.i(LOG_TAG, "Submitting " + sharedPrefs.getInt("prefSlider_numToDownload", 2) +
                    " artworks")
            return artworkArrayList
        }

    override fun doWork(): Result {
        Log.d(LOG_TAG, "Starting work")
        val client = getProviderClient(applicationContext, PixivArtProvider::class.java)
        val artworkArrayList: ArrayList<Artwork>?
        artworkArrayList = try {
            artwork
        } catch (e: IOException) {
            e.printStackTrace()
            return Result.retry()
        } catch (e: CorruptFileException) {
            e.printStackTrace()
            return Result.retry()
        }
        if (clearArtwork) {
            clearArtwork = false
            client.setArtwork(artworkArrayList!!)
        } else {
            client.addArtwork(artworkArrayList!!)
        }
        Log.d(LOG_TAG, "Work completed")
        return Result.success()
    }

}
