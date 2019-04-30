import com.drew.imaging.ImageMetadataReader
import com.drew.lang.GeoLocation

import com.drew.metadata.Metadata
import com.drew.metadata.exif.ExifIFD0Directory
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.drew.metadata.exif.GpsDirectory
import com.drew.metadata.file.FileSystemDirectory
import com.drew.metadata.mp4.Mp4Directory

import com.google.maps.GeoApiContext
import com.google.maps.GeocodingApi
import com.google.maps.model.AddressType
import com.google.maps.model.GeocodingResult
import com.google.maps.model.LatLng

import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import java.time.LocalDate
import java.time.ZoneId

/**
 * The Mover class is used to move files from a source location to the destination location. It uses
 * the @destinationPattern to determine how the files should be moved.
 *
 * Based on the switch @moveFiles the mover determines if files should actually be moved or just be copied
 * to the new location.
 *
 * The Mover class uses google API's to determine location data from the lat/long information in the actual files.
 */
class Mover (
        private val sourcePath : String,
        private val destinationPath : String,
        private val destinationPattern  : String,
        private val moveFiles: Boolean = true,
        private val googleAPIKey : String = "",
        private val progressWriter: ProgressWriter
) {
    private var apiContext : GeoApiContext? = null
    private val latLngCache = HashMap<String, String>()

    private var duplicatesFound : Int = 0
    private var filesMoved : Int = 0
    private var invalidFiles : Int = 0
    private var skippedFiles: Int = 0

    /**
     * Simple helper function to dump all meta data in the file
     */
    private fun File.dumpMetaData() {
        val metaData = ImageMetadataReader.readMetadata(this) as Metadata

        metaData.dump()
    }

    /**
     * Simple helper to dump all metadata directories to the log
     */
    private fun Metadata.dump() {
        this.directories.forEach { dir ->
            appendToLogStream("\n\n${dir.name}")
            dir.tags.forEach { tag ->
                appendToLogStream("${tag.tagName} - ${tag.description}")
            }
        }
    }

    /**
     * with this method files are moved or copied from source location to destination location. files are
     * transformed using the destination pattern. the destination pattern uses file metadata to create file
     * or folder structure
     */
    fun execute() {
        val source = File(sourcePath)
        if (!source.exists()) throw FileNotFoundException(source.name)

        duplicatesFound = 0
        filesMoved = 0
        invalidFiles = 0
        skippedFiles = 0

        val destinationFolder = File(destinationPath)
        if (!destinationFolder.exists()) destinationFolder.mkdirs()

        // as described in the API documentation, the context should be used as a singleton
        if (!googleAPIKey.isEmpty())
            apiContext = GeoApiContext.Builder().apiKey(googleAPIKey).build()

        processFolder(source, destinationFolder)

        appendToLogStream("moved or copied $filesMoved files to destintation")
        appendToLogStream("found $duplicatesFound files already existed in destinationFolder")
        appendToLogStream("$invalidFiles files could not be moved or copied ")
        appendToLogStream("$skippedFiles files where ignored")
    }

    /**
     * process all files in the source folder
     */
    private fun processFolder(source : File, destinationFolder : File) {
        appendToLogStream ( "processing folder ${source.name}" )

        // process files only, skipping all folders
        source.walk().forEach { file ->
            try {
                if (file.isFile) processFile(file, destinationFolder)
            } catch (e: Exception) {
                appendToLogStream("failed to process file '${file.name}' because of ${e.message}")
                invalidFiles++
            }
        }
    }

    /**
     * process a single file. only image and video files are processed
     */
    private fun processFile(source: File, destinationFolder: File) {
        val contentType = Files.probeContentType(source.toPath())
        appendToLogStream( "processing file ${source.name} of type $contentType" )

        if (!contentType.isNullOrEmpty()) {
            val config = when {
                contentType == "image/jpeg" ->
                    getDestinationConfigurationForFile(source)

                contentType == "image/png" ->
                    getDestinationConfigurationForFile(source)

                contentType.startsWith("video/", true) ->
                    getDestinationConfigurationForFile(source, "video", "")

                else -> null
            }

            if (config != null) {
                when (source.moveOrCopy(destinationFolder, destinationPattern, config, moveFiles)) {
                    MoveResults.MOVED, MoveResults.COPIED -> filesMoved++
                    MoveResults.DUPLICATE -> duplicatesFound++
                    MoveResults.INVALID -> invalidFiles++
                }
            } else {
                appendToLogStream("file ${source.name} was skipped")
                skippedFiles++
            }
        }

    }

    /**
     * retrieve all information from the source file and combine it into a DestinationPathConfiguration instance
     */
    private fun getDestinationConfigurationForFile(source: File, fixedPath : String = "", separator: String = DestinationPathConfiguration.DEFAULT_SEPARATOR) : DestinationPathConfiguration {
        val metaData = ImageMetadataReader.readMetadata(source) as Metadata

        val (dateTakenYear, dateTakenMonth) = getDateTaken(metaData)

        return DestinationPathConfiguration(
                dateTakenYear,
                dateTakenMonth,
                source.name,
                getLocation(metaData),
                getCamera(metaData),
                fixedPath = fixedPath,
                separator = separator
        )
    }

    /**
     * get date taken from metadata specific for the file type
     */
    private fun getDateTaken(metaData: Metadata) : Pair<Int, Int> {

        var dateDigitized =
                when {
                    // image file
                    metaData.containsDirectoryOfType(ExifSubIFDDirectory::class.java) -> {
                        val exifSubIFDData = metaData.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)

                        exifSubIFDData.getDate(ExifSubIFDDirectory.TAG_DATETIME_DIGITIZED)
                    }
                    // move file
                    metaData.containsDirectoryOfType(Mp4Directory::class.java) -> {
                        val mp4Data = metaData.getFirstDirectoryOfType(Mp4Directory::class.java)

                        mp4Data.getDate(Mp4Directory.TAG_CREATION_TIME)
                    }
                    // something without the date we need
                    else -> {
                        null
                    }
                }

        if (dateDigitized == null) {
            if (metaData.containsDirectoryOfType(FileSystemDirectory::class.java)) {
                val fileSystemData = metaData.getFirstDirectoryOfType(FileSystemDirectory::class.java)

                dateDigitized = fileSystemData.getDate(FileSystemDirectory.TAG_FILE_MODIFIED_DATE)
            }
        }

        val localDate = LocalDate.ofInstant(dateDigitized!!.toInstant(), ZoneId.systemDefault())

        appendToLogStream("\tretrieved date taken $dateDigitized")

        // look mom; tuples
        return Pair( localDate.year, localDate.monthValue )
    }

    /**
     * get camera information from metadata
     */
    private fun getCamera(metaData: Metadata): String {
        var camera = ""

        if (metaData.containsDirectoryOfType(ExifIFD0Directory::class.java)) {
            val exifIFD0Data = metaData.getFirstDirectoryOfType(ExifIFD0Directory::class.java)

            val model = exifIFD0Data.getString(ExifIFD0Directory.TAG_MODEL)
            val make = exifIFD0Data.getString(ExifIFD0Directory.TAG_MAKE)

            if (!model.isNullOrEmpty() && !make.isNullOrEmpty())
                camera = "$make - $model"

            appendToLogStream("\tretrieved camera make and model $camera")
        }

        return camera
    }

    /**
     * get location information by resolving lat/long in metadata using Google geo API
     */
    private fun getLocation(metaData: Metadata) : String {
        var location = ""

        if (apiContext != null) {
            if (metaData.containsDirectoryOfType(GpsDirectory::class.java)) {
                val gpsData = metaData.getFirstDirectoryOfType(GpsDirectory::class.java)

                if(gpsData.geoLocation != null) {
                    location = getCachedLocation(gpsData.geoLocation)
                }

                if (location.isNotEmpty())
                    appendToLogStream("\tretrieved GPS location $location")
                else
                    appendToLogStream("\tno GPS location found")
            }
        }

        return location
    }

    companion object {
        private const val LOCATION_INVALID_OR_NOT_FOUND = "?"
        private const val GEO_CACHE_LEVEL_MULTIPLIER = 1000
    }

    /**
     * try to match the geolocation to a location already in cache
     * this method was introduced to minimize the number of calls to google APIs
     */
    private fun getCachedLocation(gpsData: GeoLocation): String {
        // multiple and round the lat and long for the geo information, removing any insignificant numbers
        val latMultiplier = (gpsData.latitude * GEO_CACHE_LEVEL_MULTIPLIER).toLong()
        val lngMultiplier = (gpsData.longitude * GEO_CACHE_LEVEL_MULTIPLIER).toLong()

        // construct cache key from geo multiplier
        val cacheKey = "$latMultiplier-$lngMultiplier"

        if (!latLngCache.containsKey(cacheKey)) {
            val geoApiRequest = GeocodingApi.reverseGeocode(apiContext,
                    LatLng(gpsData.latitude, gpsData.longitude)
            ).await()

            latLngCache[cacheKey] = getBestLocationDescriptionForGeoRequest(geoApiRequest)
        }

        progressWriter.appendLine("${gpsData.latitude}, ${gpsData.longitude}, ${latLngCache.getValue(cacheKey)}")

        return latLngCache.getValue(cacheKey)
    }

    /**
     * determine the 'best' description in the location array retrieved from the Geo API (for our purposes)
     * 1 - Neighborhood
     * 2 - Sub locality level 1
     * 3 - Locality
     * 4 - default value LOCATION_INVALID_OR_NOT_FOUND
     */
    private fun getBestLocationDescriptionForGeoRequest(geoApiRequest: Array<GeocodingResult>): String {
        var location = LOCATION_INVALID_OR_NOT_FOUND

        if (geoApiRequest.isNotEmpty()) {

            for (geoCode in geoApiRequest) {
                if (geoCode.types.contains(AddressType.NEIGHBORHOOD)) {
                    location = geoCode.formattedAddress
                    break

                } else if (geoCode.types.contains(AddressType.SUBLOCALITY_LEVEL_1)) {
                    location = geoCode.formattedAddress
                    // don't break here, because neighborhood is desired

                } else if (location.contentEquals(LOCATION_INVALID_OR_NOT_FOUND) && geoCode.types.contains(AddressType.LOCALITY)) {
                    location = geoCode.formattedAddress
                }
            }

            // unable to find one of the preferred locations, so we just get the first one
            if (location.contentEquals(LOCATION_INVALID_OR_NOT_FOUND)) {
                location = geoApiRequest[0].formattedAddress
            }

        }

        return location
    }
}