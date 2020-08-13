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

import org.springframework.beans.factory.DisposableBean
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.File
import java.nio.file.Files
import kotlin.properties.Delegates

/**
 * The Spring Auto-Configuration to provide the [WebDriverPool].
 */
@Configuration
@EnableConfigurationProperties(SeleniumPoolProperties::class)
class SeleniumPoolAutoConfiguration : DisposableBean {

    private var tempProfilesDirectory: File? = null

    @Bean
    internal fun webDriverPool(properties: SeleniumPoolProperties): WebDriverPool {
        ProfileSensitiveSeleniumContainer.profilesDirectory =
                if (properties.profilesDirectory.isNotEmpty()) {
                    File(properties.profilesDirectory).apply { mkdirs() }
                } else {
                    Files.createTempDirectory("chrome_profiles").toFile()
                            .also { tempProfilesDirectory = it }
                }

        val extensionFiles = properties.extensionFilesInClasspath.split(",")
                .filter { it.isNotBlank() }
                .toTypedArray()

        return WebDriverPool(properties.size, properties.recordingDirectory, *extensionFiles)
    }

    override fun destroy() {
        tempProfilesDirectory?.deleteRecursively()
    }
}

internal const val DEFAULT_POOL_SIZE = 3

@ConfigurationProperties(prefix = "selenium.pool")
internal class SeleniumPoolProperties {
    var size by Delegates.notNull<Int>()
    var extensionFilesInClasspath by Delegates.notNull<String>()
    var recordingDirectory by Delegates.notNull<String>()
    var profilesDirectory by Delegates.notNull<String>()

    init {
        size = DEFAULT_POOL_SIZE
        extensionFilesInClasspath = ""
        recordingDirectory = ""
        profilesDirectory = ""
    }
}
