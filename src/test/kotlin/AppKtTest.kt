import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.lang.Exception
import java.lang.IllegalArgumentException

class AppKtTest {
    fun testAppendToLogStream() {
    }

    @Test
    fun testGetCommandLineArguments() {

        Assertions.assertThrows(Exception::class.java) {
            getCommandLineArguments(arrayOf(
                    "--help"
            ))
        }

        Assertions.assertThrows(IllegalArgumentException::class.java) {
            getCommandLineArguments(arrayOf(
                    "--source:/User/rku21913/Downloads",
                    "--movefiles:true",
                    "--pattern:{year}_{month}/{filename}"
            ))
        }

        val result3 = getCommandLineArguments(arrayOf(
                "--source:/User/rku21913/Downloads",
                "--destination:/User/rku21913/Downloads/smoke",
                "--movefiles:true",
                "--pattern:{year}_{month}/{filename}"
        ))
        Assertions.assertEquals("/User/rku21913/Downloads", result3.sourceFolder)
    }

    fun testShowUsage() {
    }
}
