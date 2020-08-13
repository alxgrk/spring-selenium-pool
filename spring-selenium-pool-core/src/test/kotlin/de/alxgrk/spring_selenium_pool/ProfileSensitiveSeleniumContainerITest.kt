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

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.openqa.selenium.chrome.ChromeOptions
import java.nio.file.Files

class ProfileSensitiveSeleniumContainerITest {

    @Test
    fun `getWebDriver for empty profile returns original WebDriver`() {
        val uut = ProfileSensitiveSeleniumContainer()

        val webDriver = uut
                .withCapabilities(ChromeOptions())
                .apply { start() }
                .getWebDriver(ChromeProfile.EMPTY)
        val originalWebDriver = uut.webDriver

        assertSame(webDriver, originalWebDriver)
    }

    @Test
    fun `getWebDriver for empty profile returns driverByProfile if there was an interaction before`() {
        val uut = ProfileSensitiveSeleniumContainer()

        val webDriver = uut
                .withCapabilities(ChromeOptions())
                .apply {
                    start()
                    getWebDriver(ChromeProfile("foo"))
                }
                .getWebDriver(ChromeProfile.EMPTY)
        val originalWebDriver = uut.webDriver

        assertNotSame(webDriver, originalWebDriver)
        assertNull(originalWebDriver)
        assertNotNull(webDriver)
    }

    @Test
    fun `getWebDriver returns different driverByProfile's for the same profile if profilesDirectory was not set`() {
        val uut = ProfileSensitiveSeleniumContainer()
                .withCapabilities(ChromeOptions())
                .apply { start() }

        val webDriver = uut.getWebDriver(ChromeProfile("foo"))
        val sameWebDriver = uut.getWebDriver(ChromeProfile("foo"))

        assertNotNull(webDriver)
        assertNotSame(webDriver, sameWebDriver)
    }

    @Test
    fun `getWebDriver returns the same driverByProfile for the same profile if profilesDirectory was set`() {
        try {
            ProfileSensitiveSeleniumContainer.profilesDirectory = Files.createTempDirectory("profiles").toFile()
            val uut = ProfileSensitiveSeleniumContainer()
                    .withCapabilities(ChromeOptions())
                    .apply { start() }

            val webDriver = uut.getWebDriver(ChromeProfile("foo"))
            val sameWebDriver = uut.getWebDriver(ChromeProfile("foo"))

            assertNotNull(webDriver)
            assertSame(webDriver, sameWebDriver)
        } finally {
            ProfileSensitiveSeleniumContainer.profilesDirectory?.deleteRecursively()
            ProfileSensitiveSeleniumContainer.profilesDirectory = null
        }
    }

    @Test
    fun `ChromeOptions must be set before getting a RemoteWebDriver`() {
        assertThrows<IllegalArgumentException> {
            ProfileSensitiveSeleniumContainer()
                    .getWebDriver(ChromeProfile.EMPTY)
        }
    }
}