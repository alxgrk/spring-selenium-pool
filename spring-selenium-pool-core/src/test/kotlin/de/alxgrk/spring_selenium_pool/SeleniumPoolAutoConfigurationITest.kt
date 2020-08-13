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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
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
    fun `No configuration and getting bean works`() {
        load()

        val webDriverPool = context!!.getBean(WebDriverPool::class.java)
        val config = context!!.getBean(SeleniumPoolAutoConfiguration::class.java)

        assertEquals(DEFAULT_POOL_SIZE, webDriverPool.poolSize)
        assertTrue(webDriverPool.recordingDirectory.isEmpty())
        assertTrue(webDriverPool.extensionFilesInClasspath.isEmpty())

        assertNotNull(ProfileSensitiveSeleniumContainer.profilesDirectory)
        assertTrue(ProfileSensitiveSeleniumContainer.profilesDirectory!!.name.contains("chrome_profiles"))
        assertTrue(ProfileSensitiveSeleniumContainer.profilesDirectory!!.exists())
        config.destroy()
        assertFalse(ProfileSensitiveSeleniumContainer.profilesDirectory!!.exists())
    }

    @Test
    fun `Setting pool size works`() {
        val expectedPoolSize = 2
        load("selenium.pool.size=$expectedPoolSize")

        val webDriverPool = context!!.getBean(WebDriverPool::class.java)

        assertEquals(expectedPoolSize, webDriverPool.poolSize)
        assertTrue(webDriverPool.recordingDirectory.isEmpty())
        assertTrue(webDriverPool.extensionFilesInClasspath.isEmpty())
    }

    @Test
    fun `Setting recording directory works`() {
        val recordingDirectory = "test"
        load("selenium.pool.recordingDirectory=$recordingDirectory")

        val webDriverPool = context!!.getBean(WebDriverPool::class.java)

        assertEquals(DEFAULT_POOL_SIZE, webDriverPool.poolSize)
        assertEquals(recordingDirectory, webDriverPool.recordingDirectory)
        assertTrue(webDriverPool.extensionFilesInClasspath.isEmpty())
    }

    @Test
    fun `Setting profiles directory works`() {
        var profilesDirectory: Path? = null
        try {
            profilesDirectory = Files.createTempDirectory("chrome_profiles")
            load("selenium.pool.profilesDirectory=$profilesDirectory")

            assertEquals(
                ProfileSensitiveSeleniumContainer.profilesDirectory?.absolutePath,
                profilesDirectory.toString()
            )
        } finally {
            profilesDirectory?.toFile()?.deleteRecursively()
        }
    }

    @Test
    fun `Setting extension files works`() {
        load("selenium.pool.extensionFilesInClasspath=dummy.js,dummy-manifest.json")

        val webDriverPool = context!!.getBean(WebDriverPool::class.java)

        assertEquals(DEFAULT_POOL_SIZE, webDriverPool.poolSize)
        assertTrue(webDriverPool.recordingDirectory.isEmpty())
        assertTrue(webDriverPool.extensionFilesInClasspath.all {
            listOf("dummy.js", "dummy-manifest.json").contains(it)
        })
    }

    private fun load(vararg environment: String) {
        context = AnnotationConfigApplicationContext().also {
            TestPropertyValues.of(*environment).applyTo(it)
            it.register(SeleniumPoolAutoConfiguration::class.java)
            it.refresh()
        }
    }
}