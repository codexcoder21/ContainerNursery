
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
        val configStream = Application::class.java.classLoader.getResourceAsStream("config.json")
            ?: throw RuntimeException("config.json not found for test")
        val config: Config = Gson().fromJson(configStream.reader(), object : TypeToken<Config>() {}.type)
        val dockerManager = DockerManager(config)

        application {
            module(dockerManager)
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
    }
}

