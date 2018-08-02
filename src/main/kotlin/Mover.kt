import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.Metadata
import java.io.File
import java.nio.file.Files

data class DestinationInformation(
        val year : Int,
        val month: Int,
        val filename: String,
        val location: String = "",
        val camera : String = ""
)

class Mover (
        val sourceFolder : String,
        val destinationFolder : String,
        val destinationPattern  : String,
        val moveFiles: Boolean = true
) {

    lateinit var destination  : File

    private fun File.moveOrCopy(di : DestinationInformation) {
        appendToLogStream("moveing file ${this.name}")

        val destinationFile = destinationPattern
                .replace("{year}", di.year.toString())
                .replace("{month}", di.month.toString())
                .replace("{filename}", di.filename)
                .replace("{camera}", di.camera)
                .replace("{location}", di.location)
                .replace("{separator}", "" )
                .replace( "//", "/")

        if (destination.exists()) {
            val pathEnd = destinationFile.lastIndexOf('/')
            val filePath = destinationFile.substring(0, pathEnd)

            val fullDestinationFolder = "${destination.path}$filePath"

            val fullDestination = File(fullDestinationFolder)
            if (!fullDestination.exists()) fullDestination.mkdirs()

            if (fullDestination.exists()) {
                val finalPath = File("${destination.path}$destinationFile")

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
        val source = File(sourceFolder)
        destination = File(destinationFolder)

        if (!destination.exists()) destination.mkdirs()

        processFolder(source)
    }

    private fun processFolder(source : File) {
        appendToLogStream ( "processing folder ${source.name}" )

        source.walk().forEach { if ( it.isFile ) processFile(it) }
    }

    private fun processFile(source: File) {
        appendToLogStream( "processing file ${source.name} of type ${Files.probeContentType(source.toPath())}" )

        val contentType = Files.probeContentType(source.toPath())
        if (!contentType.isNullOrEmpty()) {
            val di = when {
                contentType == "image/jpeg" -> processImageFile(source)
                contentType.startsWith("video/", true) -> processVideoFile(source)
                else -> null
            }

            if (di != null) source.moveOrCopy(di)
        }

    }

    private fun processImageFile(source: File) : DestinationInformation {
        appendToLogStream( "processing as image file" )

        dumpMetadata(source)

        return DestinationInformation(1900, 12, "file.id")
    }

    fun dumpMetadata(source: File) {
        val metaData = ImageMetadataReader.readMetadata(source) as Metadata

        metaData.directories.forEach {
            appendToLogStream("\n\n${it.name}")
            it.tags.forEach {
                appendToLogStream("${it.tagName} - ${it.description}")
            }
        }
    }

    private fun processVideoFile(source: File) : DestinationInformation {
        appendToLogStream( "processing as video file" )

        dumpMetadata(source)

        return DestinationInformation(1800, 12, "fileid.diz")
    }

}