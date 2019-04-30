import java.lang.Exception
import java.lang.IllegalArgumentException

class AppKtTest {
/*
    @Test
    fun testGetCommandLineArguments() {

        // should throw exception
        Assertions.assertThrows(Exception::class.java) {
            getCommandLineArguments(arrayOf(
                    "--help"
            ))
        }

        // should throw exception for missing argument
        Assertions.assertThrows(IllegalArgumentException::class.java) {
            getCommandLineArguments(arrayOf(
                    "--source:/User/rku21913/Downloads",
                    "--movefiles:true",
                    "--pattern:{year}_{month}/{filename}"
            ))
        }

        // check if one of the argument is copied correctly
        val result3 = getCommandLineArguments(arrayOf(
                "--source:/User/rku21913/Downloads",
                "--destination:/User/rku21913/Downloads/smoke",
                "--movefiles:true",
                "--pattern:{year}_{month}/{filename}"
        ))
        Assertions.assertEquals("/User/rku21913/Downloads", result3.sourceFolder)

        // should fail cause --debug is not a valid argument
        Assertions.assertThrows(IllegalArgumentException::class.java) {
            getCommandLineArguments(arrayOf(
                    "--source:/User/rku21913/Downloads",
                    "--movefiles:true",
                    "--pattern:{year}_{month}/{filename}",
                    "--debug:/User/rku21913/Downloads/myfile.txt"
            ))
        }
    }

    @Test
    fun testGetAllCommandLineArguments() {

        val sourceIn = "/User/rku21913/Downloads"
        val destinationIn = "/User/rku21913/Downloads"
        val moveFiles = "true"
        val pattern = "{year}{month}/F{filename}.{extension}"
        val apiKey = "UIHGSADIHIUWNEJCONoijpuaidhfiabsocnaun8391302jdfi0nc"
        val debugFile = "/User/rku21913/myfile.text"

        val result = getCommandLineArguments(arrayOf(
                "--source:$sourceIn",
                "--destination:$destinationIn",
                "--pattern:$pattern",
                "--movefiles:$moveFiles",
                "--apikey:$apiKey",
                "--debugfile:$debugFile"
        ))

        Assertions.assertEquals(sourceIn, result.sourceFolder)
        Assertions.assertEquals(destinationIn, result.destinationFolder)
        Assertions.assertEquals(pattern, result.destinationPattern)
        Assertions.assertEquals(moveFiles.toBoolean(), result.moveFiles)
        Assertions.assertEquals(apiKey, result.apiKey)
        Assertions.assertEquals(debugFile, result.debugFile)

    }
*/
}
