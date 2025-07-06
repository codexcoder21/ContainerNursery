
package org.example

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.testing.testApplication
import kotlin.test.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.concurrent.TimeUnit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO


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
                  "port": 8080
                }
              ]
            }
        """.trimIndent()
        tempConfigFile.writeText(configContent)

        val config: Config = Gson().fromJson(tempConfigFile.readText(), object : TypeToken<Config>() {}.type)
        val dockerManager = DockerManager(config)

        application {
            module(config, dockerManager)
        }

        val client = createClient {
            followRedirects = false
        }

        val response = client.get("/") {
            headers.append(HttpHeaders.Host, "www.helloworld.com")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("Hello World"))
        dockerManager.shutdown()
        tempConfigFile.delete()
    }
}

