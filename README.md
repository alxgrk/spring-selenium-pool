# 🏊 Selenium Pool

[![Maven Central](https://img.shields.io/maven-central/v/de.alxgrk/spring-selenium-pool-core?color=%23080&style=for-the-badge)](https://search.maven.org/search?q=g:%22de.alxgrk%22%20AND%20a:%22spring-selenium-pool-core%22)

This small library helps to create a pool of Selenium Docker Container with a configurable size.

## 🩲 Prerequisites

Make sure to have the following installed:
 * Docker

## 💿 Installation

For Maven, add this dependency:  

```xml
<dependency>
  <groupId>de.alxgrk</groupId>
  <artifactId>spring-selenium-pool-core</artifactId>
  <version>1.0.0</version>
</dependency>
```

For Gradle use:
```kotlin
implementation("de.alxgrk:spring-selenium-pool-core:1.0.0")
```

## 🛠️ Configuration

This library is configurable via Spring-Boot properties. Autocompletion when working with IntelliJ included. This is an example configuration:
```properties
# default values
selenium.pool.size=3
selenium.pool.recording-directory= # if empty, no recording will happen
selenium.pool.profiles-directory= # if empty, a temporary directory will be created and deleted on exit
selenium.pool.extension-files-in-classpath= # if empty, no extension files will be loaded
```
 
## 🥽 Utilities

To connect to the running Selenium container, you have to have `vncviewer` & `tigervnc-common` installed. If correctly configured, simply run:
```shell script
./vnc.sh
```

## 🤽 Usage

### 💆 Getting a container
Trying to get a container synchronously returns null, if there is no idle container at the moment.
```kotlin
@Component
class SomeSeleniumTask(@Autowired webDriverPool: WebDriverPool) {

    init {
        val container: WebDriverForContainer? = webDriverPool.getWebDriverForContainer()
    }    

}
```

Instead, one could also use the \*Async method, to wait until there is an idle container.
```kotlin
@Component
class SomeSeleniumTask(@Autowired webDriverPool: WebDriverPool) {

    init {
        val container: CompletableFuture<WebDriverForContainer> = webDriverPool.getWebDriverForContainerAsync()
        
        // blocking get
        container.get()

        // ... or with timeout
        container.get(1, TimeUnit.SECONDS)
    }    

}
```

If you used a container before, you should have a `ContainerId`, which can be used to get the formerly used container again.
```kotlin
@Component
class SomeSeleniumTask(@Autowired webDriverPool: WebDriverPool) {

    init {
        val container = webDriverPool.getWebDriverForContainer()

        // do something...

        val theSameContainer = webDriverPool.getWebDriverForContainer(container.containerId)
    }    

}
```

Since you might want to reuse a formerly used Chrome profile (in order to still have access to cookies etc.), 
it is possible to hand over the name of this profile.
```kotlin
@Component
class SomeSeleniumTask(@Autowired webDriverPool: WebDriverPool) {

    init {
        val container = webDriverPool.getWebDriverForContainer(profile = ChromeProfile("username"))
        // cookies etc is stored from last use
    }    

}
```

### 🤝 Interacting with Selenium
To interact with the Selenium server running within the container, you can simply use the provided `WebDriver` instance.
```kotlin
@Component
class SomeSeleniumTask(@Autowired webDriverPool: WebDriverPool) {

    init {
        val container = webDriverPool.getWebDriverForContainer()
        container!!.webDriver.get("https://foo.bar/")
    }    

}
```

### 👋 Finishing work
Once you are done with doing whatever you wanted to do, you should return the container to the pool 
(unless you are sure, you'll need the exact same instance later).

Since `WebDriverForContainer` implements `AutoClosable`, you can simply use try-with-resources in Java 
or `.use()` extension function in Kotlin.
```kotlin
@Component
class SomeSeleniumTask(@Autowired webDriverPool: WebDriverPool) {

    init {
        webDriverPool.getWebDriverForContainer()!!.use {
            it.webDriver.get("https://foo.bar/")
        }
    }    

}
```

## 🎉 Acknowledgements

This library heavily relies on the great work of the [Testcontainers](https://github.com/testcontainers/testcontainers-java) team - thanks a lot!

## 📝 Closing notes

I hope you like this small library. If you have any feedback or encounter any problem, feel free to contact me, create an issue or even better a pull request. 
