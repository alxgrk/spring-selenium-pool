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

import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.Volume
import com.google.common.annotations.VisibleForTesting
import org.openqa.selenium.Capabilities
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.remote.RemoteWebDriver
import org.rnorth.ducttape.timeouts.Timeouts
import org.rnorth.ducttape.unreliables.Unreliables
import org.slf4j.LoggerFactory
import org.testcontainers.containers.BrowserWebDriverContainer
import java.io.File
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * This class server as a wrapper around Testcontainer's class. By this:
 *
 *     - the workaround for https://github.com/testcontainers/testcontainers-java/issues/318 is included
 *     - it is possible to control the [RemoteWebDriver]'s creation, which is necessary to restart Chrome with different profiles
 */
internal class ProfileSensitiveSeleniumContainer(
    internal val id: ContainerId = ContainerId.random(),
    internal val inUse: AtomicBoolean = AtomicBoolean(false)
) : BrowserWebDriverContainer<ProfileSensitiveSeleniumContainer>() {

    internal var profile: ChromeProfile? = null
        private set
    private var driverByProfile: RemoteWebDriver? = null
    private var capabilities: ChromeOptions? = null

    init {
        if (profilesDirectory != null) {
            // bind profiles directory to docker container
            val profilesBind = Bind(profilesDirectory!!.absolutePath,
                    Volume(DOCKER_PROFILES_DIR))
            binds.plusAssign(profilesBind)
        }
    }

    /**
     * This method returns a [RemoteWebDriver].
     * Depending on the value of [chromeProfile], this might be either
     * the original [getWebDriver]'s result or a new [RemoteWebDriver]
     * started with the specified profile.
     * Note, that if once retrieved a [RemoteWebDriver] with a special profile,
     * subsequent calls with an empty string as an argument will
     * return the already used [RemoteWebDriver].
     *
     * @param chromeProfile the profile to start the [RemoteWebDriver] with
     *                (typically, a social media platform username)
     */
    internal fun getWebDriver(chromeProfile: ChromeProfile): RemoteWebDriver {
        require(capabilities != null) {
            "You need to define ChromeOptions via withCapabilities first."
        }

        // early exit if there is no special profile requested
        if (chromeProfile == ChromeProfile.EMPTY) {
            if (webDriver != null && !webDriver!!.stopped())
                return webDriver!!
            if (driverByProfile != null && !driverByProfile!!.stopped())
                return driverByProfile!!
            throw RuntimeException("No RemoteWebDriver available")
        }

        // quit the original web driver
        quit(webDriver)

        // only recreate driverByProfile if another profile is requested
        if (chromeProfile != this.profile) {
            var profileDirectoryOrNull = profileDirectories.getOrDefault(chromeProfile, null)

            if (profilesDirectory != null) {
                if (profileDirectoryOrNull == null) {
                    File(profilesDirectory, chromeProfile.id).apply { mkdir() }
                    profileDirectoryOrNull = Paths.get(DOCKER_PROFILES_DIR).resolve(chromeProfile.id).toFile()
                    profileDirectories += (chromeProfile to profileDirectoryOrNull)
                }

                capabilities!!.addArguments("--user-data-dir=${profileDirectoryOrNull!!.absolutePath}")
                this.profile = chromeProfile
            } else
                LOGGER.warn("No directory for saving Chrome profiles to was specified, so using default profile.")

            return recreateDriver()
        } else {
            require(driverByProfile != null && !driverByProfile!!.stopped()) {
                "If the profile already matches, the driver should exist."
            }
            return driverByProfile!!
        }
    }

    override fun getWebDriver(): RemoteWebDriver? = super.getWebDriver()
            ?.also {
                if (it.stopped())
                    return null // the RemoteWebDriver has already stopped, so don't use it
            }

    override fun withCapabilities(capabilities: Capabilities): ProfileSensitiveSeleniumContainer {
        require(capabilities is ChromeOptions) {
            "You need to supply ChromeOptions here."
        }

        this.capabilities = capabilities
        return super.withCapabilities(capabilities)
    }

    override fun stop() {
        quit(driverByProfile)
        super.stop()
    }

    @VisibleForTesting
    internal fun recreateDriver(): RemoteWebDriver {
        quit(driverByProfile)
        this.driverByProfile = create()
        return this.driverByProfile!!
    }

    private fun create(): RemoteWebDriver =
            Unreliables.retryUntilSuccess(10, TimeUnit.SECONDS,
                    Timeouts.getWithTimeout(5, TimeUnit.SECONDS) {
                        {
                            RemoteWebDriver(seleniumAddress, capabilities)
                        }
                    })

    private fun quit(webDriver: RemoteWebDriver?) {
        if (webDriver != null && !webDriver.stopped()) {
            try {
                webDriver.quit()
            } catch (e: Exception) {
                LOGGER.debug("Failed to quit the driver", e)
            }
        }
    }

    private fun RemoteWebDriver.stopped() = sessionId == null

    companion object {
        private val LOGGER = LoggerFactory.getLogger(ProfileSensitiveSeleniumContainer::class.java)

        private const val DOCKER_PROFILES_DIR = "/home/seluser/profiles"

        // this value is set in SeleniumPool (injected by Spring)
        internal var profilesDirectory: File? = null

        @get:Synchronized
        private var profileDirectories: Map<ChromeProfile, File> = updateProfilesMap()

        private fun updateProfilesMap(): Map<ChromeProfile, File> =
                (profilesDirectory?.listFiles { file -> file.isDirectory } ?: emptyArray<File>())
                        .groupBy { file -> ChromeProfile(file.name) }
                        .mapValues { entry -> entry.value.first() }
    }
}

/**
 * A holder for the profile that should be used with Chrome.
 */
data class ChromeProfile(val id: String) {
    companion object {
        val EMPTY = ChromeProfile("")
    }
}