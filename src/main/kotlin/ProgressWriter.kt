import java.io.File

class ProgressWriter(pathName :String) {

    private val progressFile = if (pathName.isNotEmpty()) File (pathName) else null

    fun appendLine(message: String) {
        progressFile?.printWriter()?.println(message)
    }
}