package de.alxgrk.spring_selenium_pool

import de.alxgrk.spring_selenium_pool.ChromeExtensionUtility.DOCKER_EXTENSION_DIR
import de.alxgrk.spring_selenium_pool.ChromeExtensionUtility.copyTo
import org.apache.commons.collections4.map.LRUMap
import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeOptions
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

internal class ChromeConfiguration(vararg files: String) {

    internal val options = ChromeOptions()

    internal val extensionDirectory = Files.createTempDirectory("selenium-chrome-extension")!!.apply { toFile().deleteOnExit() }

    init {
        files.copyTo(extensionDirectory)

        options.addArguments("--verbose", "--disable-dev-shm-usage",
                "--disable-gpu", "--no-sandbox", "--load-extension=$DOCKER_EXTENSION_DIR")
    }

}

object ChromeExtensionUtility {

    internal const val DOCKER_EXTENSION_DIR = "/home/seluser/ext/"

    private const val EXTENSION_ID = "nmamelejkbhljllfalieeoedmnfolplf"

    // sent message will be received by the extensions index.js
    private const val INITIALIZE_EXTENSION = """
            var doneCallback = arguments[arguments.length - 1]; // this is injected by chrome runtime
            chrome.runtime.sendMessage("$EXTENSION_ID", 
                {extension: "init"}, // request object
                {}, // sender object
                function(a) { doneCallback(a); } // sendResponse callback
            );"""

    internal fun Array<out String>.copyTo(targetDir: Path) =
            map { file ->
                ChromeExtensionUtility::class.java.classLoader.getResourceAsStream(file)!!
                        .use { fileStream ->
                            Files.copy(fileStream, targetDir.resolve(file))
                                    .let { File(targetDir.toFile(), file).apply { deleteOnExit() } }
                        }
            }

    private var isNetworkMonitoringEnabled = LRUMap<WebDriver, Boolean>(10)

    @kotlin.jvm.Synchronized
    fun WebDriver.initializeExtension(): Any? {
        val alreadyEnabled = isNetworkMonitoringEnabled[this]
        if (alreadyEnabled == null || !alreadyEnabled) {
            isNetworkMonitoringEnabled[this] = true
            return (this as JavascriptExecutor).executeAsyncScript(INITIALIZE_EXTENSION)
        }
        return null
    }
}