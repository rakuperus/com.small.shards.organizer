/**
 * Simple data transportation class used in the destinationFolder pattern matching
 */
data class DestinationPathConfiguration(
        val year : Int,
        val month: Int,
        val filename: String,
        val location: String = "",
        val camera : String = "",
        val fixedPath : String = "",
        val separator : String = DEFAULT_SEPARATOR) {

    companion object {
        const val DEFAULT_SEPARATOR = " - "
    }

}

