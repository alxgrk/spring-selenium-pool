package de.alxgrk.spring_selenium_pool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import java.nio.file.Files
import java.nio.file.Path

class SeleniumPoolAutoConfigurationITest {

    private var context: AnnotationConfigApplicationContext? = null

    @AfterEach
    fun tearDown() {
        if (context != null) {
            context!!.close()
        }
    }

    @Test
    fun `Configuration is not enabled and getting bean throws exception`() {
        load()
        assertThrows<NoSuchBeanDefinitionException> {
            context!!.getBean(WebDriverPool::class.java)
        }
    }

    @Test
    fun `Configuration is enabled and getting bean works`() {
        load("selenium.pool.enabled=true")

        val webDriverPool = context!!.getBean(WebDriverPool::class.java)
        val config = context!!.getBean(SeleniumPoolAutoConfiguration::class.java)

        assertEquals(DEFAULT_POOL_SIZE, webDriverPool.poolSize)
        assertTrue(webDriverPool.recordingDirectory.isEmpty())

        assertNotNull(ProfileSensitiveSeleniumContainer.profilesDirectory)
        assertTrue(ProfileSensitiveSeleniumContainer.profilesDirectory!!.name.contains("chrome_profiles"))
        assertTrue(ProfileSensitiveSeleniumContainer.profilesDirectory!!.exists())
        config.destroy()
        assertFalse(ProfileSensitiveSeleniumContainer.profilesDirectory!!.exists())
    }

    @Test
    fun `Configuration is enabled and setting pool size works`() {
        val expectedPoolSize = 5
        load("selenium.pool.enabled=true", "selenium.pool.size=$expectedPoolSize")

        val webDriverPool = context!!.getBean(WebDriverPool::class.java)

        assertEquals(expectedPoolSize, webDriverPool.poolSize)
        assertTrue(webDriverPool.recordingDirectory.isEmpty())
    }

    @Test
    fun `Configuration is enabled and setting recording directory works`() {
        val recordingDirectory = "test"
        load("selenium.pool.enabled=true", "selenium.pool.recordingDirectory=$recordingDirectory")

        val webDriverPool = context!!.getBean(WebDriverPool::class.java)

        assertEquals(DEFAULT_POOL_SIZE, webDriverPool.poolSize)
        assertEquals(recordingDirectory, webDriverPool.recordingDirectory)
    }

    @Test
    fun `Configuration is enabled and setting profiles directory works`() {
        var profilesDirectory: Path? = null
        try {
            profilesDirectory = Files.createTempDirectory("chrome_profiles")
            load("selenium.pool.enabled=true", "selenium.pool.profilesDirectory=$profilesDirectory")

            assertEquals(ProfileSensitiveSeleniumContainer.profilesDirectory?.absolutePath, profilesDirectory.toString())
        } finally {
            profilesDirectory?.toFile()?.deleteRecursively()
        }
    }

    private fun load(vararg environment: String) {
        context = AnnotationConfigApplicationContext().also {
            TestPropertyValues.of(*environment).applyTo(it)
            it.register(SeleniumPoolAutoConfiguration::class.java)
            it.refresh()
        }
    }
}