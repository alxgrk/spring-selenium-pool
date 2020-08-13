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

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.openqa.selenium.JavascriptExecutor

class ChromeConfigurationTest {

    @Test
    fun `Initialization of ChromeConfiguration configures ChromeOptions and copies extension files`() {
        val extensionFiles = arrayOf("dummy.js", "dummy-manifest.json")

        val uut = ChromeConfiguration(*extensionFiles)

        val args = uut.options.toJson().findArgsList()
        assertTrue(args.contains("--verbose"))
        assertTrue(args.contains("--disable-dev-shm-usage"))
        assertTrue(args.contains("--disable-gpu"))
        assertTrue(args.contains("--no-sandbox"))
        assertTrue(args.contains("--load-extension=/home/seluser/ext/"))

        extensionFiles.forEach {
            assertTrue(uut.extensionDirectory.resolve(it).toFile().exists())
        }
    }

    @Test
    fun `Initialization of custom extension can be done exactly once`() {
        val mockDriver = mockk<JavascriptExecutor>() {
            every { executeAsyncScript(any()) } returns Unit
        }

        with(ChromeExtensionUtility) {
            assertEquals(Unit, mockDriver.initializeExtension())
            assertNull(mockDriver.initializeExtension())
        }

        assertTrue(ChromeExtensionUtility.isExtensionInitialized[mockDriver]!!)
        verify(exactly = 1) {
            mockDriver.executeAsyncScript(any())
        }
    }
}

@Suppress("UNCHECKED_CAST")
private fun Map<*, *>.findArgsList(): List<String> {
    return (this["args"] as? List<String>)
        ?: values
            .mapNotNull {
                if (it is Map<*, *>)
                    it.findArgsList()
                else
                    null
            }.first()
}
