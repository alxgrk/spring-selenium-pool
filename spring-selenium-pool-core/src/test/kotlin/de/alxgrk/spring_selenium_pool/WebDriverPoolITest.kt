package de.alxgrk.spring_selenium_pool

import de.alxgrk.spring_selenium_pool.WebDriverForContainer.Companion.SELENIUM_PORT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.openqa.selenium.NoSuchSessionException
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import java.nio.file.Files

class WebDriverPoolITest {

    private val context = load("selenium.pool.enabled=true", "selenium.pool.size=1")

    @Test
    fun `Trying to run commands after close fails`() {
        val webDriverPool = context.getBean(WebDriverPool::class.java)

        val webDriverForContainer = webDriverPool.getWebDriverForContainer()
        // .use takes care of closing the AutoClosable
        webDriverForContainer!!.use {
            it.webDriver.get("localhost:$SELENIUM_PORT")
            assertTrue(it.webDriver.title.contains("Selenium"))
        }

        assertThrows<NoSuchSessionException> {
            webDriverForContainer.webDriver.get("will-fail")
        }
    }

    @Test
    fun `Getting web driver for a specific containerId returns the same web driver`() {
        val webDriverPool = context.getBean(WebDriverPool::class.java)

        var webDriverForContainer = webDriverPool.getWebDriverForContainer()
        var webDriver = webDriverForContainer!!.webDriver
        webDriver.get("localhost:$SELENIUM_PORT")
        assertTrue(webDriver.title.contains("Selenium"))

        webDriverForContainer = webDriverPool.getWebDriverForContainer(webDriverForContainer.containerId)!!
        webDriver = webDriverForContainer.webDriver
        webDriver.get("localhost:$SELENIUM_PORT")
        assertTrue(webDriver.title.contains("Selenium"))

        webDriverForContainer.close()
    }

    @Test
    fun `Getting web driver returns null if all are in use`() {
        val webDriverPool = context.getBean(WebDriverPool::class.java)

        // one (means all) is in use
        val webDriverForContainer = webDriverPool.getWebDriverForContainer()!!

        // no free web driver available
        assertNull(webDriverPool.getWebDriverForContainer())

        // give back container to pool without restarting
        webDriverForContainer.giveBack()
    }

    @Test
    fun `Getting web driver by containerId returns fresh if not existent`() {
        val webDriverPool = context.getBean(WebDriverPool::class.java)

        val unknownContainerId = ContainerId.random()
        val webDriverForContainer = webDriverPool.getWebDriverForContainer(unknownContainerId)!!

        assertNotEquals(unknownContainerId, webDriverForContainer.containerId)

        // give back container to pool without restarting
        webDriverForContainer.giveBack()
    }

    @Test
    fun `Getting web driver asynchronously`() {
        val webDriverPool = context.getBean(WebDriverPool::class.java)

        val future = webDriverPool.getWebDriverForContainerAsync()

        val webDriverForContainer = future.getNow(null)
        assertNotNull(webDriverForContainer)

        // give back container to pool without restarting
        webDriverForContainer.giveBack()
    }

    @Test
    fun `Getting web driver asynchronously requesting reuse returns the wanted container`() {
        val webDriverPool = context.getBean(WebDriverPool::class.java)

        val containerInUse = webDriverPool.getWebDriverForContainer()!!

        val future = webDriverPool.getWebDriverForContainerAsync(containerInUse.containerId)
        assertEquals(containerInUse.containerId, future.getNow(null)?.containerId)

        // give back container to pool without restarting
        containerInUse.giveBack()
    }

    @Test
    fun `Getting web driver asynchronously blocks until there is a free container`() {
        val webDriverPool = context.getBean(WebDriverPool::class.java)

        val containerInUse = webDriverPool.getWebDriverForContainer()!!

        val future = webDriverPool.getWebDriverForContainerAsync()
        assertNull(future.getNow(null))

        containerInUse.close()

        val webDriverForContainer = future.getNow(null)
        assertNotNull(webDriverForContainer)

        // give back container to pool without restarting
        webDriverForContainer.giveBack()
    }

    @Test
    fun `Getting web driver asynchronously and completing future makes it be skipped`() {
        val webDriverPool = context.getBean(WebDriverPool::class.java)

        val containerInUse = webDriverPool.getWebDriverForContainer()!!

        val futureToBeCompletedFromHere = webDriverPool.getWebDriverForContainerAsync()
        assertNull(futureToBeCompletedFromHere.getNow(null))
        assertEquals(1, webDriverPool.clientQueue.size)

        val future = webDriverPool.getWebDriverForContainerAsync()
        assertNull(future.getNow(null))
        assertEquals(2, webDriverPool.clientQueue.size)

        futureToBeCompletedFromHere.complete(null)
        containerInUse.close()

        // "futureToBeCompletedFromHere" would be first to retrieve new web driver,
        // but since its completed "future" directly gets new web driver
        val webDriverForContainer = future.getNow(null)
        assertNotNull(webDriverForContainer)
        assertEquals(0, webDriverPool.clientQueue.size)

        // give back container to pool without restarting
        webDriverForContainer.giveBack()
    }

    @Test
    fun `Getting web driver asynchronously and cancelling future removes it from queue`() {
        val webDriverPool = context.getBean(WebDriverPool::class.java)

        val containerInUse = webDriverPool.getWebDriverForContainer()!!

        val future = webDriverPool.getWebDriverForContainerAsync()
        assertNull(future.getNow(null))
        assertEquals(1, webDriverPool.clientQueue.size)

        assertTrue(future.cancel(true))

        assertEquals(0, webDriverPool.clientQueue.size)

        // give back container to pool without restarting
        containerInUse.giveBack()
    }

    @Test
    fun `Closing web driver pool stops all containers, but it can be reinitialized`() {
        val webDriverPool = context.getBean(WebDriverPool::class.java)

        webDriverPool.close()
        assertNull(webDriverPool.getWebDriverForContainer())

        webDriverPool.initialize()
        assertNotNull(webDriverPool.getWebDriverForContainer())
    }

    @Test
    fun `Enabling vnc recording using property works`() {
        val tmp = Files.createTempDirectory("recordings")
        try {
            val context = load("selenium.pool.enabled=true", "selenium.pool.size=1", "selenium.pool.recordingDirectory=${tmp.toAbsolutePath()}")
            val webDriverPool = context.getBean(WebDriverPool::class.java)

            // give the VNC container time to come up (should not be a problem in production
            Thread.sleep(2000)

            val webDriverForContainer = webDriverPool.getWebDriverForContainer()
            webDriverForContainer!!.webDriver.get("localhost:$SELENIUM_PORT")

            webDriverForContainer.close()

            val recording = tmp.toFile().listFiles()!!.first()
            assertTrue(recording.exists())
            assertTrue(recording.name.endsWith(".flv"))
            assertTrue(recording.length() > 0)
        } finally {
            tmp.toFile().deleteRecursively()
        }
    }

    @Test
    fun `It is possible to get a web driver for a specified profile`() {
        val webDriverPool = context.getBean(WebDriverPool::class.java)

        fun WebDriverPool.getLaunchCountForProfile(profile: ChromeProfile, expectedLaunchCount: String) =
                getWebDriverForContainer(profile = profile)!!.run {
                    webDriver.get("https://google.de/")
                    recreateDriver() // do this to ensure local state is written

                    val localState = ProfileSensitiveSeleniumContainer.profilesDirectory!!.toPath()
                            .resolve(profile.id).resolve("Local State").toFile()
                    val launchCount = expectedLaunchCount.toRegex().find(localState.readText())
                    (launchCount?.value ?: "")
                            .also { giveBack() }
                }

        val fooLaunchedOnce = webDriverPool.getLaunchCountForProfile(ChromeProfile("foo"), "\"launch_count\":\"1\"")
        val fooLaunchedTwice = webDriverPool.getLaunchCountForProfile(ChromeProfile("foo"), "\"launch_count\":\"2\"")
        val barLaunchedOnce = webDriverPool.getLaunchCountForProfile(ChromeProfile("bar"), "\"launch_count\":\"1\"")

        assertEquals("\"launch_count\":\"1\"", fooLaunchedOnce)
        assertEquals("\"launch_count\":\"2\"", fooLaunchedTwice)
        assertEquals("\"launch_count\":\"1\"", barLaunchedOnce)
    }

    @Test
    fun `It is not possible to get two containers for the same profile`() {
        load("selenium.pool.enabled=true", "selenium.pool.size=2").use { contextWithTwoContainers ->
            val webDriverPool = contextWithTwoContainers.getBean(WebDriverPool::class.java)

            val fooInUse = webDriverPool.getWebDriverForContainer(profile = ChromeProfile("foo"))
            assertNotNull(fooInUse)
            val barInUse = webDriverPool.getWebDriverForContainer(profile = ChromeProfile("bar"))
            assertNotNull(barInUse)
            barInUse?.giveBack() // make sure, there would be at least one free container

            val fooCouldNotBeAcquired = webDriverPool.getWebDriverForContainer(profile = ChromeProfile("foo"))
            assertNull(fooCouldNotBeAcquired)
        }
    }

    private fun load(vararg environment: String) =
            AnnotationConfigApplicationContext().also {
                TestPropertyValues.of(*environment).applyTo(it)
                it.register(SeleniumPoolAutoConfiguration::class.java)
                it.refresh()
            }

}