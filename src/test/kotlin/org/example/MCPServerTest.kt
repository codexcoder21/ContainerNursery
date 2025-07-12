package org.example

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class MCPServerTest {
    @Test
    fun passwordEndpointReturnsSecret() = runBlocking {
        val server = MCPServer(0)
        server.start()
        val port = server.port()
        val client = HttpClient(CIO)
        val response = client.get("http://localhost:$port/password").bodyAsText()
        client.close()
        server.stop()
        assertEquals("P@ssw0rd", response)
    }
}
