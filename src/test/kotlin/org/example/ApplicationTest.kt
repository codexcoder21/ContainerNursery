package org.example

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.ServerSocket
import kotlin.test.assertEquals

class ApplicationTest {
    private lateinit var server: ApplicationEngine
    private var port: Int = 0

    @BeforeEach
    fun setUp() {
        val socket = ServerSocket(0)
        port = socket.localPort
        socket.close()
        server = embeddedServer(Netty, port = port, module = { module() }).start()
    }

    @AfterEach
    fun tearDown() {
        server.stop(1000, 1000)
    }

    @Test
    fun testHelloWorld() {
        val client = HttpClient.newHttpClient()
        val request = HttpRequest.newBuilder(URI.create("http://localhost:$port/"))
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals("Hello World!", response.body())
    }
}
