import java.io.File
import java.nio.file.Files

enum class MoveResults {
    INVALID,        // file not found or unable to perform operation
    MOVED,          // file was moved
    COPIED,         // file was copied
    DUPLICATE       // no action take on duplicate destination
}

/**
 * File Extension
 * - move or copy the file based on the information in the Destination Information
 */
fun File.moveOrCopy(destinationFolder: File, destinationPattern: String, pathConfiguration : DestinationPathConfiguration, moveFiles: Boolean = false) : MoveResults {

    /**
     * Create the folder tree from required and return the actual destination folder
     */
    fun createPatternHierarchyInDestination(destinationFilePath: String): File {
        val fileSeparator = destinationFilePath.lastIndexOf('/')
        val missingFolderPath = destinationFilePath.substring(0, fileSeparator)

        val fullDestinationFolder = "${destinationFolder.path}$missingFolderPath"

        val missingFolder = File(fullDestinationFolder)
        if (!missingFolder.exists()) missingFolder.mkdirs()

        return missingFolder
    }

    /**
     * Use the pattern passed to this command to create a path by replacing keywords with actual values
     */
    fun getDestinationPathFromConfiguration(destinationPattern: String, config: DestinationPathConfiguration): String {
        // separate filename from extension
        val extensionSeparator = config.filename.lastIndexOf('.')
        val (filename, extension) =
                if (extensionSeparator > 0) {
                    Pair(config.filename.substring(0, extensionSeparator), config.filename.substring(extensionSeparator))
                } else {
                    Pair(config.filename, "")
                }

        // TODO: this is not very clever, regex could do smarter things here
        var destinationFilePath = destinationPattern
                .replace("{year}", config.year.toString())
                .replace("{month}", config.month.toString().padStart(2, '0'))
                .replace("{filename}", filename)
                .replace("{extension}", extension)
                .replace("{camera}", config.camera)
                .replace("{fixedPath}", config.fixedPath)

        destinationFilePath =
                if (!config.location.isEmpty()) {
                    destinationFilePath
                            .replace("{location}", config.location)
                            .replace("{separator}", config.separator)
                } else {
                    destinationFilePath
                            .replace("{location}", "")
                            .replace("{separator}", "")
                }

        destinationFilePath = destinationFilePath
                .replace("//", "/")

        return destinationFilePath
    }

    if (!destinationFolder.exists()) {
        throw IllegalArgumentException("can't move to destination folder because it does not exist")
    }

    val destinationFilePath = getDestinationPathFromConfiguration(destinationPattern, pathConfiguration)
    val missingFolder = createPatternHierarchyInDestination(destinationFilePath)

    return if (missingFolder.exists()) {
        val newFilePath = File("${destinationFolder.path}$destinationFilePath")

        if (newFilePath.exists()) {
            appendToLogStream("\tdestination already exists, file $newFilePath skipped")

            MoveResults.DUPLICATE
        } else {
            if (moveFiles) {
                this.renameTo(newFilePath)
                appendToLogStream("\tfile moved to destination")

                MoveResults.COPIED
            } else {
                Files.copy(this.toPath(), newFilePath.toPath())
                appendToLogStream("\tfile copied to destination")

                MoveResults.MOVED
            }

        }
    }
    else MoveResults.INVALID
}
