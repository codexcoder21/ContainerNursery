package org.example

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.testing.testApplication
import kotlin.test.*
import io.ktor.http.*
import java.io.File
import org.example.HelloDummyBackedContainer
import org.example.SystemClock
import org.example.ContainerFactory

class ApplicationTest {

    @Test
    fun testHelloWorldRoute() = testApplication {
        val tempConfigFile = File.createTempFile("test_config", ".json")
        val configContent = """
            {
              "routes": [
                {
                  "domain": "www.helloworld.com",
                  "image": "hello-world-docker-image:latest",
                  "keepWarmSeconds": 30,
                  "port": 8080,
                  "type": "http"
                }
              ]
            }
        """.trimIndent()
        tempConfigFile.writeText(configContent)

        val clock = SystemClock()
        val config: Config = configFromFile(tempConfigFile.absolutePath)
        val router = ConfigFileRequestRouter(config)
        val nursery = ContainerNursery(router, clock) { HelloDummyBackedContainer(clock) }

        application {
            module(config, router, nursery, clock, ContainerFactory { HelloDummyBackedContainer(clock) })
        }

        val client = createClient {
            followRedirects = false
        }

        val response = client.get("/") {
            headers.append(HttpHeaders.Host, "www.helloworld.com")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("Hello World"))
        nursery.shutdown()
        tempConfigFile.delete()
    }
}
