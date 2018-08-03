const val DEFAULT_DESTINATION_PATTERN = "/{year}/{month}/{fixedpath}/{filename}{separator}{location}{extension}"

data class Arguments (
        val sourceFolder : String,
        val destinationFolder: String,
        val destinationPattern : String,
        val moveFiles: Boolean,
        val apiKey: String
)

fun appendToLogStream(message : String) {
    println(message)
}

fun getCommandLineArguments(args: Array<String>) : Arguments {

    var source  = ""
    var destination  = ""
    var pattern = DEFAULT_DESTINATION_PATTERN
    var move = false
    var apiKey = ""

    args.forEach {
        when {
            it.startsWith("--source:", ignoreCase = true) -> {
                source = it.substringAfter(":", "" )
            }
            it.startsWith( "--destination", ignoreCase = true) -> {
                destination = it.substringAfter(":", "" )
            }
            it.startsWith( "--movefiles", ignoreCase = true) -> {
                val moveString = it.substringAfter(":", "")
                move = moveString.toBoolean()
            }
            it.startsWith( "--pattern", ignoreCase = true) -> {
                pattern = it.substringAfterLast(":", DEFAULT_DESTINATION_PATTERN)
            }
            it.startsWith( "--apikey", ignoreCase = true) -> {
                apiKey = it.substringAfterLast(":", "")
            }
            else -> {
                appendToLogStream("invalid argument $it found")
            }
        }
    }

    // validate arguments
    if (source.isEmpty()) throw IllegalArgumentException("missing source argument")
    if (destination.isEmpty()) throw IllegalArgumentException("missing destination argument")

    return Arguments(source, destination, pattern, move, apiKey)

}

fun showUsage(error: String) {

    if (error.isNotEmpty()) {
        println(error)
    }

    println("\nUsage: organizer --source:<Path> --destination:<Path> --movefiles:true|FALSE [--pattern:<patter>]\n" +
            "valid patterns keywords are :\n" +
            "{year}\t\t -> date taken year\n" +
            "{month}\t\t -> date taken month\n" +
            "{camera}\t -> make and model of the camera\n" +
            "{filename}\t -> filename without extension\n" +
            "{extension}\t -> file extension\n" +
            "{location}\t -> geo location\n" +
            "{fixedpath}\t -> fixed path based on processed file type\n" +
            "{separator}\t -> generic separator\n")
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
    }

    val mover = Mover(validatedArgs.sourceFolder, validatedArgs.destinationFolder, validatedArgs.destinationPattern, validatedArgs.moveFiles, validatedArgs.apiKey)
    mover.execute()
}