
/**
 * validated argument data class
 */
data class Arguments (
        val sourceFolder : String,
        val destinationFolder: String,
        val destinationPattern : String,
        val moveFiles: Boolean,
        val apiKey: String,
        val debugFile : String
)

/**
 * Simple logging method, might be more interesting in the future
 */
fun appendToLogStream(message : String) {
    println(message)
}

const val DEFAULT_DESTINATION_PATTERN = "/{year}/{month}-{year}/{fixedpath}/{filename}{separator}{location}{extension}"

/**
 * retrieve the command line arguments and turn them into a validated set of options
 */
fun getCommandLineArguments(args: Array<String>) : Arguments {

    var source  = ""
    var destination  = ""
    var pattern = DEFAULT_DESTINATION_PATTERN
    var move = false
    var apiKey = ""
    var debugFile = ""

    args.forEach { argument ->
        when {
            argument.startsWith("--help", ignoreCase = true) -> {
                // TODO: throwing a generic exception might be a bit easy
                throw Exception("help required")
            }
            argument.startsWith("--source:", ignoreCase = true) -> {
                source = argument.substringAfter(":", "" )
            }
            argument.startsWith( "--destination", ignoreCase = true) -> {
                destination = argument.substringAfter(":", "" )
            }
            argument.startsWith( "--movefiles", ignoreCase = true) -> {
                val moveString = argument.substringAfter(":", "")
                move = moveString.toBoolean()
            }
            argument.startsWith( "--pattern", ignoreCase = true) -> {
                pattern = argument.substringAfterLast(":", DEFAULT_DESTINATION_PATTERN)
            }
            argument.startsWith( "--apikey", ignoreCase = true) -> {
                apiKey = argument.substringAfterLast(":", "")
            }
            argument.startsWith("--debugfile", ignoreCase = true) -> {
                // this is a debugging only argument, and presented in the usage output
                debugFile = argument.substringAfterLast(":", "")
            }
            else -> {
                // source is the default argument
                source = argument
            }
        }
    }

    // validate arguments
    if (source.isEmpty()) throw IllegalArgumentException("missing source argument")
    if (destination.isEmpty()) throw IllegalArgumentException("missing destination argument")

    return Arguments(source, destination, pattern, move, apiKey, debugFile)
}

/**
 * print out the way this app is used
 */
fun showUsage(error: String = "") {

    if (error.isNotEmpty()) {
        println(error)
    }

    println("\nUsage: organizer [--source:]<Path> --destination:<Path> --movefiles:true|FALSE [--apikey:<google maps api key>] [--pattern:<patter>] [--help]\n" +
            "\ndefault pattern is:" +
            "\n\t$DEFAULT_DESTINATION_PATTERN\n" +
            "\nvalid patterns keywords are :\n" +
            "\t{year}\t\t -> date taken year\n" +
            "\t{month}\t\t -> date taken month\n" +
            "\t{camera}\t -> make and model of the camera\n" +
            "\t{filename}\t -> filename without extension\n" +
            "\t{extension}\t -> file extension\n" +
            "\t{location}\t -> geo location\n" +
            "\t{fixedpath}\t -> fixed path based on processed file type (ie video for video files)\n" +
            "\t{separator}\t -> generic separator\n")
}

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        showUsage("missing arguments")
        return
    }

    val validatedArgs = try {
        getCommandLineArguments(args)

    } catch (e: IllegalArgumentException) {
        showUsage(e.message.toString())
        return

    } catch (e: Exception) {
        showUsage()
        return

    }

    val progressWriter  = ProgressWriter(validatedArgs.debugFile)

    try {
        Mover(
                validatedArgs.sourceFolder,
                validatedArgs.destinationFolder,
                validatedArgs.destinationPattern,
                validatedArgs.moveFiles,
                validatedArgs.apiKey,
                progressWriter
        ).execute()
    } catch (e: java.lang.IllegalArgumentException) {
        showUsage(e.message.toString())
    }

    return
}