import java.io.File
import java.io.PrintWriter

/**
 * Simple logger - couldn't be bother with a dependency and wanted to try singleton and file in Kotlin
 * If the file is not created this logger just ignores all logging
 */
class ProgressWriter(pathName :String) {

    companion object {
        private var progressFile : File? = null
        private var writer : PrintWriter? = null
    }

    init {
        progressFile = if (pathName.isNotEmpty()) File(pathName) else null
        writer = progressFile?.printWriter()
    }

    /**
     * Append a single line of text to the file controlled by this ProgressWriter
     */
    fun appendLine(message: String) {
        writer?.appendln(message)
        writer?.flush()
    }
}