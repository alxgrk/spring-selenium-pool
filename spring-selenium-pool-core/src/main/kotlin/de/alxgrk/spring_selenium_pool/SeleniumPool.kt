package de.alxgrk.spring_selenium_pool

import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.File
import java.nio.file.Files
import kotlin.properties.Delegates

@Configuration
@EnableConfigurationProperties(SeleniumPoolProperties::class)
class SeleniumPoolAutoConfiguration : DisposableBean {

    private var tempProfilesDirectory: File? = null

    @Bean
    fun webDriverPool(properties: SeleniumPoolProperties): WebDriverPool {
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

const val DEFAULT_POOL_SIZE = 3

@ConfigurationProperties(prefix = "selenium.pool")
class SeleniumPoolProperties {
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

