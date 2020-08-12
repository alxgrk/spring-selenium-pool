package de.alxgrk.spring_selenium_pool

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.openqa.selenium.chrome.ChromeOptions
import java.nio.file.Files

internal class ProfileSensitiveSeleniumContainerITest {

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
        }finally {
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