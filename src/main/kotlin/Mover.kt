import com.drew.imaging.ImageMetadataReader

import com.drew.metadata.Metadata
import com.drew.metadata.exif.ExifIFD0Directory
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.drew.metadata.exif.GpsDirectory
import com.drew.metadata.file.FileSystemDirectory
import com.drew.metadata.mp4.Mp4Directory

import com.google.maps.GeoApiContext
import com.google.maps.GeocodingApi
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
 * Based on the switch @moveFiles the mover determines if files should actualy be moved or just be copied
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
    companion object {
        private const val DEFAULT_SEPARATOR = " - "
    }

    private lateinit var destinationFolder  : File
    private var apiContext : GeoApiContext? = null

    private var duplicatesFound : Int = 0
    private var filesMoved : Int = 0

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

    private fun File.moveOrCopy(di : DestinationInformation) {
        if (!destinationFolder.exists()) {
            appendToLogStream("can't move to destination folder because it does not exist")
            return
        }

        val destinationFilePath = transformDestinationFromPattern(di)

        val missingFolder = createPatternFolderInDestination(destinationFilePath)
        if (missingFolder.exists()) {
            val newFilePath = File("${destinationFolder.path}$destinationFilePath")

            if (newFilePath.exists()) {
                appendToLogStream("\tdestination already exists, file $newFilePath skipped")
                duplicatesFound++
            } else {
                if (moveFiles) {
                    this.renameTo(newFilePath)
                    appendToLogStream("\tfile moved to destination")
                } else {
                    Files.copy(this.toPath(), newFilePath.toPath())
                    appendToLogStream("\tfile copied to destination")
                }

                filesMoved++
            }
        }
    }

    private fun createPatternFolderInDestination(destinationFilePath: String): File {
        val fileSeparator = destinationFilePath.lastIndexOf('/')
        val missingFolderPath = destinationFilePath.substring(0, fileSeparator)

        val fullDestinationFolder = "${destinationFolder.path}$missingFolderPath"

        val missingFolder = File(fullDestinationFolder)
        if (!missingFolder.exists()) missingFolder.mkdirs()

        return missingFolder
    }

    private fun transformDestinationFromPattern(di: DestinationInformation): String {
        val extensionSeparator = di.filename.lastIndexOf('.')
        val (filename, extension) =
                if (extensionSeparator > 0) {
                    Pair(di.filename.substring(0, extensionSeparator), di.filename.substring(extensionSeparator))
                } else {
                    Pair(di.filename, "")
                }

        // TODO: this is not very clever, regex could do smarter things here
        var destinationFilePath = destinationPattern
                .replace("{year}", di.year.toString())
                .replace("{month}", di.month.toString().padStart(2, '0'))
                .replace("{filename}", filename)
                .replace("{extension}", extension)
                .replace("{camera}", di.camera)
                .replace("{fixedpath}", di.fixedpath)

        destinationFilePath =
                if (!di.location.isEmpty()) {
                    destinationFilePath
                            .replace("{location}", di.location)
                            .replace("{separator}", di.separator)
                } else {
                    destinationFilePath
                            .replace("{location}", "")
                            .replace("{separator}", "")
                }

        destinationFilePath = destinationFilePath
                .replace("//", "/")

        return destinationFilePath
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

        destinationFolder = File(destinationPath)
        if (!destinationFolder.exists()) destinationFolder.mkdirs()

        // as described in the API documentation, the context should be used as a singleton
        if (!googleAPIKey.isEmpty())
            apiContext = GeoApiContext.Builder().apiKey(googleAPIKey).build()

        processFolder(source)

        appendToLogStream("moved or copied $filesMoved files to destintation")
        appendToLogStream("found $duplicatesFound files already existed in destinationFolder")
    }

    /**
     * process all files in the source folder
     */
    private fun processFolder(source : File) {
        appendToLogStream ( "processing folder ${source.name}" )

        // process files only, skipping all folders
        source.walk().forEach { file ->
            if ( file.isFile ) processFile(file)
        }
    }

    /**
     * process a single file. only image and video files are processed
     */
    private fun processFile(source: File) {
        val contentType = Files.probeContentType(source.toPath())
        appendToLogStream( "processing file ${source.name} of type $contentType" )

        if (!contentType.isNullOrEmpty()) {
            val di = when {
                contentType == "image/jpeg" ->
                    getDestinationInformationForFile(source)

                contentType.startsWith("video/", true) ->
                    getDestinationInformationForFile(source, "video", "")

                else -> null
            }

            if (di != null) source.moveOrCopy(di)
        }

    }

    private fun getDestinationInformationForFile(source: File, fixedpath : String = "", separator: String = DEFAULT_SEPARATOR) : DestinationInformation {
        val metaData = ImageMetadataReader.readMetadata(source) as Metadata

        val (dateTakenYear, dateTakenMonth) = getDateTaken(metaData)

        return DestinationInformation(
                dateTakenYear,
                dateTakenMonth,
                source.name,
                getLocation(metaData),
                getCamera(metaData),
                fixedpath = fixedpath,
                separator = separator
        )
    }

    /**
     * get date taken from metadata specific for the filetype
     */
    private fun getDateTaken(metaData: Metadata) : Pair<Int, Int> {

        var dateDigitized =
                when {
                    metaData.containsDirectoryOfType(ExifSubIFDDirectory::class.java) -> {
                        val exifSubIFDData = metaData.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)

                        exifSubIFDData.getDate(ExifSubIFDDirectory.TAG_DATETIME_DIGITIZED)
                    }
                    metaData.containsDirectoryOfType(Mp4Directory::class.java) -> {
                        val mp4Data = metaData.getFirstDirectoryOfType(Mp4Directory::class.java)

                        mp4Data.getDate(Mp4Directory.TAG_CREATION_TIME)
                    }
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

                if (gpsData.geoLocation != null) {
                    progressWriter.appendLine("${gpsData.geoLocation.latitude}, ${gpsData.geoLocation.longitude}")

                    val geoApiRequest = GeocodingApi.reverseGeocode(apiContext,
                            LatLng(gpsData.geoLocation.latitude, gpsData.geoLocation.longitude)
                    ).await()

                    if (geoApiRequest.isNotEmpty())
                        location = geoApiRequest[0].formattedAddress
                }

                appendToLogStream("\tretrieved GPS location $location")
            }
        }

        return location
    }

    /**
     * Simple data transporation class used in the destinationFolder pattern matching
     */
    data class DestinationInformation(
            val year : Int,
            val month: Int,
            val filename: String,
            val location: String = "",
            val camera : String = "",
            val fixedpath : String = "",
            val separator : String = DEFAULT_SEPARATOR
    )
}