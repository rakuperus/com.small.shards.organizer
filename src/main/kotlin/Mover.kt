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
import java.util.*

const val DEFAULT_SEPARATOR = " - "

data class DestinationInformation(
        val year : Int,
        val month: Int,
        val filename: String,
        val location: String = "",
        val camera : String = "",
        val fixedpath : String = "",
        val separator : String = DEFAULT_SEPARATOR
)

class Mover (
        private val sourcePath : String,
        private val destinationPath : String,
        private val destinationPattern  : String,
        private val moveFiles: Boolean = true,
        private val googleAPIKey : String = ""
) {

    lateinit var destination  : File
    var apiContext : GeoApiContext? = null

    private fun File.dumpMetaData() {
        val metaData = ImageMetadataReader.readMetadata(this) as Metadata

        metaData.directories.forEach { dirs ->
            appendToLogStream("\n\n${dirs.name}")
            dirs.tags.forEach {
                appendToLogStream("${it.tagName} - ${it.description}")
            }
        }

    }

    private fun Metadata.dump() {
        this.directories.forEach { dirs ->
            appendToLogStream("\n\n${dirs.name}")
            dirs.tags.forEach {
                appendToLogStream("${it.tagName} - ${it.description}")
            }
        }
    }

    private fun File.moveOrCopy(di : DestinationInformation) {
        val dotLocation = di.filename.lastIndexOf('.')
        val (filename, extension) =
                if (dotLocation > 0) {
                    Pair(di.filename.substring(0, dotLocation), di.filename.substring(dotLocation))
                } else {
                    Pair(di.filename, "")
                }

        // TODO: this is not very clever, regex could do smarter things here
        val destinationFilePath = destinationPattern
                .replace("{year}", di.year.toString())
                .replace("{month}", di.month.toString().padStart(2, '0'))
                .replace("{filename}", filename)
                .replace("{extension}", extension)
                .replace("{camera}", di.camera)
                .replace("{location}", di.location)
                .replace("{fixedpath}", di.fixedpath)
                .replace("{separator}", di.separator )
                .replace( "//", "/")

        if (destination.exists()) {
            // create all missing folders, but not the files as folder
            val pathEnd = destinationFilePath.lastIndexOf('/')
            val filePath = destinationFilePath.substring(0, pathEnd)

            val fullDestinationFolder = "${destination.path}$filePath"

            val fullDestination = File(fullDestinationFolder)
            if (!fullDestination.exists()) fullDestination.mkdirs()

            if (fullDestination.exists()) {
                val finalPath = File("${destination.path}$destinationFilePath")

                if (finalPath.exists()) {
                    appendToLogStream("skipping file $finalPath, destination exists")
                } else {
                    if (moveFiles) {
                        this.renameTo(finalPath)
                    } else {
                        Files.copy(this.toPath(), finalPath.toPath())
                    }
                }
            }
        }
    }

    fun execute() {
        val source = File(sourcePath)
        if (!source.exists()) throw FileNotFoundException(source.name)

        destination = File(destinationPath)
        if (!destination.exists()) destination.mkdirs()

        if (!googleAPIKey.isNullOrEmpty())
            apiContext = GeoApiContext.Builder().apiKey(googleAPIKey).build()

        processFolder(source)
    }

    private fun processFolder(source : File) {
        appendToLogStream ( "processing folder ${source.name}" )

        // process all files in the file tree starting from the source location
        source.walk().forEach { file -> if ( file.isFile ) processFile(file) }
    }

    private fun processFile(source: File) {
        val contentType = Files.probeContentType(source.toPath())
        appendToLogStream( "processing file ${source.name} of type $contentType" )

        if (!contentType.isNullOrEmpty()) {
            val di = when {
                contentType == "image/jpeg" ->
                    processImageFile(source)
                contentType.startsWith("video/", true) ->
                    processImageFile(source, "video", "")
                else -> null
            }

            if (di != null) source.moveOrCopy(di)
        }

    }

    private fun processImageFile(source: File, fixedpath : String = "", separator: String = DEFAULT_SEPARATOR) : DestinationInformation {
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

    private fun getDateTaken(metaData: Metadata) : Pair<Int, Int> {

        var dateDigitized : Date? =
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

    private fun getLocation(metaData: Metadata) : String {
        var location = ""

        if (apiContext != null) {
            if (metaData.containsDirectoryOfType(GpsDirectory::class.java)) {
                val GPSData = metaData.getFirstDirectoryOfType(GpsDirectory::class.java)

                val geoApiRequest = GeocodingApi.reverseGeocode(apiContext,
                        LatLng(GPSData.geoLocation.latitude, GPSData.geoLocation.longitude)
                ).await()

                if (geoApiRequest.isNotEmpty())
                    location = geoApiRequest[0].formattedAddress

                appendToLogStream("\tretrieved GPS location $location")
            }
        }

        return location
    }

}