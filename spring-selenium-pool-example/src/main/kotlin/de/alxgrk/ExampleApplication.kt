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
package de.alxgrk

import de.alxgrk.spring_selenium_pool.ChromeExtensionUtility.initializeExtension
import de.alxgrk.spring_selenium_pool.ChromeExtensionUtility.sendAsyncMessage
import de.alxgrk.spring_selenium_pool.ChromeProfile
import de.alxgrk.spring_selenium_pool.WebDriverPool
import org.openqa.selenium.By
import org.openqa.selenium.JavascriptExecutor
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
            println("Extension says: ${(this as JavascriptExecutor).initializeExtension()}")
            println("""
                
                    😎😎😎
                    ${findElements(By.cssSelector("h1"))[0].text}
                    😎😎😎
                
                    """.trimIndent())

            // set proxy
            val proxyIp = "127.0.0.1"
            val proxyPort = 12345
            (this as JavascriptExecutor).sendAsyncMessage("""
                    { 
                        extension: "setProxy",
                        proxyJson: { 
                            ip: "$proxyIp",
                            port: $proxyPort
                        }
                    }
                    """)
        }
    }
}
