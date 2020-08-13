/*
 * Copyright © 2020 Alexander Girke (alexgirke@posteo.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.alxgrk.spring_selenium_pool

import de.alxgrk.spring_selenium_pool.ChromeExtensionUtility.DOCKER_EXTENSION_DIR
import de.alxgrk.spring_selenium_pool.ChromeExtensionUtility.copyTo
import org.apache.commons.collections4.map.LRUMap
import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.chrome.ChromeOptions
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

internal class ChromeConfiguration(vararg files: String) {

    internal val options = ChromeOptions()
        .apply {
            addArguments(
                "--verbose", "--disable-dev-shm-usage",
                "--disable-gpu", "--no-sandbox", "--load-extension=$DOCKER_EXTENSION_DIR"
            )
        }

    internal val extensionDirectory =
        Files.createTempDirectory("selenium-chrome-extension")!!.apply { toFile().deleteOnExit() }

    init {
        files.copyTo(extensionDirectory)
    }
}

/**
 * This singleton holds information on custom extension loading state.
 */
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

    internal var isExtensionInitialized = LRUMap<JavascriptExecutor, Boolean>(10)

    /**
     * This method calls your custom extension exactly once with '{extension: "init"}' as a payload
     * and returns the result of your extension background script.
     */
    fun JavascriptExecutor.initializeExtension(): Any? {
        val alreadyEnabled = isExtensionInitialized[this]
        if (alreadyEnabled == null || !alreadyEnabled) {
            isExtensionInitialized[this] = true
            return this.executeAsyncScript(INITIALIZE_EXTENSION)
        }
        return null
    }
}