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
import de.alxgrk.spring_selenium_pool.ContainerId.Fresh
import de.alxgrk.spring_selenium_pool.ContainerId.Reuse
import org.openqa.selenium.WebDriver
import org.openqa.selenium.remote.LocalFileDetector
import org.openqa.selenium.remote.RemoteWebDriver
import org.slf4j.LoggerFactory
import org.testcontainers.containers.BrowserWebDriverContainer.VncRecordingMode
import org.testcontainers.lifecycle.TestDescription
import java.io.File
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * This class creates a pool of [poolSize] Selenium containers on initialization.
 * It furthermore takes care of supplying [RemoteWebDriver]s for the pool's containers
 * and manages their recycling, once the client is no longer needing a [RemoteWebDriver].
 */
class WebDriverPool(
    internal val poolSize: Int,
    internal val recordingDirectory: String,
    internal vararg val extensionFilesInClasspath: String
) : AutoCloseable {

    private val shouldSafeRecordings = recordingDirectory.isNotEmpty()

    private val containerFactory: () -> ProfileSensitiveSeleniumContainer = {
        ProfileSensitiveSeleniumContainer()
                .also { container ->
                    ChromeConfiguration(*extensionFilesInClasspath).apply {
                        container.withCapabilities(options)

                        // bind extension directory to docker container
                        val extensionBind = Bind(extensionDirectory.toString(),
                                Volume(ChromeExtensionUtility.DOCKER_EXTENSION_DIR))
                        container.binds.plusAssign(extensionBind)
                    }

                    if (shouldSafeRecordings)
                        container.withRecordingMode(VncRecordingMode.RECORD_ALL, File(recordingDirectory))
                    else
                        container.withRecordingMode(VncRecordingMode.SKIP, File(""))
                }
    }

    @get:Synchronized
    @set:Synchronized
    private var runningContainers: Set<ProfileSensitiveSeleniumContainer> = setOf()

    init {
        initialize()
    }

    @VisibleForTesting
    internal fun initialize() {
        runningContainers = (1..poolSize).map { containerFactory() }.toSet()
        runningContainers.map { container -> CompletableFuture.runAsync { container.start() } }
                .forEach { it.join() }
    }

    @get:VisibleForTesting
    internal val clientQueue = LinkedBlockingQueue<Pair<CompletableFuture<WebDriverForContainer>, ChromeProfile>>()

    /**
     * Returns a [WebDriver] wrapped into [WebDriverForContainer],
     * which can be used to interact with an either new or already used Selenium instance.
     *
     * If you specify a profile other than [ChromeProfile.EMPTY] while [containerId] equals [Fresh],
     * this method returns null, if a container in use is already associated with that profile.
     *
     * @param profile the identifying Chrome profile to use in this container
     * @param containerId default is [Fresh], but a [ContainerId] of type [Reuse]
     *              can be submitted to get the same instance as before
     * @return a wrapped [WebDriver] or null, if there is currently no free Selenium instance
     */
    fun getWebDriverForContainer(containerId: ContainerId = Fresh, profile: ChromeProfile = ChromeProfile.EMPTY): WebDriverForContainer? = synchronized(runningContainers) {
        val container = when (containerId) {
            is Fresh -> {
                if (profile != ChromeProfile.EMPTY && runningContainers.any { profile.inUseBy(it) })
                    return null

                runningContainers.firstOrNull { it.isFreeToUse() }
                        ?: return null
            }
            is Reuse -> {
                runningContainers.firstOrNull { it.id == containerId }
                        ?: return getWebDriverForContainer(Fresh, profile)
            }
        }
        WebDriverForContainer(profile, this, container)
    }

    private fun ChromeProfile.inUseBy(it: ProfileSensitiveSeleniumContainer) =
            it.inUse.get() && it.isRunning && it.profile == this

    private fun ProfileSensitiveSeleniumContainer.isFreeToUse() = !inUse.get() && isRunning

    /**
     * Returns a [CompletableFuture] of a [WebDriver] wrapped into [WebDriverForContainer],
     * which can be used to interact with an either new or already used Selenium instance.
     *
     * @param profile the identifying Chrome profile to use in this container; default to empty string, so no special profile
     * @param containerId default is [Fresh], but a [ContainerId] of type [Reuse]
     *              can be submitted to get the same instance as before - this only works if such instance is immediately available
     * @return a [CompletableFuture] of a wrapped [WebDriver]
     */
    fun getWebDriverForContainerAsync(containerId: ContainerId = Fresh, profile: ChromeProfile = ChromeProfile.EMPTY): CompletableFuture<WebDriverForContainer> =
            CompletableFuture<WebDriverForContainer>().also { future ->

                clientQueue.put(future to profile)

                future.exceptionally { t ->
                    clientQueue.removeIf { it.first == future }

                    throw t
                }

                // instantly complete if there is a free container
                getWebDriverForContainer(containerId, profile)?.let(future::complete)
            }

    internal fun releaseContainer(container: ProfileSensitiveSeleniumContainer) {

        safeRecordings(container)

        val newContainer = removeAndReplaceContainer(container)

        notifyWaitingClients(newContainer)
    }

    private fun notifyWaitingClients(newContainer: ProfileSensitiveSeleniumContainer) {
        try {
            fun takeOrNext(): Pair<CompletableFuture<WebDriverForContainer>, ChromeProfile>? {
                if (clientQueue.size > 0) {
                    if (clientQueue.peek().first.isDone) {
                        clientQueue.remove()
                        return takeOrNext()
                    }
                    return clientQueue.take()
                }
                return null
            }

            takeOrNext()?.let { next ->
                val webDriver = getWebDriverForContainer(newContainer.id, next.second)
                if (webDriver != null)
                    next.first.complete(webDriver)
                else
                    next.first.completeExceptionally(
                            RuntimeException("at this point, there should definitely be a free WebDriver"))
            }
        } catch (e: InterruptedException) {
            // swallow exception
            log.error("Although there seem to be clients waiting for a WebDriver, a new one couldn't be provided.")
        }
    }

    private fun removeAndReplaceContainer(container: ProfileSensitiveSeleniumContainer): ProfileSensitiveSeleniumContainer {
        var newContainer: ProfileSensitiveSeleniumContainer? = null
        runningContainers = runningContainers
                .map {
                    if (it == container) {
                        container.stop()
                        containerFactory().apply {
                            start()
                            newContainer = this
                        }
                    } else it
                }
                .toSet()
        return newContainer ?: throw RuntimeException("Couldn't replace container with ID ${container.id}")
    }

    private fun safeRecordings(container: ProfileSensitiveSeleniumContainer) {
        if (shouldSafeRecordings) {
            container.afterTest(object : TestDescription {
                override fun getFilesystemFriendlyName() = "selenium"
                override fun getTestId() = "irrelevant"
            }, Optional.empty())
        }
    }

    override fun close() {
        runningContainers.forEach {
            safeRecordings(it)
            it.stop()
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(WebDriverPool::class.java)!!
    }
}

/**
 * This class encapsulates relevant information about the Docker container and its related [WebDriver].
 * It is also dependent on whatever [ChromeProfile] you specify.
 */
class WebDriverForContainer internal constructor(
    profile: ChromeProfile,
    private val webDriverPool: WebDriverPool,
    private val container: ProfileSensitiveSeleniumContainer
) : AutoCloseable {

    /**
     * The ID of the Docker container.
     */
    val containerId = container.id

    /**
     * The [WebDriver] used to interact with Selenium.
     */
    // at this point, web driver must exist
    val webDriver: WebDriver = container.getWebDriver(profile).also {
        it.fileDetector = LocalFileDetector()
        it.manage().timeouts().implicitlyWait(20, TimeUnit.SECONDS).pageLoadTimeout(20, TimeUnit.SECONDS)
    }

    /**
     * The IP address of the container.
     */
    val host: String = container.containerIpAddress!!

    /**
     * The external Selenium port of the container.
     */
    val exposedSeleniumPort: Int = container.getMappedPort(SELENIUM_PORT)!!

    /**
     * The external VNC port of the container.
     */
    val exposedVncPort: Int = container.getMappedPort(VNC_PORT)!!

    init {
        container.inUse.set(true)
    }

    /**
     * Only to be used in test (for performance reasons).
     * Giving back a used container and acting as if it was fresh,
     * shouldn't be allowed in production.
     */
    @VisibleForTesting
    internal fun giveBack() {
        container.inUse.set(false)
    }

    @VisibleForTesting
    internal fun recreateDriver() {
        container.recreateDriver()
    }

    override fun close() {
        webDriverPool.releaseContainer(container)
    }

    companion object {
        const val SELENIUM_PORT = 4444
        const val VNC_PORT = 5900
    }
}

/**
 * A separate ID for the container: either it references an already used container
 * or it is used to be provided by a new container.
 */
sealed class ContainerId {

    /**
     * Gets you an already used container.
     */
    data class Reuse(val id: String) : ContainerId()

    /**
     * Gets you a new container.
     */
    object Fresh : ContainerId()

    companion object {
        /**
         * Returns a [Reuse] id with a random value.
         */
        fun random() = Reuse(UUID.randomUUID().toString())
    }
}