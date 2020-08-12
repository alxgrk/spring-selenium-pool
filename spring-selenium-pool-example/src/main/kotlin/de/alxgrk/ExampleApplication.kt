package de.alxgrk

import de.alxgrk.spring_selenium_pool.ChromeExtensionUtility.initializeExtension
import de.alxgrk.spring_selenium_pool.ChromeProfile
import de.alxgrk.spring_selenium_pool.WebDriverPool
import org.openqa.selenium.By
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean

fun main(vararg args: String) {
    SpringApplication.run(ExampleApplication::class.java, *args)
}

@SpringBootApplication
class ExampleApplication {

    @Bean
    fun commandLineRunner(webDriverPool: WebDriverPool) = CommandLineRunner {
        val webDriver = webDriverPool.getWebDriverForContainer(profile = ChromeProfile("test"))?.webDriver
        webDriver?.apply {
            get("https://github.com/")
            println("Extension says: ${initializeExtension()}")
            println("""
                
                    😎😎😎
                    ${findElements(By.cssSelector("h1"))[0].text}
                    😎😎😎
                
                    """.trimIndent())
        }
    }

}